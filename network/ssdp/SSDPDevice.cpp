#include "SSDPDevice.hpp"



void SSDPDevice::init(const std::string& uuid, const std::string& deviceType, const std::string& location)
{
    this->uuid = uuid;
    this->deviceType = deviceType;
    this->location = location;
    alive_msg = SSDP::buildNotifyAlive(uuid, location, deviceType);
    byebye_msg = SSDP::buildNotifyByebye(uuid, deviceType);
    response_msg = SSDP::buildResponse(uuid, location, deviceType);

    std::cout << "Alive msg: \n" << alive_msg << std::endl << std::endl;
    std::cout << "Bye msg: \n" << byebye_msg << std::endl << std::endl;
    std::cout << "Response msg: \n" << response_msg << std::endl << std::endl;

    setupSocket();
}

SSDPDevice::SSDPDevice(const std::string& uuid, const std::string& deviceType, const std::string& location, int qos)
{
    this->QoS = qos;
    init(uuid, deviceType, location);
}

SSDPDevice::SSDPDevice(const std::string& location, int qos)
{
    try{
        this->QoS = qos;
        std::ifstream file(location);
        std::stringstream buffer;
        buffer << file.rdbuf();
        std::string content = buffer.str();
        json::jobject obj = json::jobject::parse(content.c_str());

        init(obj["uuid"], obj["group"], location);

    }catch(const std::exception &e){
        throw std::runtime_error("Can't initialize device. Unable to parse json file!");
    }
}

SSDPDevice::~SSDPDevice()
{
    stop();
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
    for(int i = 0; i < QoS; i++){
        if(sendto(socket_fd_, (const char*)alive_msg.c_str(), alive_msg.size(), 0, (const sockaddr *)&multicastAddr, sizeof(multicastAddr)) < 0){
            safeCout("[WARN] Failed to send alive msg!\n");
        }
    }
}

void SSDPDevice::sendNotifyByebye(){
    for(int i = 0; i < QoS; i++){
        if(sendto(socket_fd_, (const char*)byebye_msg.c_str(), byebye_msg.size(), 0, (const sockaddr *)&multicastAddr, sizeof(multicastAddr)) < 0){
            safeCout("[WARN] Failed to send byebye msg!\n");
        }
    }   
}

void SSDPDevice::respondToSearch(const sockaddr_in& sender){
    for(int i = 0; i < QoS; i++){
        if(sendto(socket_fd_, (const char*)response_msg.c_str(), response_msg.size(), 0, (const sockaddr *)&sender, sizeof(sender)) < 0){
            safeCout("[WARN] Failed to send response msg!\n");
        }
    }
}

void SSDPDevice::aliveLoop(){
    std::unique_lock<std::mutex> mx(sleepMutex);

    while(running){
        if(sleepCv.wait_for(mx, std::chrono::seconds(NOTIFY_TIMEOUT), [this]{return !running;})){
            break;
        }

        sendNotifyAlive();
    }
}

void SSDPDevice::listenLoop() {
    char buffer[BUFFER_SIZE];

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

    sleepCv.notify_all();
    sendNotifyByebye();

    if (listenerThread.joinable()) listenerThread.join();
    if (aliveThread.joinable()) aliveThread.join();

    if(socket_fd_ >= 0){
        close(socket_fd_);
        socket_fd_ = -1;
    }
}


void SSDPDevice::safeCout(const std::string msg){
    std::unique_lock<std::mutex> mx(cout_mx);

    std::cout << msg;
}