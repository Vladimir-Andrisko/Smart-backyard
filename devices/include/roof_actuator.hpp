#ifndef HUMIDITY_SENSOR_HPP
#define HUMIDITY_SENSOR_HPP

#include <iostream>
#include <csignal>
#include <fstream>
#include <string>
#include <sstream>
#include <algorithm>
#include <mutex>
#include "mosquitto.h"
#include "network/ssdp/SSDPDevice.hpp"

static constexpr int SLEEP_TIME = 10;
static constexpr int QoS = 0;
static constexpr int keepAlive = 60;

void handleSignal(int);
std::string loadTopicFromJson(const std::string& path);
void on_connect(struct mosquitto *mosq, void *obj, int rc);
void on_message(struct mosquitto *mosq, void *obj, const struct mosquitto_message *msg);
std::string loadTopicFromJson(const std::string& path);

#endif