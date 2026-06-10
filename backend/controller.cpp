#include "controller.hpp"

using namespace std;
SSDPController *ssdp = nullptr;
unordered_map<string, string> device_descriptions;
mutex mx;

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
	string uuid;
	
	
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

void loadDevices(){
	string path = "backend/device_config.json";
	ifstream file(path);
	json data;

	if(!file.is_open()){
		cout << "Failed to open: " << path << endl;
		exit(0);
	}

	data = json::parse(file);
	file.close();
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

	rc = mosquitto_connect(mosq, "localhost", 1883, 10);
	if(rc) {
		printf("Could not connect to Broker with return code %d\n", rc);
		exit(0);
	}
	cout << "Connected to the broker\n";


	mosquitto_loop_start(mosq);

	printf("Press Enter to quit...\n");
	// getchar();

	// cout << "Sending message to roof\n\n";
	// int ret = mosquitto_publish(mosq, NULL, "garden/global/actuator/roof_actuator", 17, "Hello controller", 0, false);
	// printf("Publish ret = %d\n", ret);

	getchar();
	vector<Device> devices = ssdp->getAllDevices();

	for(auto &d : devices){
		cout << d.uuid << "  " << d.maxAge << "  ";
		if(d.state == DeviceState::ON){
			cout << "ON" << endl;
		}else if(d.state == DeviceState::OFF){
			cout << "OFF" << endl;
		}else if(d.state == DeviceState::UNREACHABLE){
			cout << "UNREACHABLE" << endl;
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