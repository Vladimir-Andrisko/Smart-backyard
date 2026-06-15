#include "controller.hpp"

using namespace std;
SSDPController *ssdp = nullptr;
atomic<bool> running = true;
unordered_set<string> registered_topics;
unordered_map<string, json> device_state;

void handleSignal(int){
    if (ssdp != nullptr){
		ssdp->stop();
		delete ssdp;
        ssdp = nullptr;
    }
    exit(0);
}

void on_message(struct mosquitto *mosq, void *obj, const struct mosquitto_message *msg) {
	string topic(msg->topic);
	string payload(static_cast<const char*>(msg->payload),msg->payloadlen);
	json data;

	if(!registered_topics.count(topic)){
		cout << "[ERROR] Topic not registered: " << topic << endl;
		return;
	}

	try{
		data = json::parse(payload);
	}catch(const exception &e){	
		cout << "[JSON] Error parsing msg: " << e.what() << endl;
		return;
	}

	if(topic == HUMIDITY_SENSOR_TOPIC){
		device_state[data["uuid"]]["Humidity"] = data["Service"]["Humidity"].get<int>();
	}else if(topic == TEMPERATURE_SENSOR_TOPIC){
		device_state[data["uuid"]]["Temperature"] = data["Service"]["Temperature"].get<int>();
	}else if(topic == LIGHT_SENSOR_TOPIC){
		device_state[data["uuid"]]["Intensity"] = data["Service"]["Intensity"].get<int>();
	}else if(topic == ROOF_ACTUATOR_TOPIC_SUB){
		device_state[data["uuid"]]["Position"] = data["Service"]["Position"].get<string>();
	}else if(topic.find("/sensor/row_sensor") != std::string::npos){
		device_state[data["uuid"]]["Humidity"] = data["Service"]["Humidity"].get<int>();
	}else if(topic.find("/actuator/row_actuator") != std::string::npos){
		device_state[data["uuid"]]["Position"] = data["Service"]["Position"].get<string>();
	}else if(topic == APP_TOPIC_SUB){
		parseAppData(data, mosq);
	}else if(topic == APP_TOPIC_ALIVE){
		cout << "[APP] IT'S ALIVE!!\n";
	}

	std::cout << "\033[2J\033[1;1H" << std::flush;
	cout << "======================================================================================\n";
	for(auto &pair : device_state){
		cout << pair.first << ": " << pair.second << endl;
	}
	cout << "======================================================================================\n\n";
}

void on_connect(struct mosquitto *mosq, void *obj, int rc) {
	if(rc) {
		printf("Error with result code: %d\n", rc);
		exit(-1);
	}
	mosquitto_subscribe(mosq, NULL, "garden/global/sensor/#", 0);
	mosquitto_subscribe(mosq, NULL, "garden/global/actuator/roof_actuator", 0);
	mosquitto_subscribe(mosq, NULL, "garden/+/sensor/row_sensor", 0);
	mosquitto_subscribe(mosq, NULL, "garden/+/actuator/row_actuator", 0);
	mosquitto_subscribe(mosq, NULL, APP_TOPIC_SUB, 0);
	mosquitto_subscribe(mosq, NULL, APP_TOPIC_ALIVE, 0);
	mosquitto_message_callback_set(mosq, on_message);
}


int main(){
	signal(SIGINT, handleSignal);
	struct mosquitto *mosq;
	int rc, id=1;

    try{
        ssdp = new SSDPController(true);
    }catch(const std::exception &e){
        cerr << e.what() << endl;
        return 1;
    }

	setup_callback();
    ssdp->start();

	mosquitto_lib_init();
	mosq = mosquitto_new("smart_garden_controller", true, &id);
	mosquitto_connect_callback_set(mosq, on_connect);

	rc = mosquitto_connect(mosq, "localhost", 1883, 10);
	if(rc) {
		printf("Could not connect to Broker with return code %d\n", rc);
		exit(0);
	}
	cout << "Connected to the broker\n";

	registered_topics.insert(APP_TOPIC_SUB);
	registered_topics.insert(APP_TOPIC_ALIVE);
	mosquitto_loop_start(mosq);

	string roof_msg = "OPEN";
	string msg;
	char c;
	while((c = getchar()) != 'q'){
		if(c == 'r'){
			string id = "uuid:1::row_actuator";
			string group = "row1";
			string top = generateTopic(id, group);
			if(roof_msg == "OPEN"){
				msg = generateActuatorMsg(id, "CLOSED");
				roof_msg = "CLOSED";
			}
			else{
				roof_msg = "OPEN";
				msg = generateActuatorMsg(id, "OPEN");
			}
			cout << "Sending message to actuator (" << id << "):  " << msg << "\n";
			int ret = mosquitto_publish(mosq, NULL, top.c_str(), msg.size(), msg.c_str(), 0, false);
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


void setup_callback(){
	ssdp->setOnDeviceAdded([&](const Device& dev){
		auto it = device_state.find(dev.uuid);
		if(it != device_state.end()){
			it->second["State"] = "ON";
		}else{
			try{
				ifstream file(dev.location);
				if(!file.is_open()){
					cout << "Failed to open device description file: " << dev.location << endl;
					return;
				}
				json desc = json::parse(file);
				json service = desc["Service"];
				string topic = desc["topic"];

				service["State"] = "ON";
				registered_topics.emplace(topic);
				device_state[dev.uuid] = service;
			}catch(const exception &e){
				cout << "[JSON ERROR] " << e.what() << endl;
			}
		}
	});

	ssdp->setOnDeviceRemoved([&](const Device& dev){
		auto it = device_state.find(dev.uuid);
		if(it != device_state.end()){
			it->second["State"] = "OFF";
		}
	});

	ssdp->setOnDeviceExpired([&](const Device& dev){
		auto it = device_state.find(dev.uuid);
		if(it != device_state.end()){
			it->second["State"] = "UNREACHABLE";
		}
	});
}

string generateActuatorMsg(string uuid, string position){
	json msg;
	msg["uuid"] = uuid;
	msg["Service"] = json::object();
	msg["Service"]["Position"] = position;

	return msg.dump();
}

void parseAppData(json &data, struct mosquitto *mosq){
	string command_type = data["command_type"];
	string uuid = data["uuid"];
	string group = data["group"];
	cout << "[DEBUG] Command type: " << command_type << endl;
	cout << "[DEBUG] UUID: " << uuid << endl;

	if(command_type == "SET"){
		json serv = data["Service"];
		cout << "Service: " << serv.dump() << endl;

		string msg = generateActuatorMsg(uuid, serv["Position"]);
		string top = generateTopic(uuid, group);
		int ret = mosquitto_publish(mosq, NULL, top.c_str(), msg.size(), msg.c_str(), 0, false);

	}else if(command_type == "GET"){
		json temp;
		temp["uuid"] = uuid;
		temp["group"] = group;

		if(!device_state.count(uuid)) return;

		json device_info = device_state[uuid];
		temp["Service"] = device_info;

		string msg = temp.dump();
		cout << "[DEBUG] Response to GET: " << msg << endl;

		int ret = mosquitto_publish(mosq, NULL, APP_TOPIC_PUB, msg.size(), msg.c_str(), 0, false);
	}
}

string generateTopic(string uuid, string group){
	if(uuid == "uuid:1::roof_actuator"){
		return ROOF_ACTUATOR_TOPIC_PUB;
	}else{
		return string("garden/" + group + "/actuator/row_actuator/cmd");
	}
}

void control_loop(){
	while(running){
		this_thread::sleep_for(chrono::milliseconds(REFRESH_RATE));
	}
}