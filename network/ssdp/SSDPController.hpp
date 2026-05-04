#ifndef SSDP_CONTROLLER_HPP_
#define SSDP_CONTROLLER_HPP_

#include <thread>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <string>
#include <unistd.h>
#include <stdio.h>

class SsdpController
{
private:

public:
    SsdpController();
    ~SsdpController();
};

#endif