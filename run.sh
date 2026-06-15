#!/bin/bash
SLEEP=0.2
TAG="smart_backyard"

gnome-terminal -- ./bin/controller &
sleep $SLEEP
gnome-terminal -- ./bin/temperature_sensor config/sensor/temperature_sensor_desc.json $TAG &
sleep $SLEEP
gnome-terminal -- ./bin/light_sensor config/sensor/light_sensor_desc.json $TAG &
sleep $SLEEP
gnome-terminal -- ./bin/humidity_sensor config/sensor/humidity_sensor_desc.json $TAG &
sleep $SLEEP
gnome-terminal -- ./bin/row_sensor config/sensor/row_sensor/row1_sensor_desc.json $TAG &
sleep $SLEEP
gnome-terminal -- ./bin/row_sensor config/sensor/row_sensor/row2_sensor_desc.json $TAG &
sleep $SLEEP
gnome-terminal -- ./bin/row_sensor config/sensor/row_sensor/row3_sensor_desc.json $TAG &
sleep $SLEEP
gnome-terminal -- ./bin/row_sensor config/sensor/row_sensor/row4_sensor_desc.json $TAG &
sleep $SLEEP
gnome-terminal -- ./bin/row_sensor config/sensor/row_sensor/row5_sensor_desc.json $TAG &
sleep $SLEEP
gnome-terminal -- ./bin/row_sensor config/sensor/row_sensor/row6_sensor_desc.json $TAG &
sleep $SLEEP
gnome-terminal -- ./bin/row_sensor config/sensor/row_sensor/row7_sensor_desc.json $TAG &
sleep $SLEEP
gnome-terminal -- ./bin/row_sensor config/sensor/row_sensor/row8_sensor_desc.json $TAG &
sleep $SLEEP
gnome-terminal -- ./bin/row_sensor config/sensor/row_sensor/row9_sensor_desc.json $TAG &
sleep $SLEEP
gnome-terminal -- ./bin/row_sensor config/sensor/row_sensor/row10_sensor_desc.json $TAG &
sleep $SLEEP
gnome-terminal -- ./bin/roof_actuator config/actuator/roof_actuator_desc.json $TAG &
sleep $SLEEP
gnome-terminal -- ./bin/row_actuator config/actuator/row_actuator/row1_actuator_desc.json $TAG &
sleep $SLEEP
gnome-terminal -- ./bin/row_actuator config/actuator/row_actuator/row2_actuator_desc.json $TAG &
sleep $SLEEP
gnome-terminal -- ./bin/row_actuator config/actuator/row_actuator/row3_actuator_desc.json $TAG &
sleep $SLEEP
gnome-terminal -- ./bin/row_actuator config/actuator/row_actuator/row4_actuator_desc.json $TAG &
sleep $SLEEP
gnome-terminal -- ./bin/row_actuator config/actuator/row_actuator/row5_actuator_desc.json $TAG &
sleep $SLEEP
gnome-terminal -- ./bin/row_actuator config/actuator/row_actuator/row6_actuator_desc.json $TAG &
sleep $SLEEP
gnome-terminal -- ./bin/row_actuator config/actuator/row_actuator/row7_actuator_desc.json $TAG &
sleep $SLEEP
gnome-terminal -- ./bin/row_actuator config/actuator/row_actuator/row8_actuator_desc.json $TAG &
sleep $SLEEP
gnome-terminal -- ./bin/row_actuator config/actuator/row_actuator/row9_actuator_desc.json $TAG &
sleep $SLEEP
gnome-terminal -- ./bin/row_actuator config/actuator/row_actuator/row10_actuator_desc.json $TAG &
