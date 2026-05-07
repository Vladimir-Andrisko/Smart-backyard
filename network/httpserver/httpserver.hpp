#ifndef HTTP_SERVER
#define HTTP_SERVER
#include <iostream>
#include <mutex>
#include <unordered_map>
#include <string>
#include <fstream>
#include <sstream>
#include "json/json.hpp"

// Proxy namespace that functions as a stand-in for a HTTP server
namespace HTTPServer
{
    #define DEVICE_SERVICE_SUBTREE "Service"
    std::mutex mapMutex;
    std::unordered_map<std::string, std::mutex> fileMutexes;

    std::string readDeviceServiceVariable(const std::string &location, const std::string &key)
    {
        std::mutex *mtx;
        {
            std::lock_guard<std::mutex> lock(mapMutex);
            mtx = &fileMutexes[location];
        }

        std::lock_guard<std::mutex> lock(*mtx);

        try
        {
            std::ifstream file(location);
            std::stringstream buffer;
            buffer << file.rdbuf();
            file.close();

            json::jobject obj = json::jobject::parse(buffer.str().c_str());
            json::jobject service = json::jobject::parse(obj[DEVICE_SERVICE_SUBTREE].as_string().c_str());
            return service[key];
        }
        catch(const std::exception &e)
        {
            std::cerr << "Failed to read from JSON: " << e.what() << std::endl;
            std::cerr << "Location: " << location << std::endl;
            return "";
        }
    }

    std::string readDeviceGeneralVariable(const std::string &location, const std::string &key)
    {
        std::mutex *mtx;
        {
            std::lock_guard<std::mutex> lock(mapMutex);
            mtx = &fileMutexes[location];
        }

        std::lock_guard<std::mutex> lock(*mtx);

        try
        {
            std::ifstream file(location);
            std::stringstream buffer;
            buffer << file.rdbuf();
            file.close();

            json::jobject obj = json::jobject::parse(buffer.str().c_str());
            return obj[key];
        }
        catch(const std::exception &e)
        {
            std::cerr << "Failed to read from JSON: " << e.what() << std::endl;
            std::cerr << "Location: " << location << std::endl;
            return "";
        }
    }

    bool writeDeviceGeneralVariable(const std::string &location, const std::string &key, const std::string &value)
    {
        std::mutex *mtx;
        {
            std::lock_guard<std::mutex> lock(mapMutex);
            mtx = &fileMutexes[location];
        }

        std::lock_guard<std::mutex> lock(*mtx);

        try
        {
            std::ifstream file(location);
            std::stringstream buffer;
            buffer << file.rdbuf();
            file.close();

            json::jobject obj = json::jobject::parse(buffer.str().c_str());
            json::jobject service = json::jobject::parse(obj[DEVICE_SERVICE_SUBTREE].as_string().c_str());
            obj[key] = value;
            std::ofstream outputFile(location);
            outputFile << obj.pretty();
            outputFile.close();
            return true;
        }
        catch(const std::exception &e)
        {
            std::cerr << "Failed to write to JSON: " << e.what() << std::endl;
            std::cerr << "Location: " << location << std::endl;
            return false;
        }
    }

    bool writeDeviceServiceVariable(const std::string &location, const std::string &key, const std::string &value)
    {
        std::mutex *mtx;
        {
            std::lock_guard<std::mutex> lock(mapMutex);
            mtx = &fileMutexes[location];
        }

        std::lock_guard<std::mutex> lock(*mtx);

        try
        {
            std::ifstream file(location);
            std::stringstream buffer;
            buffer << file.rdbuf();
            file.close();

            json::jobject obj = json::jobject::parse(buffer.str().c_str());
            json::jobject service = json::jobject::parse(obj[DEVICE_SERVICE_SUBTREE].as_string().c_str());
            service[key] = value;
            obj[DEVICE_SERVICE_SUBTREE] = service;
            std::ofstream outputFile(location);
            outputFile << obj.pretty();
            outputFile.close();
            return true;
        }
        catch(const std::exception &e)
        {
            std::cerr << "Failed to write to JSON: " << e.what() << std::endl;
            std::cerr << "Location: " << location << std::endl;
            return false;
        }
    }
};

#endif