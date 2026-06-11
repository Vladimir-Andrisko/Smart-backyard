#ifndef CONTROLLER_HPP_
#define CONTROLLER_HPP_

#include "network/ssdp/SSDPController.hpp"
#include <iostream>
#include <csignal>
#include <fstream>
#include <string>
#include <sstream>
#include <algorithm>
#include <mutex>
#include <thread>
#include <chrono>
#include "mosquitto.h"

#include "nlohmann/json.hpp"
using json = nlohmann::json;

static constexpr const char* HUMIDITY_SENSOR_TOPIC = "garden/global/sensor/humidity_sensor";
static constexpr const char* TEMPERATURE_SENSOR_TOPIC = "garden/global/sensor/temperature_sensor";
static constexpr const char* ROOF_ACTUATOR_TOPIC_SUB = "garden/global/actuator/roof_actuator/subscriber";
static constexpr const char* ROOF_ACTUATOR_TOPIC_PUB = "garden/global/actuator/roof_actuator/publisher";

void loadDevices();
void handleSignal(int);
void on_connect(struct mosquitto *mosq, void *obj, int rc);
void on_message(struct mosquitto *mosq, void *obj, const struct mosquitto_message *msg);

#endif