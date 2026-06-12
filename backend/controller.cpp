#include "controller.hpp"

using namespace std;
SSDPController *ssdp = nullptr;
unordered_set<string> registered_topics;
unordered_map<string, json> device_state;
unordered_map<string, RowSensor> row_sensors;
unordered_map<string, RowActuator> row_actuators;

TemperatureSensor temp_sensor;
HumiditySensor humidity_sensor;
LightSensor light_sensor;
RoofActuator roof_actuator;

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

	cout << "DEBUG: " << data << endl;

	if(topic == HUMIDITY_SENSOR_TOPIC){
		humidity_sensor.humidity = data["Service"]["Humidity"].get<int>();
		cout << "Humidity: " << humidity_sensor.humidity << endl;
	}else if(topic == TEMPERATURE_SENSOR_TOPIC){
		temp_sensor.temperature = data["Service"]["Temperature"].get<int>();
		cout << "Temperature: " << temp_sensor.temperature << endl;
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

	ssdp->setOnDeviceAdded([&](const Device& dev){
		try{
			ifstream file(dev.location);
			if(!file.is_open()){
				cout << "Failed to open device description file: " << dev.location << endl;
				return;
			}
			json desc = json::parse(file);
			string topic = desc["topic"];
			string uuid = desc["uuid"];
			registered_topics.emplace(topic);

			if(register_device(uuid)){
				cout << "Registered new device: " << uuid << endl;
			}
		}catch(exception e){
			cout << "[JSON ERROR] " << e.what() << endl;
		}
	});

	ssdp->setOnDeviceRemoved([&](const std::string& uuid){
		
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


bool register_device(string uuid){
	string name = uuid.erase(0, uuid.find("::")+2);

	if(name == "temperature_sensor"){
		temp_sensor.uuid = uuid;
	}else if(name == "humidity_sensor"){
		humidity_sensor.uuid = uuid;
	}else if(name == "light_sensor"){
		light_sensor.uuid = uuid;
	}else if(name == "roof_actuator"){
		roof_actuator.uuid = uuid;
	}else if(name == "row_sensor"){
		RowSensor dev;
		dev.uuid = uuid;
		row_sensors[uuid] = dev;
	}else if(name == "row_actuator"){
		RowActuator dev;
		dev.uuid = uuid;
		row_actuators[uuid] = dev;
	}else{
		cout << "New device not whitelisted: " << name << endl;
		return false;
	}

	return true;
}