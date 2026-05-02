#ifndef SSDP_HPP_
#define SSDP_HPP_

#include <thread>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <string.h>
#include <unistd.h>
#include <stdio.h>

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