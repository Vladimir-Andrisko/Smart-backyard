#include "row_sensor.hpp"

using namespace std;
static SSDPDevice *ssdp = nullptr;
static mutex sleepMx;
static condition_variable sleepCv;
atomic<bool> running(true);

static string uuid;
static string topic;
static string group;
static int max_humidity;
static int min_humidity;
static int keep_alive = 30;

void handleSignal(int){
    running = false;
    sleepCv.notify_all();
}

int main(int argc, char* argv[]){
    signal(SIGINT, handleSignal);
    signal(SIGTERM, handleSignal);

    int rc;
	struct mosquitto *mosq;

    try{
        if (argc >= 2) {
            cout << "Config file path:  " << argv[1] << endl << endl;
            parseDesc(string(argv[1]));
            ssdp = new SSDPDevice(argv[1], keep_alive);
        }
        else{
            cout << "Config file path:  " << "config/sensor/row_sensor/row1_sensor_desc.json" << endl << endl;
            parseDesc(string("config/sensor/row_sensor/row1_sensor_desc.json"));
            ssdp = new SSDPDevice("1", "row_sensor", "config/sensor/row_sensor/row1_sensor_desc.json", 60, keep_alive);
        }
    }catch(const std::exception &e){
        cerr << e.what() << endl;
        return 1;
    }

    ssdp->start();
	mosquitto_lib_init();
    string mqtt_client_name = string(group + "_sensor");
	mosq = mosquitto_new(mqtt_client_name.c_str(), true, NULL);

	rc = mosquitto_connect(mosq, "localhost", 1883, mqtt_alive);
	if(rc != 0){
		printf("Client could not connect to broker! Error Code: %d\n", rc);
		mosquitto_destroy(mosq);
		return -1;
	}
    mosquitto_loop_start(mosq);

    {
        unique_lock<mutex> ul(sleepMx);
        while (running)
        {
            int humidity = generateHumidity();
            cout << "Row humidity sensor reading: " << humidity << endl;
            string msg = construct_msg(humidity);
            int rc = mosquitto_publish(mosq, NULL, topic.c_str(), msg.size(), msg.c_str(), mqtt_QoS, false);
            sleepCv.wait_for(ul, chrono::seconds(SLEEP_TIME));
        }
    }

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
    group = desc["group"];
    keep_alive = desc["keepAlive"];

    json service = desc["Service"];
    string humidity = service["Humidity"];
    
    size_t tilde = humidity.find('~');
    size_t pipe  = humidity.find('|');

    min_humidity = stoi(humidity.substr(0, tilde));
    max_humidity = stoi(humidity.substr(tilde + 1, pipe - tilde - 1));
}