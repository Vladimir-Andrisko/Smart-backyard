#include "network/ssdp/SSDPDevice.hpp"
#include <iostream>

using namespace std;

int main(){
    SSDPDevice *ssdp = nullptr;

    try{
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