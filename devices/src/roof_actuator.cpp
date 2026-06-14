#include "roof_actuator.hpp"

using namespace std;
SSDPDevice *ssdp = nullptr;
static string uuid;
static string pub_topic;
static string state_open;
static string state_closed;
static string current_position = "CLOSED";
static int keepAlive = 30;

atomic<bool> running = true;
condition_variable sleepCv;
mutex sleepMx;

void handleSignal(int){
    running = false;
    sleepCv.notify_all();
}

void on_connect(struct mosquitto *mosq, void *obj, int rc){
	if(rc) {
		printf("Error with result code: %d\n", rc);
		exit(-1);
	}
	mosquitto_subscribe(mosq, NULL, ROOF_ACTUATOR_TOPIC_SUB, mqtt_QoS);
	mosquitto_message_callback_set(mosq, on_message);
}

void on_message(struct mosquitto *mosq, void *obj, const struct mosquitto_message *msg){
	string payload(static_cast<const char*>(msg->payload), msg->payloadlen);
    json data;
    string topic(msg->topic);
    cout << "TOPIC: " << topic << endl;

    cout << payload << endl;
     
    try{
		data = json::parse(payload);
	}catch(const exception &e){	
		cout << "[JSON] " << e.what() << endl;
	}
    string new_position = data["Service"]["Position"];
    if(new_position != state_open && new_position != state_closed){
        cout << "Wrong position: " << new_position << endl;
        return;
    }

    if(new_position != current_position){
        current_position = new_position;
        std::thread([=](){
            int time = generateRandomTime();
            std::this_thread::sleep_for(std::chrono::seconds(time));

            string msg = construct_msg(current_position);
            mosquitto_publish(mosq, NULL, pub_topic.c_str(), msg.size(), msg.c_str(), 0, false);

        }).detach();
    }
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
            ssdp = new SSDPDevice(argv[1], keepAlive);
            
        }else{ 
            cout << "Config file path:  " << "config/actuator/roof_actuator_desc.json" << endl << endl;
            parseDesc(string("config/actuator/roof_actuator_desc.json"));
            ssdp = new SSDPDevice("1", "roof_actuator", "config/actuator/roof_actuator_desc.json", 60, keepAlive);
        }
    }catch(const std::exception &e){
        cerr << e.what() << endl;
        return 1;
    }

    ssdp->start();
	mosquitto_lib_init();

	mosq = mosquitto_new("roof_actuator", true, NULL);
    mosquitto_connect_callback_set(mosq, on_connect);

	rc = mosquitto_connect(mosq, "localhost", 1883, mqtt_alive);
	if(rc != 0){
		printf("Client could not connect to broker! Error Code: %d\n", rc);
		mosquitto_destroy(mosq);
		return -1;
	}
    
    mosquitto_loop_start(mosq);    

    {
        unique_lock<mutex> ul(sleepMx);
        while(running){
            sleepCv.wait(ul, [](){
                return !running.load();
            });
        }
    }

    mosquitto_loop_stop(mosq, true);
	mosquitto_disconnect(mosq);
	mosquitto_destroy(mosq);
	mosquitto_lib_cleanup();

    ssdp->stop();
    delete ssdp;

    return 0;
}


int generateRandomTime()
{
    static random_device rd;
    static mt19937 gen(rd());

    uniform_int_distribution<> dist(3, 7);
    int value = (int)dist(gen);

    return value;
}

string construct_msg(string position){
    json data;
    data["uuid"] = uuid;
    data["Service"] = json::object();
    data["Service"]["Position"] = position;

    return data.dump();
}

void parseDesc(const string &file_path){
    ifstream file(file_path);

    if(!file.is_open())
        throw runtime_error("Unable to open description file!");

    json desc = json::parse(file);
    file.close();

    uuid = string("uuid:" + desc["uuid"].get<string>());
    pub_topic = desc["topic"];
    keepAlive = desc["keepAlive"];

    cout << "PUB topic: " << pub_topic << endl;
    cout << "SUB topic: " << ROOF_ACTUATOR_TOPIC_SUB << endl;

    json service = desc["Service"];
    string position = service["Position"];

    size_t pos = position.find('/');
    state_open = position.substr(0, pos);
    state_closed = position.substr(pos+1);
    current_position = "OPEN";
}