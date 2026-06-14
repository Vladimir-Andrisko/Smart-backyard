#ifndef ROW_ACTUATOR_HPP
#define ROW_ACTUATOR_HPP

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

static constexpr const char* ROW_ACTUATOR_TOPIC_SUB = "garden/global/actuator/row_actuator/cmd";
static constexpr int SLEEP_TIME = 10;
static constexpr int mqtt_QoS = 0;
static constexpr int mqtt_alive = 120;

void handleSignal(int);
void on_connect(struct mosquitto *mosq, void *obj, int rc);
void on_message(struct mosquitto *mosq, void *obj, const struct mosquitto_message *msg);
void parseDesc(const std::string &file_path);
std::string construct_msg(std::string position);
int generateRandomTime();

#endif