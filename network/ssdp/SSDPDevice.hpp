#ifndef SSDP_DEVICE_HPP_
#define SSDP_DEVICE_HPP_

#include "SSDPCommon.hpp"

#include <thread>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <string>
#include <unistd.h>
#include <atomic>
#include <mutex>
#include <condition_variable>
#include <iostream>
#include <fstream>

#include "nlohmann/json.hpp"

static constexpr int NOTIFY_TIMEOUT = 30;
static constexpr int SOCKET_TIMEOUT = 1;
static constexpr int BUFFER_SIZE = 1024;

using json = nlohmann::json;

class SSDPDevice
{
private:
    void init(const std::string& uuid, const std::string& deviceType, const std::string& location, const int &max_age);
    void setupSocket();

    void listenLoop();
    void aliveLoop();

    void sendNotifyAlive();
    void sendNotifyByebye();
    void respondToSearch(const sockaddr_in& sender);
    void safeCout(const std::string msg);

    int socket_fd_;
    sockaddr_in multicastAddr{};

    std::string alive_msg;
    std::string byebye_msg;
    std::string response_msg;

    std::atomic<bool> running;

    std::thread listenerThread;
    std::thread aliveThread;

    std::mutex cout_mx;
    std::mutex sleepMutex;
    std::condition_variable sleepCv;

    std::string location;
    std::string uuid;
    std::string deviceType;
    int QoS;
    
public:
    SSDPDevice(const std::string& uuid, const std::string& deviceType, const std::string& location, const int& max_age, int qos);
    SSDPDevice(const std::string& location, int qos);
    ~SSDPDevice();

    void start();
    void stop();
};


#endif