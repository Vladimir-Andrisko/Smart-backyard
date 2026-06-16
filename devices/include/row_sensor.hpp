#ifndef ROW_SENSOR_HPP
#define ROW_SENSOR_HPP

#include "network/ssdp/SSDPDevice.hpp"
#include "mosquitto.h"
#include "csignal"
#include <random>
#include <chrono>
#include <atomic>
#include <thread>
#include <mutex>
#include "nlohmann/json.hpp"
using json = nlohmann::json;

// For simulation purposes
static constexpr const char* LIGHT_SENSOR_TOPIC = "garden/global/sensor/light_sensor";
static constexpr const char* HUMIDITY_SENSOR_TOPIC = "garden/global/sensor/humidity_sensor";

static constexpr int REGULAR_SLEEP = 30;
static constexpr int WATERING_SLEEP = 30;
static constexpr int mqtt_QoS = 0;
static constexpr int mqtt_alive = 120;

void handleSignal(int);
int generateHumidity();
std::string construct_msg(int humidity);
void parseDesc(const std::string &file_path);
void on_connect(struct mosquitto *mosq, void *obj, int rc);
void on_message(struct mosquitto *mosq, void *obj, const struct mosquitto_message *msg);


#endif