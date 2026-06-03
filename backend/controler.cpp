#include "network/ssdp/SSDPController.hpp"
#include <iostream>
#include "mosquitto.h"
#include "json.hpp"

using namespace std;

void on_connect(struct mosquitto *mosq, void *obj, int rc) {
	printf("ID: %d\n", * (int *) obj);
	if(rc) {
		printf("Error with result code: %d\n", rc);
		exit(-1);
	}
	mosquitto_subscribe(mosq, NULL, "garden/global/sensor/humidity_sensor", 0);
}

void on_message(struct mosquitto *mosq, void *obj, const struct mosquitto_message *msg) {
	printf("New message with topic %s: %s\n", msg->topic, (char *) msg->payload);
}

int main(){
    SSDPController *ssdp = nullptr;

    try{
        ssdp = new SSDPController(true);
    }catch(const std::exception &e){
        cerr << e.what() << endl;
        return 1;
    }

    ssdp->start();
    cout << "Zapoceo ssdp\n";

    int rc, id=12;

	mosquitto_lib_init();

	struct mosquitto *mosq;

	mosq = mosquitto_new("subscribe-test", true, &id);
	mosquitto_connect_callback_set(mosq, on_connect);
	mosquitto_message_callback_set(mosq, on_message);
	
	rc = mosquitto_connect(mosq, "192.168.100.39", 1883, 10);
	if(rc) {
		printf("Could not connect to Broker with return code %d\n", rc);
		return -1;
	}

	mosquitto_loop_start(mosq);
	printf("Press Enter to quit...\n");
	getchar();
	mosquitto_loop_stop(mosq, true);

	mosquitto_disconnect(mosq);
	mosquitto_destroy(mosq);
	mosquitto_lib_cleanup();


    
    getchar();

    ssdp->stop();
    cout << "Zavrsio ssdp\n";
    delete ssdp;

    return 0;
}