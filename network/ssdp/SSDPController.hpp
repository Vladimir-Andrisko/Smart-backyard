#ifndef SSDP_CONTROLLER_HPP_
#define SSDP_CONTROLLER_HPP_

#include <thread>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <string>
#include <unistd.h>
#include <map>
#include <atomic>
#include <mutex>

struct Device{
    std::string uuid;
    std::string location;
    std::string st;

    std::chrono::steady_clock::time_point lastSeen;
    int maxAge;
};

class SSDPController
{
private:
    std::map<std::string, Device> device_dict;
    int socket_fd_;
    sockaddr_in multicastAddr{};

    std::atomic<bool> running;

    std::thread listenerThread;
    std::thread cleanupThread;
    std::mutex mx;

    Device parseMessage(const std::string &msg);
    void sendControllerNotify(const Device &dev);

    void setupSocket();
    void listenLoop();
    void cleanupLoop();

public:
    SSDPController();
    ~SSDPController();

    void updateDevice(const Device &dev);
    void removeDevice(const std::string &uuid);
    void removeExperiedDevices();

    void start();
    void stop();
};

#endif