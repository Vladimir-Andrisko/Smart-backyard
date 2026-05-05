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

NOTE: This is a test compile, there is no need to run mqtt broker mosquitto.


```
mkdir build && cd build
cmake ..
make
---

all executables will be in Smart-backyard/bin/
