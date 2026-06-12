#include "humidity_sensor.hpp"

using namespace std;
static SSDPDevice *ssdp = nullptr;
static mutex sleepMx;
static condition_variable sleepCv;
atomic<bool> running(true);


void handleSignal(int){
    if(ssdp != nullptr){
        delete ssdp;
        ssdp = nullptr;
    }
    exit(0);
}

int main(int argc, char* argv[]){
    signal(SIGINT, handleSignal);
    
    int rc;
	struct mosquitto *mosq;
    string topic;

    try{
        if (argc >= 2) ssdp = new SSDPDevice(argv[1], 5);
        else ssdp = new SSDPDevice("1", "humidity_sensor", "config/sensor/humidity_sensor_desc.json", 10, SSDP_QoS);
    }catch(const std::exception &e){
        cerr << e.what() << endl;
        return 1;
    }

    try{
        cout << "Config file path: " << argv[1] << endl;
        topic = loadTopicFromJson(argv[1]);
    }catch(const std::exception &e){
        cerr << e.what() << endl;
        return 1;
    }

    ssdp->start();
	mosquitto_lib_init();
	mosq = mosquitto_new("humidity_sensor", true, NULL);

	rc = mosquitto_connect(mosq, "localhost", 1883, keepAlive);
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
            cout << "Humidity sensor reading: " << humidity << endl;
            string msg = construct_msg(humidity);
            int rc = mosquitto_publish(mosq, NULL, topic.c_str(), msg.size(), msg.c_str(), QoS, false);
            
            sleepCv.wait_for(ul, chrono::seconds(SLEEP_TIME));
        }
    }

    exitThread.join();

	mosquitto_disconnect(mosq);
	mosquitto_destroy(mosq);
	mosquitto_lib_cleanup();

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
    json data;
    data["uuid"] = "uuid:1::humidity_sensor";
    data["Service"] = json::object();
    data["Service"]["Humidity"] = humidity;

    return data.dump();
}

std::string loadTopicFromJson(const std::string& path){
    std::ifstream file(path);

    if(!file.is_open())
        throw std::runtime_error("Failed to open json file");

    json obj = json::parse(file);
    return obj["topic"].get<string>();
}