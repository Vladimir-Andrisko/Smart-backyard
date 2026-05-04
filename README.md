# Smart-backyard
Projektni zadatak iz predmeta Bežične mreže - Internet of things

---

## 1. Install Mosquitto library and broker

```
sudo apt update
sudo apt install libmosquitto-dev mosquitto
```

---

## 2. Run broker

```
mosquitto
```

---

## 3. Test Compile

NOTE: This is a test compile, in the future a cmake will handle this. There is no need to run mqtt broker mosquitto.

Controler:

```
g++ backend/controler.cpp network/ssdp/SSDPController.cpp -I. -o controler -pthread
```

Sensor:

```
g++ devices/row_node/src/sensor_node.cpp network/ssdp/SSDPController.cpp -I. -o sensor -pthread
```

---
