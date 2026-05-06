#include "SSDPDevice.hpp"
#include <iostream>
#include <fstream>
#include <sstream>
#include "json/json.hpp"

void SSDPDevice::init(const std::string& uuid, const std::string& deviceType, const std::string& location)
{
    alive_msg = SSDP::buildNotifyAlive(uuid, location, deviceType);
    byebye_msg = SSDP::buildNotifyByebye(uuid, deviceType);
    response_msg = SSDP::buildResponse(uuid, location, deviceType);
    setupSocket();
}

SSDPDevice::SSDPDevice(const std::string& uuid, const std::string& deviceType, const std::string& location)
{
    init(uuid, deviceType, location);
}

SSDPDevice::SSDPDevice(const std::string& jsonFile)
{
    try{
        std::ifstream file(jsonFile);
        std::stringstream buffer;
        buffer << file.rdbuf();
        std::string content = buffer.str();
        json::jobject obj = json::jobject::parse(content.c_str());
        init(obj["uuid"], obj["deviceType"], obj["location"]);

    }catch(const std::exception &e){
        std::cerr << e.what() << std::endl;
        init("err", "err", "err");
    }
}

SSDPDevice::~SSDPDevice()
{
    stop();
    if(socket_fd_ >= 0) close(socket_fd_);
}

void SSDPDevice::setupSocket(){
    if((socket_fd_ = socket(AF_INET, SOCK_DGRAM, 0)) < 0){
        throw std::runtime_error("SSDP: socket() failed!\n");
    }

    int yes = 1;
    if (setsockopt(socket_fd_, SOL_SOCKET, SO_REUSEADDR, &yes, sizeof(yes)) < 0) {
        close(socket_fd_);
        throw std::runtime_error("SSDP: set socket reuse address failed!\n");
    }

    struct timeval tv{};
    tv.tv_sec = SOCKET_TIMEOUT; // Timeout for recv
    if (setsockopt(socket_fd_, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv)) < 0) {
        close(socket_fd_);
        throw std::runtime_error("SSDP: set socket timeout failed!\n");
    }

    sockaddr_in addr{};
    addr.sin_family = AF_INET;
    addr.sin_port = htons(SSDP::PORT);
    addr.sin_addr.s_addr = INADDR_ANY;

    if (bind(socket_fd_, (sockaddr*)&addr, sizeof(addr)) < 0) {
        close(socket_fd_);
        throw std::runtime_error("SSDP: bind() failed!\n");
    }

    ip_mreq mreq{};
    mreq.imr_multiaddr.s_addr = inet_addr(SSDP::MULTICAST_ADDRESS);
    mreq.imr_interface.s_addr = INADDR_ANY;

    if (setsockopt(socket_fd_, IPPROTO_IP, IP_ADD_MEMBERSHIP, &mreq, sizeof(mreq)) < 0) {
        close(socket_fd_);
        throw std::runtime_error("SSDP: IP_ADD_MEMBERSHIP failed!\n");
    }

    multicastAddr.sin_family = AF_INET;
    multicastAddr.sin_port = htons(SSDP::PORT);
    multicastAddr.sin_addr.s_addr = inet_addr(SSDP::MULTICAST_ADDRESS);
}

void SSDPDevice::sendNotifyAlive(){
    if(sendto(socket_fd_, (const char*)alive_msg.c_str(), alive_msg.size(), 0, (const sockaddr *)&multicastAddr, sizeof(multicastAddr)) < 0){
        std::cerr << "[WARN] Failed to send alive msg!\n";
    }
}

void SSDPDevice::sendNotifyByebye(){
    if(sendto(socket_fd_, (const char*)byebye_msg.c_str(), byebye_msg.size(), 0, (const sockaddr *)&multicastAddr, sizeof(multicastAddr)) < 0){
        std::cerr << "[WARN] Failed to send byebye msg!\n";
    }
}

void SSDPDevice::respondToSearch(const sockaddr_in& sender){
    if(sendto(socket_fd_, (const char*)response_msg.c_str(), response_msg.size(), 0, (const sockaddr *)&sender, sizeof(sender)) < 0){
        std::cerr << "[WARN] Failed to send response msg!\n";
    }
}

void SSDPDevice::aliveLoop(){
    while(running){
        std::this_thread::sleep_for(std::chrono::seconds(NOTIFY_TIMEOUT));
        sendNotifyAlive();
    }
}

void SSDPDevice::listenLoop() {
    char buffer[1024];

    while (running) {
        sockaddr_in sender{};
        socklen_t len = sizeof(sender);

        int n = recvfrom(socket_fd_, buffer, sizeof(buffer) - 1, 0, (sockaddr*)&sender, &len);
        if (n <= 0) continue;

        buffer[n] = '\0';
        std::string msg(buffer);

        if (msg.find("M-SEARCH") != std::string::npos && msg.find("ssdp:discover") != std::string::npos)
        {
            respondToSearch(sender);
        }
    }
}

void SSDPDevice::start() {
    if (running) return;

    running = true;

    sendNotifyAlive();

    listenerThread = std::thread(&SSDPDevice::listenLoop, this);
    aliveThread = std::thread(&SSDPDevice::aliveLoop, this);
}

void SSDPDevice::stop() {
    if (!running) return;

    running = false;

    sendNotifyByebye();

    if (listenerThread.joinable()) listenerThread.join();
    if (aliveThread.joinable()) aliveThread.join();
}

