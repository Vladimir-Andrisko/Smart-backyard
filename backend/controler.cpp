#include "network/ssdp/SSDPController.hpp"
#include <iostream>

using namespace std;

int main(){
    SSDPController *ssdp = nullptr;

    try{
        ssdp = new SSDPController(true);
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