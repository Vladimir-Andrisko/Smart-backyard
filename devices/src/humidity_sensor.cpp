#include "humidity_sensor.hpp"

using namespace std;
static SSDPDevice *ssdp = nullptr;
static mutex sleepMx;
static condition_variable sleepCv;
atomic<bool> running(true);

static string uuid;
static string topic;
static int max_humidity;
static int min_humidity;

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

    try{
        if (argc >= 2) {
            ssdp = new SSDPDevice(argv[1], 2);
            cout << "Config file path:  " << argv[1] << endl << endl;
            parseDesc(string(argv[1]));
        }
        else{
            ssdp = new SSDPDevice("1", "humidity_sensor", "config/sensor/humidity_sensor_desc.json", 10, SSDP_QoS);
            cout << "Config file path:  " << "config/sensor/humidity_sensor_desc.json" << endl << endl;
            parseDesc(string("config/sensor/humidity_sensor_desc.json"));
        }
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

    if (value < min_humidity) value = min_humidity;
    if (value > max_humidity) value = max_humidity;

    return value;
}


std::string construct_msg(int humidity){
    json data;
    data["uuid"] = uuid;
    data["Service"] = json::object();
    data["Service"]["Humidity"] = humidity;

    return data.dump();
}

void parseDesc(const string &file_path){
    ifstream file(file_path);

    if(!file.is_open())
        throw runtime_error("Unable to open description file!");

    json desc = json::parse(file);
    file.close();

    uuid = string("uuid:" + desc["uuid"].get<string>());
    topic = desc["topic"];

    json service = desc["Service"];
    string humidity = service["Humidity"];
    
    size_t tilde = humidity.find('~');
    size_t pipe  = humidity.find('|');

    min_humidity = stoi(humidity.substr(0, tilde));
    max_humidity = stoi(humidity.substr(tilde + 1, pipe - tilde - 1));
}