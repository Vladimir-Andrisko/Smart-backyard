#ifndef TEMPERATURE_SENSOR_HPP
#define TEMPERATURE_SENSOR_HPP

static constexpr int SLEEP_TIME = 300;
static constexpr int QoS = 0;
static constexpr int keepAlive = 60;

int generateTemperature();
void handleSignal(int);
std::string construct_msg(int humidity);
std::string loadTopicFromJson(const std::string& path);

#endif