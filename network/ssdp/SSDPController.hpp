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
#include <unordered_set>
#include <condition_variable>

#include <iostream>
#include <chrono>
#include <vector>
#include <fstream>
#include <sstream>

#include "json.hpp"
#include "httpserver.hpp"

struct Device{
    std::string uuid;
    std::string location;
    std::string st;
    bool alive;

    std::chrono::steady_clock::time_point lastSeen;
    int maxAge;
};

static constexpr int SOCKET_TIMEOUT = 1;
static constexpr int SEARCH_TIMEOUT = 30;
static constexpr int EXPIRE_TIMEOUT = 1; 
static constexpr int RECONNECT_COOLDOWN = 10;

class SSDPController
{
private:
    Device parseMessage(const std::string &msg);
    void sendControllerNotify(const Device &dev);

    void setupSocket();
    void listenLoop();
    void livenessCheckLoop();
    void searchLoop();
    void safeCout(const std::string &msg);
    void loadWhitelist(const std::string& path);

    std::map<std::string, Device> device_dict;
    // used to ignore stale packets from disconnecting devices
    std::map<std::string, std::chrono::steady_clock::time_point> device_reconnect_cooldowns;
    std::unordered_set<std::string> whitelist;

    int socket_fd_;
    sockaddr_in multicastAddr{};

    std::string m_searchMsg;

    std::atomic<bool> running;

    std::thread listenerThread;
    std::thread livenessCheckThread;
    std::thread searchThread;

    std::mutex mx;
    std::mutex cout_mx;
    std::mutex sleep_mx;
    std::condition_variable sleepCv;

    bool debug_;
    int QoS;

public:
    SSDPController(bool debug);
    ~SSDPController();

    void updateDevice(Device &dev);
    void removeDevice(const std::string &uuid);

    void start();
    void stop();
};

#endif