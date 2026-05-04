#include "network/ssdp/SSDPController.hpp"
#include <iostream>

using namespace std;

int main(){
    SSDPController ssdp;

    ssdp.start();
    cout << "Zapoceo ssdp\n";

    while(true){
        getchar();
        break;
    }

    ssdp.stop();
    cout << "Zavrsio ssdp\n";

    return 0;
}