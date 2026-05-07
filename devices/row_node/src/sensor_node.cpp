#include "network/ssdp/SSDPDevice.hpp"
#include <iostream>
#include <csignal>

using namespace std;

SSDPDevice* ssdp = nullptr;

void handleSignal(int)
{
    std::cout << "Zaustavlja se SSDP\n";

    if (ssdp)
    {
        delete ssdp;
        ssdp = nullptr;
    }

    std::exit(0);
}

int main(int argc, char* argv[]){

    //std::signal(SIGINT, handleSignal);

    try{
        if (argc >= 2)
            ssdp = new SSDPDevice(argv[1]);
        else
            ssdp = new SSDPDevice("1", "gas", "/negde");
    }catch(const std::exception &e){
        cerr << e.what() << endl;
        return 1;
    }

    ssdp->start();
    cout << "Zapoceo ssdp\n";
    
    getchar();

    ssdp->stop();
    cout << "Zavrsio ssdp\n";
    delete ssdp;
    return 0;
}