#include "network/ssdp/SSDPDevice.hpp"
#include "roof_actuator.hpp"
#include <iostream>
#include <csignal>
#include <fstream>
#include <string>
#include <sstream>
#include <algorithm>
#include <mutex>
#include "mosquitto.h"

using namespace std;
SSDPDevice *ssdp = nullptr;

void handleSignal(int){
    if(ssdp != nullptr){
        delete ssdp;
    }
    exit(0);
}

void on_message(struct mosquitto *mosq, void *obj, const struct mosquitto_message *msg) {
	string topic(msg->topic);
	std::string payload(static_cast<const char*>(msg->payload),msg->payloadlen);
	string uuid;

    
    int rc = mosquitto_publish(mosq, NULL, topic.c_str(), msg.size(), msg.c_str(), QoS, false);
	
}

void on_connect(struct mosquitto *mosq, void *obj, int rc) {
	printf("ID: %d\n", * (int *) obj);
	if(rc) {
		printf("Error with result code: %d\n", rc);
		exit(-1);
	}
	mosquitto_subscribe(mosq, NULL, "garden/global/actuator/roof_actuator", 0);
	mosquitto_message_callback_set(mosq, on_message);
}

int main(int argc, char* argv[]){
    signal(SIGINT, handleSignal);
    int rc;
	struct mosquitto *mosq;
    string topic;

    try{
        if (argc >= 2)
            ssdp = new SSDPDevice(argv[1], 5);
        else
            ssdp = new SSDPDevice("1", "roof_actuator", "config/actuator/roof_actuator_desc.json", 5);

    }catch(const std::exception &e){
        cerr << e.what() << endl;
        return 1;
    }

    try{
        topic = loadTopicFromJson(argv[1]);
    }catch(const std::exception &e){
        cerr << e.what() << endl;
        return 1;
    }


    ssdp->start();
	mosquitto_lib_init();
	mosq = mosquitto_new("humidity_sensor", true, NULL);

	rc = mosquitto_connect(mosq, "0.0.0.0", 1883, keepAlive);
	if(rc != 0){
		printf("Client could not connect to broker! Error Code: %d\n", rc);
		mosquitto_destroy(mosq);
		return -1;
	}
    mosquitto_loop_start(mosq);    

    getchar();

	mosquitto_disconnect(mosq);
	mosquitto_destroy(mosq);
	mosquitto_lib_cleanup();
    mosquitto_loop_stop(mosq, true);

    ssdp->stop();
    delete ssdp;

    return 0;
}

std::string loadTopicFromJson(const std::string& path){
    std::ifstream file(path);

    if(!file.is_open())
        throw std::runtime_error("Failed to open json file");

    std::stringstream buffer;
    buffer << file.rdbuf();

    json::jobject obj = json::jobject::parse(buffer.str().c_str());

    return obj["topic"].as_string();
}