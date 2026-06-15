#include "controller.hpp"

using namespace std;
SSDPController *ssdp = nullptr;
atomic<bool> running = true;
unordered_set<string> registered_topics;
unordered_map<string, json> device_state;
unordered_map<string, RowControl> row_control;

mutex deviceState_mutex;
mutex rowControl_mutex;
mutex sleepMutex_print;
mutex sleepMutex_controlLoop;
condition_variable sleepCv;

void handleSignal(int){
    running = false;
	sleepCv.notify_all();
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

	{
		unique_lock<mutex> ul(deviceState_mutex);
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
		}
	}
	
	if(topic == APP_TOPIC_SUB){
		parseAppData(data, mosq);
	}else if(topic == APP_TOPIC_ALIVE){
		cout << "[SSDP] App Alive!\n";
	}
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
	signal(SIGTERM, handleSignal);
	struct mosquitto *mosq;
	int rc, id=1;

    try{
        ssdp = new SSDPController(false);
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
	cout << "[INFO] Connected to the broker\n";

	registered_topics.insert(APP_TOPIC_SUB);
	registered_topics.insert(APP_TOPIC_ALIVE);
	mosquitto_loop_start(mosq);

	string roof_msg = "OPEN";
	string msg;

	thread print_thread = thread(print_loop);
	thread control_thread = thread(control_loop, mosq);

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

	running = false;
	sleepCv.notify_all();
	print_thread.join();
	control_thread.join();

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
	// cout << "[DEBUG] Command type: " << command_type << endl;
	// cout << "[DEBUG] Got message: " << data.dump() << endl;

	if(command_type == "SET"){
		string uuid = data["uuid"];
		string group = data["group"];
		// cout << "[DEBUG] UUID: " << uuid << endl;

		json serv = data["Service"];
		// cout << "Service: " << serv.dump() << endl;

		string msg = generateActuatorMsg(uuid, serv["Position"]);
		string top = generateTopic(uuid, group);

		int ret = mosquitto_publish(mosq, NULL, top.c_str(), msg.size(), msg.c_str(), 0, false);
	}else if(command_type == "GET"){
		string uuid = data["uuid"];
		string group = data["group"];

		json temp;
		temp["uuid"] = uuid;
		temp["group"] = group;

		if(!device_state.count(uuid)) return;
		json device_info = device_state[uuid];

		temp["Service"] = device_info;

		string msg = temp.dump();
		// cout << "[DEBUG] Response to GET: " << msg << endl;

		int ret = mosquitto_publish(mosq, NULL, APP_TOPIC_PUB, msg.size(), msg.c_str(), 0, false);
	}else if(command_type == "SET.garden_rows"){
		json control = data["control"];
		{
			unique_lock<mutex> ul(rowControl_mutex);
			row_control.clear();
			for(auto& [key, value] : control.items()){
				RowControl temp;

				string id = key.substr(3);
				temp.sensor_uuid = string("uuid:" + id + "::row_sensor");
				temp.actuator_uuid = string("uuid:" + id + "::row_actuator");

				temp.group = key;
				temp.max_moisture = value["max_moisture"];
				temp.min_moisture = value["min_moisture"];
				temp.min_pause = value["min_pause"];
				temp.max_water = value["max_water"];

				row_control[key] = temp;
			}
		}
	}
}

string generateTopic(string uuid, string group){
	if(uuid == "uuid:1::roof_actuator"){
		return ROOF_ACTUATOR_TOPIC_PUB;
	}else{
		return string("garden/" + group + "/actuator/row_actuator/cmd");
	}
}

void control_loop(struct mosquitto *mosq){
	unordered_map<string, json> deviceState_copy;
	while(running){
		{
			unique_lock<mutex> ul(deviceState_mutex);
			deviceState_copy = device_state;
		}

		{
			unique_lock<mutex> ul(rowControl_mutex);
			for(auto& [row, control] : row_control){
				if(deviceState_copy.find(control.sensor_uuid) == deviceState_copy.end()){
                    continue;
                }

				int moisture = deviceState_copy[control.sensor_uuid]["Humidity"];
				auto now = chrono::steady_clock::now();

				if(control.watering){
                    auto watering_time = chrono::duration_cast<chrono::seconds>(now - control.watering_start).count();
                    bool stop_watering = false;

                    if(moisture >= control.max_moisture){
                        stop_watering = true;
                    }

                    if(watering_time >= control.max_water){
                        stop_watering = true;
                    }

                    if(stop_watering){
                        string top = generateTopic(control.actuator_uuid, control.group);
						string msg = generateActuatorMsg(control.actuator_uuid, "CLOSED");
						int ret = mosquitto_publish(mosq, NULL, top.c_str(), msg.size(), msg.c_str(), 0, false);

                        control.watering = false;
                        control.last_watering_end = now;
                    }
                }else{
                    auto pause_time = chrono::duration_cast<chrono::seconds>(now - control.last_watering_end).count();
                    bool can_water = pause_time >= control.min_pause;

                    if(moisture < control.min_moisture && can_water){
						string top = generateTopic(control.actuator_uuid, control.group);
						string msg = generateActuatorMsg(control.actuator_uuid, "OPEN");
						int ret = mosquitto_publish(mosq, NULL, top.c_str(), msg.size(), msg.c_str(), 0, false);

                        control.watering = true;
                        control.watering_start = now;
                    }
                }
			}
		}

		{
			unique_lock<mutex> ul(sleepMutex_controlLoop);
			sleepCv.wait_for(ul ,chrono::milliseconds(CONTROL_REFRESH_RATE), [&](){
				return !running.load();
			});
		}
	}
}

void print_loop(){
	while(running){
		cout << "\033[2J\033[1;1H" << flush;
		cout << "======================================================================================\n";
		{
			unique_lock<mutex> ul(deviceState_mutex);
			for(auto &pair : device_state){
				cout << pair.first << ": " << pair.second << endl;
			}
		}
		cout << "======================================================================================\n";
		{
			unique_lock<mutex> ul(deviceState_mutex);
			for(auto &pair : row_control){
				cout << pair.first << ": " << pair.second.watering << endl;
			}
		}
		cout << "======================================================================================\n\n";

		{
			unique_lock<mutex> ul(sleepMutex_print);
			sleepCv.wait_for(ul ,chrono::milliseconds(PRINT_REFRESH_RATE), [&](){
				return !running.load();
			});
		}
	}
}