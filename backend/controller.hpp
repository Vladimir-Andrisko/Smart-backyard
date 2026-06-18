#ifndef CONTROLLER_HPP_
#define CONTROLLER_HPP_

#include "network/ssdp/SSDPController.hpp"
#include <iostream>
#include <csignal>
#include <fstream>
#include <string>
#include <mutex>
#include <thread>
#include <condition_variable>
#include <atomic>
#include <unordered_set>
#include <unordered_map>
#include "mosquitto.h"

#include "nlohmann/json.hpp"
using json = nlohmann::json;
using namespace std;

static constexpr const char* HUMIDITY_SENSOR_TOPIC = "garden/global/sensor/humidity_sensor";
static constexpr const char* TEMPERATURE_SENSOR_TOPIC = "garden/global/sensor/temperature_sensor";
static constexpr const char* ROOF_ACTUATOR_TOPIC_SUB = "garden/global/actuator/roof_actuator";
static constexpr const char* ROOF_ACTUATOR_TOPIC_PUB = "garden/global/actuator/roof_actuator/cmd";
static constexpr const char* LIGHT_SENSOR_TOPIC = "garden/global/sensor/light_sensor";

static constexpr const char* APP_TOPIC_SUB = "garden/app/controller";
static constexpr const char* APP_TOPIC_PUB = "garden/app/app";
static constexpr const char* APP_TOPIC_ALIVE = "garden/app/alive";

static constexpr int CONTROL_REFRESH_RATE = 500;
static constexpr int PRINT_REFRESH_RATE = 500;

struct RowControl{
    std::string sensor_uuid;
    std::string actuator_uuid;
    std::string group;

    int min_moisture;
    int max_moisture;
    int min_pause;
    int max_water; 
    bool watering = false;

    std::chrono::steady_clock::time_point last_watering_end;
    std::chrono::steady_clock::time_point watering_start;
};

void handleSignal(int);
void on_connect(struct mosquitto *mosq, void *obj, int rc);
void on_message(struct mosquitto *mosq, void *obj, const struct mosquitto_message *msg);
std::string generateActuatorMsg(std::string uuid, std::string position);
void setup_callback();
void parseAppData(json &data, struct mosquitto *mosq);
void publish_to_valve(std::string group, std::string msg, struct mosquitto *mosq);
void print_loop();
void control_loop(struct mosquitto *mosq);
void publish_to_valve(std::string topic, std::string msg);

#endif