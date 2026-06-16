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
Make the scripts executable:
```
sudo chmod +x run.sh
sudo chmod +x destroy.sh
```
Run the script:
```
./run.sh
```
If you have ip address binding problems you can kill all smart_backyard proccesses:
```
./destroy.sh
```

If you get errors with ./run.sh, download gnome terminal:
```
sudo apt install gnome-terminal
```