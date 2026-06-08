#include "network/ssdp/SSDPDevice.hpp"
#include <iostream>
#include <csignal>
#include "mosquitto.h"

using namespace std;

SSDPDevice* ssdp = nullptr;

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

int main(int argc, char* argv[]){

    std::signal(SIGINT, handleSignal);

    try{
        if (argc >= 2)
            ssdp = new SSDPDevice(argv[1], 5);
        else
            ssdp = new SSDPDevice("1", "row_sensor", "config/sensor/sensor_test.json", 5);
    }catch(const std::exception &e){
        cerr << e.what() << endl;
        return 1;
    }

    ssdp->start();
    cout << "Zapoceo ssdp\n";

    int rc;
	struct mosquitto * mosq;

	mosquitto_lib_init();

	mosq = mosquitto_new("publisher-test", true, NULL);

	rc = mosquitto_connect(mosq, "0.0.0.0", 1883, 60);
	if(rc != 0){
		printf("Client could not connect to broker! Error Code: %d\n", rc);
		mosquitto_destroy(mosq);
		return -1;
	}
	printf("We are now connected to the broker!\n");

	mosquitto_publish(mosq, NULL, "test/t1", 6, "Hello", 0, false);
    getchar();

    mosquitto_disconnect(mosq);
	mosquitto_destroy(mosq);

	mosquitto_lib_cleanup();

    ssdp->stop();
    cout << "Zavrsio ssdp\n";
    delete ssdp;
    return 0;
}