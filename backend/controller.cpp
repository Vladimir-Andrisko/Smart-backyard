#include "controller.hpp"

using namespace std;
SSDPController *ssdp = nullptr;
unordered_set<string> registered_topics;
unordered_map<string, json> device_state;

void handleSignal(int){
    if (ssdp != nullptr){
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
		cout << "Topic not registered: " << topic << endl;
		return;
	}

	try{
		data = json::parse(payload);
	}catch(exception e){	
		cout << e.what() << endl;
	}

	if(topic == HUMIDITY_SENSOR_TOPIC){
		device_state[data["uuid"]]["Humidity"] = data["Service"]["Humidity"].get<int>();
	}else if(topic == TEMPERATURE_SENSOR_TOPIC){
		device_state[data["uuid"]]["Temperature"] = data["Service"]["Temperature"].get<int>();
	}else if(topic == LIGHT_SENSOR_TOPIC){
		device_state[data["uuid"]]["Intensity"] = data["Service"]["Intensity"].get<int>();
	}else if(topic == ROOF_ACTUATOR_TOPIC_SUB){
		device_state[data["uuid"]]["Position"] = data["Service"]["Position"].get<int>();
	}

	for(auto &pair : device_state){
		cout << "DEVICE STATES: " << pair.second << endl;
	}
}

void on_connect(struct mosquitto *mosq, void *obj, int rc) {
	if(rc) {
		printf("Error with result code: %d\n", rc);
		exit(-1);
	}
	mosquitto_subscribe(mosq, NULL, "garden/global/sensor/#", 0);
	mosquitto_subscribe(mosq, NULL, "garden/global/actuator/#", 0);
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


	mosquitto_loop_start(mosq);

	// cout << "Sending message to roof\n\n";
	// int ret = mosquitto_publish(mosq, NULL, "garden/global/actuator/roof_actuator", 17, "Hello controller", 0, false);
	// printf("Publish ret = %d\n", ret);

	getchar();

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

				cout << "TOPIC: " << topic << endl;

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