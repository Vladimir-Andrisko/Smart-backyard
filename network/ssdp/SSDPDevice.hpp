#ifndef SSDP_DEVICE_HPP_
#define SSDP_DEVICE_HPP_

#include "SSDPCommon.hpp"

#include <thread>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <string>
#include <unistd.h>
#include <stdio.h>
#include <atomic>
#include <unordered_map>
#include <mutex>

static constexpr int NOTIFY_TIMEOUT = 15;
static constexpr int SOCKET_TIMEOUT = 1;

class SSDPDevice
{
private:
    void init(const std::string& uuid, const std::string& deviceType, const std::string& location);
    void setupSocket();

    void listenLoop();
    void aliveLoop();

    void sendNotifyAlive();
    void sendNotifyByebye();
    void respondToSearch(const sockaddr_in& sender);
    void writeToJSON(void);

    int socket_fd_;
    sockaddr_in multicastAddr{};

    std::string alive_msg;
    std::string byebye_msg;
    std::string response_msg;

    std::atomic<bool> running;

    std::thread listenerThread;
    std::thread aliveThread;

    // JSON info
    std::string location;
    std::string uuid;
    std::string deviceType;
    std::string state;

    std::mutex mapMutex;
    std::unordered_map<std::string, std::mutex> fileMutexes;
    
public:
    SSDPDevice(const std::string& uuid, const std::string& deviceType, const std::string& location);
    SSDPDevice(const std::string& location);
    ~SSDPDevice();

    void start();
    void stop();
};


#endif