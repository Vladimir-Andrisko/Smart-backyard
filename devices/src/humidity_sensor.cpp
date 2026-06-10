#include "network/ssdp/SSDPDevice.hpp"
#include "mosquitto.h"
#include "csignal"
#include <random>
#include <chrono>
#include <atomic>
#include <thread>
#include <mutex>
#include "json.hpp"

#include "humidity_sensor.hpp"

using namespace std;
static SSDPDevice *ssdp = nullptr;
static mutex sleepMx;
static condition_variable sleepCv;


void handleSignal(int){
    if(ssdp != nullptr){
        delete ssdp;
    }
    exit(0);
}


int main(int argc, char* argv[]){
    signal(SIGINT, handleSignal);
    atomic<bool> running(true);
    int rc;
	struct mosquitto *mosq;
    string topic;

    try{
        if (argc >= 2)
            ssdp = new SSDPDevice(argv[1], 5);
        else
            ssdp = new SSDPDevice("1", "humidity_sensor", "config/sensor/humidity_sensor_desc.json", 5);

    }catch(const std::exception &e){
        cerr << e.what() << endl;
        return 1;
    }

    try{
        topic = loadTopicFromJson(argv[1]);
    }catch(const std::exception &e){
        cerr << e.what() << endl;
        return 1;
    }

    ssdp->start();
	mosquitto_lib_init();
	mosq = mosquitto_new("humidity_sensor", true, NULL);

	rc = mosquitto_connect(mosq, "0.0.0.0", 1883, keepAlive);
	if(rc != 0){
		printf("Client could not connect to broker! Error Code: %d\n", rc);
		mosquitto_destroy(mosq);
		return -1;
	}
    mosquitto_loop_start(mosq);


    std::thread exitThread([&]() {
        std::cin.get();
        running = false;
        sleepCv.notify_all();
    });

    {
        unique_lock<mutex> ul(sleepMx);
        while (running)
        {
            int humidity = generateHumidity();
            string msg = construct_msg(humidity);
            cout << "Humiditi sensor: \n" << msg << endl;
            int rc = mosquitto_publish(mosq, NULL, topic.c_str(), msg.size(), msg.c_str(), QoS, false);

            sleepCv.wait_for(ul, chrono::seconds(SLEEP_TIME), [&]{return !running.load();});
        }
    }

    exitThread.join();

	mosquitto_disconnect(mosq);
	mosquitto_destroy(mosq);
	mosquitto_lib_cleanup();
    mosquitto_loop_stop(mosq, true);

    ssdp->stop();
    delete ssdp;
}


int generateHumidity()
{
    static std::random_device rd;
    static std::mt19937 gen(rd());

    std::normal_distribution<> dist(50.0, 15.0);

    int value = (int)std::round(dist(gen));

    if (value < 0) value = 0;
    if (value > 100) value = 100;

    return value;
}


std::string construct_msg(int humidity){

    return std::string(R"json(
{
    "uuid": "uuid:1::humidity_sensor",
    "group": "global",
    "Service": {
        "State": "ON",
        "Humidity": )json")
    + std::to_string(humidity) +
    R"json(
    }
}
    )json";
}

std::string loadTopicFromJson(const std::string& path){
    std::ifstream file(path);

    if(!file.is_open())
        throw std::runtime_error("Failed to open json file");

    std::stringstream buffer;
    buffer << file.rdbuf();

    json::jobject obj = json::jobject::parse(buffer.str().c_str());

    return obj["topic"].as_string();
}