#include "network/ssdp/SSDPDevice.hpp"
#include <iostream>

using namespace std;

int main(int argc, char* argv[]){
    SSDPDevice *ssdp = nullptr;

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