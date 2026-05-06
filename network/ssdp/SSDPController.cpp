#include "SSDPController.hpp"
#include "SSDPCommon.hpp"
#include "json/json.hpp"
#include <iostream>
#include <chrono>
#include <vector>
#include <fstream>
#include <sstream>

SSDPController::SSDPController(bool debug){
    debug_ = debug;
    m_searchMsg = SSDP::buildMSearch();
    setupSocket();
}

SSDPController::~SSDPController(){
    stop();
    if(socket_fd_ >= 0) close(socket_fd_);
}

void SSDPController::safeCout(const std::string &msg){
    std::unique_lock<std::mutex> ul(cout_mx);
    if(debug_)
        std::cout << msg;
}

void SSDPController::updateDevice(Device &dev){
    std::unique_lock<std::mutex> ul(mx);
    device_dict[dev.uuid] = dev;
}

void SSDPController::removeDevice(const std::string &uuid){
    std::unique_lock<std::mutex> ul(mx);
    std::string location = device_dict[uuid].location;

    device_dict.erase(uuid);
}

void SSDPController::removeExperiedDevices(){
    std::vector<Device> expired;
    auto now = std::chrono::steady_clock::now();

    {
        std::lock_guard<std::mutex> lock(mx);

        for(auto it = device_dict.begin(); it != device_dict.end();){
            auto time = std::chrono::duration_cast<std::chrono::seconds>(
            now - it->second.lastSeen).count();

            if(time >= it->second.maxAge + 2){
                Device dev = it->second;
                expired.push_back(dev);
                it = device_dict.erase(it);
            }else{
                ++it;
            }
        }
    }

    for(const auto& dev : expired){
        safeCout("[WARN] Device expired: " + dev.uuid + "\n");
        sendControllerNotify(dev);
    }
}



void SSDPController::setupSocket(){
    if((socket_fd_ = socket(AF_INET, SOCK_DGRAM, 0)) < 0){
        throw std::runtime_error("Failed at socket()\n");
    }

    int yes = 1;
    if(setsockopt(socket_fd_, SOL_SOCKET, SO_REUSEADDR, &yes, sizeof(yes)) < 0){
        close(socket_fd_);
        throw std::runtime_error("Failed at setsockopt()\n");
    }

    struct timeval tv{};
    tv.tv_sec = SOCKET_TIMEOUT;
    if(setsockopt(socket_fd_, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv)) < 0){
        close(socket_fd_);
        throw std::runtime_error("Failed at setsockopt()\n");
    }

    sockaddr_in addr{};
    addr.sin_family = AF_INET;
    addr.sin_port = htons(1900);
    addr.sin_addr.s_addr = INADDR_ANY;

    if(bind(socket_fd_, (sockaddr*)&addr, sizeof(addr)) < 0){
        close(socket_fd_);
        throw std::runtime_error("Failed at bind()\n");
    }

    ip_mreq mreq{};
    mreq.imr_multiaddr.s_addr = inet_addr(SSDP::MULTICAST_ADDRESS);
    mreq.imr_interface.s_addr = INADDR_ANY;

    if(setsockopt(socket_fd_, IPPROTO_IP, IP_ADD_MEMBERSHIP, &mreq, sizeof(mreq)) < 0){
        close(socket_fd_);
        throw std::runtime_error("Failed at setsockopt()\n");
    }

    multicastAddr.sin_family = AF_INET;
    multicastAddr.sin_port = htons(1900);
    multicastAddr.sin_addr.s_addr = inet_addr(SSDP::MULTICAST_ADDRESS);
}

void SSDPController::sendControllerNotify(const Device &dev) {
    std::string msg = 
            "NOTIFY * HTTP/1.1\r\n"
            "HOST: 239.255.255.250:1900\r\n"
            "NT: upnp:rootdevice\r\n"
            "NTS: ssdp:byebye\r\n"
            "USN: " + dev.uuid + "\r\n"
            "\r\n";

    if (sendto(socket_fd_, msg.c_str(), msg.size(), 0, (sockaddr*)&multicastAddr, sizeof(multicastAddr)) < 0)
    {
        safeCout("[WARN] Controller NOTIFY send failed for: " + dev.uuid + "\n");
    } else {
        safeCout("[INFO] Controller sent BYEBYE for: " + dev.uuid + "\n");
    }
}

void SSDPController::start(){
    running = true;

    listenerThread = std::thread(&SSDPController::listenLoop, this);
    cleanupThread = std::thread(&SSDPController::cleanupLoop, this);
    searchThread = std::thread(&SSDPController::searchLoop, this);
}

void SSDPController::stop(){
    if(!running) return;

    running = false;

    if (listenerThread.joinable()) listenerThread.join();
    if (cleanupThread.joinable()) cleanupThread.join();
    if (searchThread.joinable()) searchThread.join();
}

void SSDPController::listenLoop(){
    char buffer[2048];

    while(running){
        sockaddr_in sender{};
        socklen_t len = sizeof(sender);

        int size = recvfrom(socket_fd_, buffer, sizeof(buffer) - 1, 0, (sockaddr*) &sender, &len);
        if (size <= 0) continue;

        buffer[size] = '\0';
        std::string msg(buffer);
        
        Device dev = parseMessage(msg);

        if (dev.uuid.empty()) continue;

        if (msg.find("ssdp:byebye") != std::string::npos) {
            removeDevice(dev.uuid);
            safeCout("[INFO] Device BYEBYE: " + dev.uuid + "\n");
        }else{
            dev.lastSeen = std::chrono::steady_clock::now();
            updateDevice(dev);
            safeCout("[INFO] Device updated: " + dev.uuid + "\n");
        }
    }

}

void SSDPController::cleanupLoop(){
    while(running){
        removeExperiedDevices();
        std::this_thread::sleep_for(std::chrono::seconds(EXPIRE_TIMEOUT));
    }
}

void SSDPController::searchLoop(){
    while(running){
        if (sendto(socket_fd_, m_searchMsg.c_str(), m_searchMsg.size(), 0, (sockaddr*)&multicastAddr, sizeof(multicastAddr)) < 0){
            safeCout("[WARN] Controller SEARCH failed!\n");
        } else {
            safeCout("[INFO] Controller sent M-SEARCH\n");
        }
        std::this_thread::sleep_for(std::chrono::seconds(SEARCH_TIMEOUT));
    }
}

Device SSDPController::parseMessage(const std::string &msg){
    Device dev{};

    auto find = [&](const std::string &key) -> std::string{
        size_t pos = msg.find(key);
        if (pos == std::string::npos) return "";

        size_t start = pos + key.length();
        size_t end = msg.find("\r\n", start);

        return msg.substr(start, end - start);
    };

    dev.uuid = find("USN: ");
    dev.location = find("LOCATION: ");
    dev.st = find("ST: ");

    std::string temp = find("CACHE-CONTROL: max-age=");

    std::string debug = "Parsed msg from device: \nUUID: " + dev.uuid + "\nLOCATION: " + dev.location + "\nST: " + dev.st + "\nMAX-AGE: " + temp + "\n\n";
    safeCout(debug);

    if(temp.empty()){
        dev.maxAge = 10;
    }else{
        dev.maxAge = stoi(temp);
    }

    return dev;
}