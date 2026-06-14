# Smart-backyard
Projektni zadatak iz predmeta Bežične mreže - Internet of things

---

## 1. Install Mosquitto library, broker and json library

```
sudo apt update
sudo apt install libmosquitto-dev mosquitto
sudo apt install nlohmann-json3-dev
```

---

## 2. Run broker

```
mosquitto
```

---

## 3. Compile

```
mkdir build && cd build
cmake ..
make
```
all executables will be in Smart-backyard/bin/

## 4. Run

Go back to project root:
```
cd ..
```
Make the script executable:
```
sudo chmod +x run.sh
```
Run the script:
```
./run.sh
```
