#include "light_sensor.hpp"

using namespace std;

static SSDPDevice *ssdp = nullptr;
static mutex sleepMx;
static condition_variable sleepCv;
atomic<bool> running(true);

static string uuid;
static string topic;
static int max_intensity;
static int min_intensity;
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
        if (argc >= 2){
            cout << "Config file path:  " << argv[1] << endl << endl;
            parseDesc(string(argv[1]));
            ssdp = new SSDPDevice(argv[1], keep_alive);
        }
        else{
            cout << "Config file path:  " << "config/sensor/light_sensor_desc.json" << endl << endl;
            parseDesc(string("config/sensor/light_sensor_desc.json"));
            ssdp = new SSDPDevice("1", "light_sensor", "config/sensor/light_sensor_desc.json", 60, keep_alive);
        }
    }catch(const exception &e){
        cerr << e.what() << endl;
        return 1;
    }

    ssdp->start();
	mosquitto_lib_init();
	mosq = mosquitto_new("light_sensor", true, NULL);

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
            int brightness = generateBrightness();
            string msg = construct_msg(brightness);
            cout << "Light sensor reading: " << brightness << endl;
            int rc = mosquitto_publish(mosq, NULL, topic.c_str(), msg.size(), msg.c_str(), mqtt_QoS, false);

            sleepCv.wait_for(ul, chrono::seconds(SLEEP_TIME), [&]{return !running.load();});
        }
    }

	mosquitto_disconnect(mosq);
	mosquitto_destroy(mosq);
	mosquitto_lib_cleanup();

    ssdp->stop();
    delete ssdp;
}


int generateBrightness()
{
    static random_device rd;
    static mt19937 gen(rd());
    static normal_distribution<> noise(0.0, 5.0);

    auto now = chrono::system_clock::now();
    time_t t = chrono::system_clock::to_time_t(now);
    tm local = *localtime(&t);

    int hour = local.tm_hour;
    double x = hour / 24.0;

    double base = max(0.0, sin(M_PI * x));
    double intensity = min_intensity + base * (max_intensity - min_intensity);


    intensity += noise(gen);

    if (intensity < min_intensity) intensity = min_intensity;
    if (intensity > max_intensity) intensity = max_intensity;

    return static_cast<int>(round(intensity));
}

string construct_msg(int brightness){
    json data;
    data["uuid"] = uuid;
    data["Service"] = json::object();
    data["Service"]["Intensity"] = brightness;

    return data.dump();
}

void parseDesc(const string &file_path){
    ifstream file(file_path);

    if(!file.is_open())
        throw runtime_error("Unable to open description file!");

    json desc = json::parse(file);
    file.close();

    uuid = string("uuid:" + desc["uuid"].get<string>());
    keep_alive = desc["keepAlive"];

    topic = desc["topic"];

    json service = desc["Service"];
    string brightness = service["Intensity"];
    
    size_t tilde = brightness.find('~');
    size_t pipe  = brightness.find('|');

    min_intensity = stoi(brightness.substr(0, tilde));
    max_intensity = stoi(brightness.substr(tilde + 1, pipe - tilde - 1));
}