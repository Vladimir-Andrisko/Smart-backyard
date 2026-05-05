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

static constexpr int NOTIFY_TIMEOUT = 15;
static constexpr int SOCKET_TIMEOUT = 1;

class SSDPDevice
{
private:
    void setupSocket();

    void listenLoop();
    void aliveLoop();

    void sendNotifyAlive();
    void sendNotifyByebye();
    void respondToSearch(const sockaddr_in& sender);

    int socket_fd_;
    sockaddr_in multicastAddr{};

    std::string alive_msg;
    std::string byebye_msg;
    std::string response_msg;

    std::atomic<bool> running;

    std::thread listenerThread;
    std::thread aliveThread;
    
public:
    SSDPDevice(const std::string& uuid, const std::string& deviceType, const std::string& location);
    ~SSDPDevice();

    void start();
    void stop();
};


#endif