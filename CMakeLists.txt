cmake_minimum_required(VERSION 3.16)

project(SmartBackyard)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_SOURCE_DIR}/bin)

find_package(PkgConfig REQUIRED)
pkg_check_modules(MOSQUITTO REQUIRED libmosquitto)

# ========================
# SSDP
# ========================
add_library(ssdp
    network/ssdp/SSDPController.cpp
    network/ssdp/SSDPDevice.cpp
)
target_include_directories(ssdp PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# ========================
# CONTROLLER
# ========================
add_executable(controller backend/controller.cpp)
target_include_directories(controller PRIVATE
    ${MOSQUITTO_INCLUDE_DIRS}
)
target_link_libraries(controller
    ssdp
    ${MOSQUITTO_LIBRARIES}
)

# ========================
# SENSOR
# ========================
add_executable(humidity_sensor devices/src/humidity_sensor.cpp)
target_include_directories(humidity_sensor PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/devices/include
    ${MOSQUITTO_INCLUDE_DIRS}
)
target_link_libraries(humidity_sensor
    ssdp
    ${MOSQUITTO_LIBRARIES}
)


add_executable(temperature_sensor devices/src/temperature_sensor.cpp)
target_include_directories(temperature_sensor PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/devices/include
    ${MOSQUITTO_INCLUDE_DIRS}
)
target_link_libraries(temperature_sensor
    ssdp
    ${MOSQUITTO_LIBRARIES}
)


add_executable(light_sensor devices/src/light_sensor.cpp)
target_include_directories(light_sensor PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/devices/include
    ${MOSQUITTO_INCLUDE_DIRS}
)
target_link_libraries(light_sensor
    ssdp
    ${MOSQUITTO_LIBRARIES}
)


add_executable(row_sensor devices/src/row_sensor.cpp)
target_include_directories(row_sensor PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/devices/include
    ${MOSQUITTO_INCLUDE_DIRS}
)
target_link_libraries(row_sensor
    ssdp
    ${MOSQUITTO_LIBRARIES}
)

# ==========================
#       ACTUATOR
# ==========================

add_executable(roof_actuator devices/src/roof_actuator.cpp)
target_include_directories(roof_actuator PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/devices/include
    ${MOSQUITTO_INCLUDE_DIRS}
)
target_link_libraries(roof_actuator
    ssdp
    ${MOSQUITTO_LIBRARIES}
)

add_executable(row_actuator devices/src/row_actuator.cpp)
target_include_directories(row_actuator PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/devices/include
    ${MOSQUITTO_INCLUDE_DIRS}
)
target_link_libraries(row_actuator
    ssdp
    ${MOSQUITTO_LIBRARIES}
)