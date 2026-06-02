#include "httpserver.hpp"

namespace HTTPServer{
    std::string readDeviceServiceVariable(const std::string &location, const std::string &key){
        std::shared_ptr<std::mutex> mtx;
            {
                std::lock_guard<std::mutex> lock(mapMutex);

                if(fileMutexes.find(location) == fileMutexes.end())
                    fileMutexes[location] = std::make_shared<std::mutex>();

                mtx = fileMutexes[location];
            }

            std::lock_guard<std::mutex> lock(*mtx);

            try
            {
                std::ifstream file(location);
                if(!file.is_open())
                    throw std::runtime_error("Can't open file: " + location);

                std::stringstream buffer;
                buffer << file.rdbuf();
                file.close();

                json::jobject obj = json::jobject::parse(buffer.str().c_str());
                json::jobject service = (obj[DEVICE_SERVICE_SUBTREE]);
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
        std::shared_ptr<std::mutex> mtx;

        {
            std::lock_guard<std::mutex> lock(mapMutex);

            if(fileMutexes.find(location) == fileMutexes.end())
                fileMutexes[location] = std::make_shared<std::mutex>();

            mtx = fileMutexes[location];
        }

        std::lock_guard<std::mutex> lock(*mtx);

        try
        {
            std::ifstream file(location);
            if(!file.is_open())
                throw std::runtime_error("Can't open file: " + location);

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
        std::shared_ptr<std::mutex> mtx;

        {
            std::lock_guard<std::mutex> lock(mapMutex);

            if(fileMutexes.find(location) == fileMutexes.end())
                fileMutexes[location] = std::make_shared<std::mutex>();

            mtx = fileMutexes[location];
        }

        std::lock_guard<std::mutex> lock(*mtx);

        try
        {
            std::ifstream file(location);
            if(!file.is_open())
                throw std::runtime_error("Can't open file: " + location);

            std::stringstream buffer;
            buffer << file.rdbuf();
            file.close();

            json::jobject obj = json::jobject::parse(buffer.str().c_str());
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
        std::shared_ptr<std::mutex> mtx;

        {
            std::lock_guard<std::mutex> lock(mapMutex);

            if(fileMutexes.find(location) == fileMutexes.end())
                fileMutexes[location] = std::make_shared<std::mutex>();

            mtx = fileMutexes[location];
        }

        std::lock_guard<std::mutex> lock(*mtx);

        try
        {
            std::ifstream file(location);
            if(!file.is_open())
                throw std::runtime_error("Can't open file: " + location);

            std::stringstream buffer;
            buffer << file.rdbuf();
            file.close();

            json::jobject obj = json::jobject::parse(buffer.str().c_str());
            json::jobject service = (obj[DEVICE_SERVICE_SUBTREE]);
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


}