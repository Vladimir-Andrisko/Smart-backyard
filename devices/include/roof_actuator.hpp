#ifndef HUMIDITY_SENSOR_HPP
#define HUMIDITY_SENSOR_HPP

static constexpr int SLEEP_TIME = 10;
static constexpr int QoS = 0;
static constexpr int keepAlive = 60;

void handleSignal(int);
std::string loadTopicFromJson(const std::string& path);

#endif