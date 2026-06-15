#include "SSDPController.hpp"
#include "SSDPCommon.hpp"

SSDPController::SSDPController(bool debug){
    debug_ = debug;
    m_searchMsg = SSDP::buildMSearch();
    loadWhitelist("config/whitelist.json");
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
    bool isNew = false;
    {
        std::lock_guard<std::mutex> lock(mx);

        auto it = on_devices.find(dev.uuid);

        if(it != on_devices.end()){
            it->second.lastSeen = std::chrono::steady_clock::now();
        }else{
            off_devices.erase(dev.uuid);
            unreachable_devices.erase(dev.uuid);

            dev.lastSeen = std::chrono::steady_clock::now();
            on_devices.emplace(dev.uuid, dev);
            isNew = true;
        }
    }
    if(onDeviceAdded && isNew){
        onDeviceAdded(dev);
    }
}

void SSDPController::removeDevice(Device &dev){
    bool isRemoved = false;
    {
        std::lock_guard<std::mutex> lock(mx);
        if(on_devices.count(dev.uuid) || unreachable_devices.count(dev.uuid)){
            on_devices.erase(dev.uuid);
            unreachable_devices.erase(dev.uuid);
            isRemoved = true;
        }
        off_devices.emplace(dev.uuid, dev);
    }
    if(isRemoved && onDeviceRemoved){
        onDeviceRemoved(dev);
    }
}

void SSDPController::livenessCheckLoop(){
    while(running){
        auto now = std::chrono::steady_clock::now();
        std::vector<Device> list;
        {
            std::lock_guard<std::mutex> lock(mx);
            for (auto it = on_devices.begin(); it != on_devices.end();){
                auto &[uuid, dev] = *it;
                auto age = std::chrono::duration_cast<std::chrono::seconds>(now - dev.lastSeen).count();

                if(age > dev.maxAge + 2){
                    unreachable_devices.emplace(uuid, dev);
                    list.push_back(dev);
                    it = on_devices.erase(it);
                }else{
                    ++it;
                }
            }
        }
        for(auto& d : list){
            if(onDeviceExpired) onDeviceExpired(d);
        }
        std::this_thread::sleep_for(std::chrono::seconds(EXPIRE_TIMEOUT));
    }
}


void SSDPController::setupSocket(){
    if((socket_fd_ = socket(AF_INET, SOCK_DGRAM, 0)) < 0){
        throw std::runtime_error("[SSDP] Failed at socket()\n");
    }

    int yes = 1;
    if(setsockopt(socket_fd_, SOL_SOCKET, SO_REUSEADDR, &yes, sizeof(yes)) < 0){
        close(socket_fd_);
        throw std::runtime_error("[SSDP] Failed at setsockopt()\n");
    }

    struct timeval tv{};
    tv.tv_sec = SOCKET_TIMEOUT;
    if(setsockopt(socket_fd_, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv)) < 0){
        close(socket_fd_);
        throw std::runtime_error("[SSDP] Failed at setsockopt()\n");
    }

    sockaddr_in addr{};
    addr.sin_family = AF_INET;
    addr.sin_port = htons(1900);
    addr.sin_addr.s_addr = INADDR_ANY;

    if(bind(socket_fd_, (sockaddr*)&addr, sizeof(addr)) < 0){
        close(socket_fd_);
        throw std::runtime_error("[SSDP] Failed at bind()\n");
    }

    ip_mreq mreq{};
    mreq.imr_multiaddr.s_addr = inet_addr(SSDP::MULTICAST_ADDRESS);
    mreq.imr_interface.s_addr = INADDR_ANY;

    if(setsockopt(socket_fd_, IPPROTO_IP, IP_ADD_MEMBERSHIP, &mreq, sizeof(mreq)) < 0){
        close(socket_fd_);
        throw std::runtime_error("[SSDP] Failed at setsockopt()\n");
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

    if ((sendto(socket_fd_, msg.c_str(), msg.size(), 0, (sockaddr*)&multicastAddr, sizeof(multicastAddr)) < 0) && debug_)
    {
        safeCout("[SSDP] Controller NOTIFY send failed for: " + dev.uuid + "\n");
    } else {
        safeCout("[SSDP] Controller sent BYEBYE for: " + dev.uuid + "\n");
    }
}

void SSDPController::start(){
    running = true;

    listenerThread = std::thread(&SSDPController::listenLoop, this);
    livenessCheckThread = std::thread(&SSDPController::livenessCheckLoop, this);
    searchThread = std::thread(&SSDPController::searchLoop, this);
}

void SSDPController::stop(){
    if(!running) return;

    running = false;
    sleepCv.notify_all();

    if (listenerThread.joinable()) listenerThread.join();
    if (livenessCheckThread.joinable()) livenessCheckThread.join();
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
            removeDevice(dev);
            safeCout("[SSDP] Device BYEBYE: " + dev.uuid + "\n");
        }else if(msg.find("ssdp:alive") != std::string::npos){
            updateDevice(dev);
            safeCout("[SSDP] Device ALIVE: " + dev.uuid + "\n");
        }else if(msg.find("ssdp:discover") != std::string::npos){
            updateDevice(dev);
            safeCout("[SSDP] Device responding to M-search: " + dev.uuid + "\n");
        }
    }
}

void SSDPController::searchLoop(){
    std::unique_lock<std::mutex> mx(sleep_mx);

    while(running){
        if (sendto(socket_fd_, m_searchMsg.c_str(), m_searchMsg.size(), 0, (sockaddr*)&multicastAddr, sizeof(multicastAddr)) < 0){
            safeCout("[SSDP] Controller SEARCH failed!\n");
        } else {
            safeCout("[SSDP] Controller sent M-SEARCH\n");
        }

        if(sleepCv.wait_for(mx, std::chrono::seconds(SEARCH_TIMEOUT), [this]{return !running;})){
            break;
        }
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

    std::string uuid = find("USN: ");

    if(whitelist.find(uuid) == whitelist.end()){
        dev.uuid = "";
        dev.location = "";
        dev.st = "";
        return dev;
    }

    dev.uuid = uuid;
    dev.location = find("LOCATION: ");
    dev.st = find("ST: ");

    std::string age = find("CACHE-CONTROL: max-age=");

    try {
        dev.maxAge = std::stoi(age);
    } catch (...) {
        dev.maxAge = 10;
    }

    return dev;
}

void SSDPController::loadWhitelist(const std::string& path)
{
    std::ifstream file(path);
    if (!file.is_open())
        throw std::runtime_error("Can't open whitelist file\n\n");

    json data = json::parse(file);

    for(const auto &item : data["whitelist"])
        whitelist.insert(item.get<std::string>());
}

void SSDPController::setOnDeviceAdded(std::function<void(const Device&)> callback){
    onDeviceAdded = callback;
}

void SSDPController::setOnDeviceRemoved(std::function<void(const Device &dev)> callback){
    onDeviceRemoved = callback;
}

void SSDPController::setOnDeviceExpired(std::function<void(const Device &dev)> callback){
    onDeviceExpired = callback;
}