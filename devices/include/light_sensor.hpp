#ifndef LIGHT_SENSOR_HPP
#define LIGHT_SENSOR_HPP

#include "network/ssdp/SSDPDevice.hpp"
#include "mosquitto.h"
#include "csignal"
#include <random>
#include <chrono>
#include <atomic>
#include <thread>
#include <mutex>
#include <string>

#include "nlohmann/json.hpp"

static constexpr int SLEEP_TIME = 10;
static constexpr int QoS = 0;
static constexpr int keepAlive = 60;

int generateBrightness();
void handleSignal(int);
void parseDesc(const std::string &file_path);
std::string construct_msg(int brightness);

#endif