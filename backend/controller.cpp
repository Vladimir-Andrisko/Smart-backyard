#include "controller.hpp"

using namespace std;
SSDPController *ssdp = nullptr;

void handleSignal(int){
    if (ssdp != nullptr){
		delete ssdp;
        ssdp = nullptr;
    }
    exit(0);
}

void on_message(struct mosquitto *mosq, void *obj, const struct mosquitto_message *msg) {
	string topic(msg->topic);
	std::string payload(static_cast<const char*>(msg->payload),msg->payloadlen);
	
	
	
}

void on_connect(struct mosquitto *mosq, void *obj, int rc) {
	printf("ID: %d\n", * (int *) obj);
	if(rc) {
		printf("Error with result code: %d\n", rc);
		exit(-1);
	}
	mosquitto_subscribe(mosq, NULL, "garden/global/sensor/#", 0);
	mosquitto_subscribe(mosq, NULL, "test/t1", 0);
	mosquitto_message_callback_set(mosq, on_message);
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

	ssdp->setOnDeviceAdded([&](const Device& dev){
		try{
			ifstream file(dev.location);
			if(!file.is_open()){
				cout << "Failed to open device description file: " << dev.location << endl;
				return;
			}
			json desc = json::parse(file);
			cout << desc << endl;
		}catch(exception e){
			cout << "[JSON] " << e.what() << endl;
		}
	});
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