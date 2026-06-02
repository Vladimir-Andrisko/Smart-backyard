#ifndef HTTP_SERVER
#define HTTP_SERVER
#include <iostream>
#include <mutex>
#include <unordered_map>
#include <string>
#include <fstream>
#include <sstream>
#include <memory>
#include "json.hpp"


namespace HTTPServer
{
    constexpr const char* DEVICE_SERVICE_SUBTREE = "Service";
    inline std::mutex mapMutex;
    inline std::unordered_map<std::string, std::shared_ptr<std::mutex>> fileMutexes;

    std::string readDeviceServiceVariable(const std::string &location, const std::string &key);
    std::string readDeviceGeneralVariable(const std::string &location, const std::string &key);
    bool writeDeviceGeneralVariable(const std::string &location, const std::string &key, const std::string &value);
    bool writeDeviceServiceVariable(const std::string &location, const std::string &key, const std::string &value);

};

#endif