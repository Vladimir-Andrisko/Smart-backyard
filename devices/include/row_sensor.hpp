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

static constexpr int SLEEP_TIME = 10;
static constexpr int mqtt_QoS = 0;
static constexpr int mqtt_alive = 120;

void handleSignal(int);
int generateHumidity();
std::string construct_msg(int humidity);
std::string loadTopicFromJson(const std::string& path);
void parseDesc(const std::string &file_path);


#endif