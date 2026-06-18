#include "row_sensor.hpp"

using namespace std;
static SSDPDevice *ssdp = nullptr;
static mutex sleepMx;
static condition_variable sleepCv;

atomic<bool> running(true);

static string uuid;
static string topic;
static string group;
static string VALVE_CONTROL_TOPIC;
static int max_humidity;
static int min_humidity;
static int keep_alive = 30;
static int moisture = 50;

// Control signals
atomic<int> humidity(100);
atomic<int> light_intensity(0);
atomic<int> temperature(0);
atomic<bool> is_valve_open(false);

void handleSignal(int){
    running = false;
    sleepCv.notify_all();
}

void on_connect(struct mosquitto *mosq, void *obj, int rc){
	if(rc) {
		printf("Error with result code: %d\n", rc);
		exit(-1);
	}
	mosquitto_subscribe(mosq, NULL, VALVE_CONTROL_TOPIC.c_str(), mqtt_QoS);
    mosquitto_subscribe(mosq, NULL, LIGHT_SENSOR_TOPIC, mqtt_QoS);
    mosquitto_subscribe(mosq, NULL, TEMPERATURE_SENSOR_TOPIC, mqtt_QoS);
    mosquitto_subscribe(mosq, NULL, HUMIDITY_SENSOR_TOPIC, mqtt_QoS);
	mosquitto_message_callback_set(mosq, on_message);
}

void on_message(struct mosquitto *mosq, void *obj, const struct mosquitto_message *msg){
	string payload(static_cast<const char*>(msg->payload), msg->payloadlen);
    string topic = string(msg->topic);
    json data;

    try{
        data = json::parse(payload);
    }catch(const exception &e){
        cout << "[JSON] error: " << e.what() << endl; 
        return;
    }

    if(topic == LIGHT_SENSOR_TOPIC){
        light_intensity = data["Service"]["Intensity"].get<int>();
        cout << "Light sensor: " << data["Service"]["Intensity"] << endl;
    }else if(topic == HUMIDITY_SENSOR_TOPIC){
        humidity = data["Service"]["Humidity"].get<int>();
        cout << "Humidity sensor: " << data["Service"]["Humidity"] << endl;
    }else if(topic == TEMPERATURE_SENSOR_TOPIC){
        temperature = data["Service"]["Temperature"].get<int>();
        cout << "Temperature sensor: " << data["Service"]["Temperature"] << endl;
    }else if(topic == VALVE_CONTROL_TOPIC){
        string pos = data["Service"]["Position"].get<string>();
        cout << "VALVE POSITION: " << data["Service"]["Position"] << endl;
        sleepCv.notify_all();
        if(pos == "OPEN"){
            is_valve_open = true;
            cout << "[DEBUG] OPENING VALVE!!!!!!!!!!!!!\n";
        }
        else if(pos == "CLOSED"){
            is_valve_open = false;
            cout << "[DEBUG] CLOSING VALVE!!!!!!!!!!!!!\n";
        }
    }
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
    VALVE_CONTROL_TOPIC = string("garden/" + group + "/valve_control");

	mosq = mosquitto_new(mqtt_client_name.c_str(), true, NULL);
    mosquitto_connect_callback_set(mosq, on_connect);

	rc = mosquitto_connect(mosq, "localhost", 1883, mqtt_alive);
	if(rc != 0){
		printf("Client could not connect to broker! Error Code: %d\n", rc);
		mosquitto_destroy(mosq);
		return -1;
	}
    mosquitto_loop_start(mosq);

    int sleep = 1;
    {
        unique_lock<mutex> ul(sleepMx);
        while (running)
        {
            if(is_valve_open.load()) sleep = WATERING_SLEEP;
            else sleep = REGULAR_SLEEP;

            updateMoisture(DT);
            cout << "Row moisture sensor reading: " << moisture << endl;

            string msg = construct_msg(moisture);
            int rc = mosquitto_publish(mosq, NULL, topic.c_str(), msg.size(), msg.c_str(), mqtt_QoS, false);

            sleepCv.wait_for(ul, chrono::seconds(sleep));
        }
    }

	mosquitto_disconnect(mosq);
	mosquitto_destroy(mosq);
	mosquitto_lib_cleanup();

    //ssdp->stop();
    //delete ssdp;
}


void updateMoisture(double dt){
    static random_device rd;
    static mt19937 gen(rd());

    uniform_real_distribution<> noise(-0.3, 0.3);

    double evaporation = 0.0;

    evaporation += temperature.load() * 0.003;
    evaporation += light_intensity.load() * 0.002;
    evaporation += (100 - humidity.load()) * 0.0015;
    evaporation += noise(gen);
    moisture -= (evaporation * dt) / 3;

    if(is_valve_open.load()) moisture += 2 * dt;

    if (moisture < min_humidity) moisture = min_humidity;
    if (moisture > max_humidity) moisture = max_humidity;
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
    string humidity;

    try{
        uuid = string("uuid:" + desc["uuid"].get<string>());
        topic = desc["topic"];
        group = desc["group"];
        keep_alive = desc["keepAlive"];

        json service = desc["Service"];
        humidity = service["Humidity"];
    }catch(const exception &e){
        string err = string("[ERROR] Can not parse json description: ") + e.what();
        throw runtime_error(err);
    }

    size_t tilde = humidity.find('~');
    size_t pipe  = humidity.find('|');

    min_humidity = stoi(humidity.substr(0, tilde));
    max_humidity = stoi(humidity.substr(tilde + 1, pipe - tilde - 1));
}