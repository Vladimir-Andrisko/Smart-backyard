#include "network/ssdp/SSDPController.hpp"
#include <iostream>
#include <csignal>
#include <fstream>
#include <string>
#include <sstream>
#include <algorithm>
#include <mutex>
#include "mosquitto.h"
#include "json.hpp"
#include "httpserver.hpp"

using namespace std;
SSDPController *ssdp = nullptr;
unordered_map<string, string> device_descriptions;
mutex mx;

void handleSignal(int)
{
    std::cout << "Zaustavlja se SSDP\n";
    if (ssdp)
    {
        delete ssdp;
        ssdp = nullptr;
    }
    std::exit(0);
}

void on_message(struct mosquitto *mosq, void *obj, const struct mosquitto_message *msg) {
	string topic(msg->topic);
	std::string payload(static_cast<const char*>(msg->payload),msg->payloadlen);
	string uuid;

	try {
        json::jobject Obj = json::jobject::parse(payload);
		uuid = string(Obj["uuid"].as_string());
		json::jobject ServiceObj = Obj["Service"].as_object();

		if(topic == "garden/global/sensor/temperature_sensor"){
			string value = ServiceObj["Temperature"];
			string location = device_descriptions[uuid];
			HTTPServer::writeDeviceServiceVariable(location, "Temperature", value);

		}else if(topic == "garden/global/sensor/humidity_sensor"){
			string value = ServiceObj["Humidity"];
			string location = device_descriptions[uuid];
			HTTPServer::writeDeviceServiceVariable(location, "Humidity", value);
		}
    } catch (const std::exception &e) {
        std::cout << "JSON parse error: " << e.what() << std::endl;
    }
	
}

void on_connect(struct mosquitto *mosq, void *obj, int rc) {
	printf("ID: %d\n", * (int *) obj);
	if(rc) {
		printf("Error with result code: %d\n", rc);
		exit(-1);
	}
	mosquitto_subscribe(mosq, NULL, "garden/global/sensor/#", 0);
	mosquitto_message_callback_set(mosq, on_message);
}

void loadDevices(){
	string path = "backend/device_config.json";
	ifstream file(path);
	json::jobject data;

	if(!file.is_open()){
		cout << "Failed to open: " << path << endl;
		exit(0);
	}

	stringstream buffer;
	buffer << file.rdbuf();
	file.close();

	json::jobject obj = json::jobject::parse(buffer.str().c_str());
	json::key_list_t keys = obj.list_keys();

	for (size_t i = 0; i < keys.size(); i++) {
		std::string key = keys[i];
		std::string value = obj[key].as_string();
		device_descriptions[key] = value;
	}
}

int main(){
	signal(SIGINT, handleSignal);
	struct mosquitto *mosq;
	int rc, id=12;

    try{
        ssdp = new SSDPController(true);
    }catch(const std::exception &e){
        cerr << e.what() << endl;
        return 1;
    }

	loadDevices();
    ssdp->start();

    
	mosquitto_lib_init();
	mosq = mosquitto_new("smart_garden_controller", true, &id);
	mosquitto_connect_callback_set(mosq, on_connect);

	rc = mosquitto_connect(mosq, "0.0.0.0", 1883, 10);
	if(rc) {
		printf("Could not connect to Broker with return code %d\n", rc);
		exit(0);
	}
	cout << "Connected to the broker\n";


	mosquitto_loop_start(mosq);

	printf("Press Enter to quit...\n");
	getchar();


	ssdp->getAvailableDevices();


	mosquitto_loop_stop(mosq, true);
	mosquitto_disconnect(mosq);
	mosquitto_destroy(mosq);
	mosquitto_lib_cleanup();
    ssdp->stop();
    delete ssdp;

    return 0;
}