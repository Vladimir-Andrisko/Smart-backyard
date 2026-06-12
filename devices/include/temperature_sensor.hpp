#ifndef TEMPERATURE_SENSOR_HPP
#define TEMPERATURE_SENSOR_HPP

#include "network/ssdp/SSDPDevice.hpp"
#include "mosquitto.h"
#include "csignal"
#include <random>
#include <chrono>
#include <atomic>
#include <thread>
#include <mutex>

#include "nlohmann/json.hpp"

static constexpr int SLEEP_TIME = 10;
static constexpr int QoS = 0;
static constexpr int keepAlive = 60;

int generateTemperature();
void handleSignal(int);
std::string loadTopicFromJson(const std::string& path);
std::string construct_msg(int temperature);

#endif