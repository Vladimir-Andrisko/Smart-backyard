gnome-terminal -- ./bin/controller &
sleep 0.1
gnome-terminal -- ./bin/temperature_sensor config/sensor/temperature_sensor_desc.json &
sleep 0.1 
gnome-terminal -- ./bin/light_sensor config/sensor/light_sensor_desc.json &
sleep 0.1
gnome-terminal -- ./bin/humidity_sensor config/sensor/humidity_sensor_desc.json &