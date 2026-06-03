#ifndef HUMIDITY_SENSOR_HPP
#define HUMIDITY_SENSOR_HPP

static constexpr int SLEEP_TIME = 300;
static constexpr int QoS = 0;
static constexpr int keepAlive = 60;

void handleSignal(int);
int generateHumidity();
std::string construct_msg(int humidity);
std::string loadTopicFromJson(const std::string& path);


#endif