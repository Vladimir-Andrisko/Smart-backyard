#ifndef CONTROLLER_HPP_
#define CONTROLLER_HPP_

#include "network/ssdp/SSDPController.hpp"
#include <iostream>
#include <csignal>
#include <fstream>
#include <string>
#include <mutex>
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
static constexpr const char* ROW1_SENSOR_TOPIC = "garden/row1/sensor/row_sensor";

enum DeviceState{ON, OFF, UNREACHABLE};

void handleSignal(int);
void on_connect(struct mosquitto *mosq, void *obj, int rc);
void on_message(struct mosquitto *mosq, void *obj, const struct mosquitto_message *msg);
std::string generateActuatorMsg(std::string uuid, std::string position);
void setup_callback();

#endif