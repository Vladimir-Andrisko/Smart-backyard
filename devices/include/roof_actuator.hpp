#ifndef ROOF_ACTUATOR_HPP
#define ROOF_ACTUATOR_HPP

#include <iostream>
#include <csignal>
#include <fstream>
#include <string>
#include <random>
#include <thread>
#include <atomic>
#include <condition_variable>
#include <mutex>

#include "mosquitto.h"
#include "network/ssdp/SSDPDevice.hpp"
#include "nlohmann/json.hpp"

using json = nlohmann::json;

static constexpr const char* ROOF_ACTUATOR_TOPIC_SUB = "garden/global/actuator/roof_actuator/cmd";
static constexpr int mqtt_QoS = 2;
static constexpr int mqtt_alive = 120;

void handleSignal(int);
void on_connect(struct mosquitto *mosq, void *obj, int rc);
void on_message(struct mosquitto *mosq, void *obj, const struct mosquitto_message *msg);
void parseDesc(const std::string &file_path);
std::string construct_msg(std::string position);
int generateRandomTime();

#endif