#ifndef SSDP_HPP_
#define SSDP_HPP_

#include <thread>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <string>
#include <unistd.h>
#include <stdio.h>

namespace SSDP{

    std::string ALIVE = 
        "NOTIFY * HTTP/1.1\r\n"
        "HOST: 239.255.255.250:1900\r\n"
        "CACHE-CONTROL: max-age=1800\r\n"
        "NT: upnp:rootdevice\r\n"
        "NTS: ssdp:alive\r\n"
        "SERVER: Linux/5.15 UPnP/1.1 MyDevice/1.0\r\n"
        "USN: uuid:my-device-123::upnp:rootdevice\r\n"
        "BOOTID.UPNP.ORG: 1\r\n"
        "CONFIGID.UPNP.ORG: 1\r\n"
        "\r\n";

    std::string BYEBYE = 
        "NOTIFY * HTTP/1.1\r\n"
        "HOST: 239.255.255.250:1900\r\n"
        "CACHE-CONTROL: max-age=1800\r\n"
        "NT: upnp:rootdevice\r\n"
        "NTS: ssdp:alive\r\n"
        "SERVER: Linux/5.15 UPnP/1.1 MyDevice/1.0\r\n"
        "USN: uuid:my-device-123::upnp:rootdevice\r\n"
        "BOOTID.UPNP.ORG: 1\r\n"
        "CONFIGID.UPNP.ORG: 1\r\n"
        "\r\n";

    std::string RESPONSE = 
        "M-SEARCH * HTTP/1.1\r\n"
        "HOST: 239.255.255.250:1900\r\n"
        "MAN: ssdp:discover\r\n"
        "MX: 3\r\n"
        "ST: Uslov pretraživanja.\r\n"
        "USER_AGENT Linux/5.15 UPnP/1.1 MyDevice/1.0\r\n"
        "CPFN.UPNP.ORG: Prijateljsko ime kontrolne tačke.\r\n"
        "CPUUID.UPNP.ORG: UUID kontrolne tačke.\r\n";


    std::string DISCOVER = 
        "HTTP/1.1 200 OK\r\n"
        "CACHE-CONTROL: max-age = \r\n"
        "DATE: \r\n"
        "EXT: \r\n"
        "LOCATION: Adresa do detaljnih informacija."
        "SERVER: \r\n"
        "ST: \r\n"
        "USN: \r\n" 
        "BOOTID.UPNP.ORG: \r\b"
        "CONFIGID.UPNP.ORG: \r\n"
        "SEARCHPORT.UPNP.ORG: \r\n"
        "MSEARCH \r\n";

}

class SsdpClient
{
private:
    
public:
    SsdpClient();
    ~SsdpClient();
};

class SsdpController
{
private:

public:
    SsdpController();
    ~SsdpController();
};

#endif