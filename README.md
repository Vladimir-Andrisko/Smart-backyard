# Smart-backyard
Projektni zadatak iz predmeta Bežične mreže - Internet of things

---

## 1. Install Mosquitto library and broker

```
sudo apt update
sudo apt install libmosquitto-dev mosquitto
```

---

## 2. Compile

For C:

```
gcc example.c -o example -lmosquitto
```

For C++:

```
g++ example.cpp -o example -lmosquitto
```

---

## 3. Run broker

```
mosquitto
```

---
