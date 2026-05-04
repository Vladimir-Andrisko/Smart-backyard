#include "network/ssdp/SSDPDevice.hpp"
#include <iostream>

using namespace std;

int main(){
    SSDPDevice ssdp("1", "gas", "/negde");

    ssdp.start();
    cout << "Zapoceo ssdp\n";
    while(true){
        getchar();
        break;
    }

    cout << "Zavrsio ssdp\n";
    ssdp.stop();
    return 0;
}