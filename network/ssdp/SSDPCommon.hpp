#ifndef SSDP_COMMON_HPP_
#define SSDP_COMMON_HPP_

#include <string>

namespace SSDP{

    constexpr const char* MULTICAST_ADDRESS = "239.255.255.250";
    constexpr const int PORT = 1900;

    inline std::string buildNotifyAlive(const std::string& uuid, const std::string& location, const std::string& deviceType, const int& max_age)
    {
        return
            "NOTIFY * HTTP/1.1\r\n"
            "HOST: 239.255.255.250:1900\r\n"
            "CACHE-CONTROL: max-age=" + std::to_string(max_age) + "\r\n"
            "NT: " + deviceType + "\r\n"
            "NTS: ssdp:alive\r\n"
            "USN: uuid:" + uuid + "\r\n"
            "LOCATION: " + location + "\r\n"
            "\r\n";
    }

    inline std::string buildNotifyByebye(const std::string& uuid, const std::string& deviceType)
    {
        return
            "NOTIFY * HTTP/1.1\r\n"
            "HOST: 239.255.255.250:1900\r\n"
            "NT: " + deviceType + "\r\n"
            "NTS: ssdp:byebye\r\n"
            "USN: uuid:" + uuid + "\r\n"
            "\r\n";
    }

    inline std::string buildMSearch()
    {
        return
            "M-SEARCH * HTTP/1.1\r\n"
            "HOST: 239.255.255.250:1900\r\n"
            "MAN: ssdp:discover\r\n"
            "MX: 69\r\n"
            "ST: ssdp:all\r\n"
            "\r\n";
    }

    inline std::string buildResponse(const std::string& uuid, const std::string& location, const std::string& deviceType, const int& max_age)
    {
        return
            "HTTP/1.1 200 OK\r\n"
            "CACHE-CONTROL: max-age=" + std::to_string(max_age) + "\r\n"
            "EXT:\r\n"
            "LOCATION: " + location + "\r\n"
            "ST: " + deviceType + "\r\n"
            "NTS: ssdp:discover\r\n"
            "USN: uuid:" + uuid + "\r\n"
            "\r\n";
    }
}

#endif