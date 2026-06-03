cmake_minimum_required(VERSION 3.16)

project(SmartBackyard)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_SOURCE_DIR}/bin)

# ========================
# PKG CONFIG + MOSQUITTO
# ========================
find_package(PkgConfig REQUIRED)
pkg_check_modules(MOSQUITTO REQUIRED libmosquitto)

# ========================
# JSON LIBRARY
# ========================
add_library(json json/json.cpp)

target_include_directories(json PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/json
)

# ========================
# HTTP SERVER
# ========================
add_library(http network/httpserver/httpserver.cpp)

target_include_directories(http PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/network/httpserver
)

target_link_libraries(http PUBLIC json)

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

target_link_libraries(ssdp PUBLIC
    json
    http
)

# ========================
# CONTROLLER
# ========================
add_executable(controller backend/controler.cpp)

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
add_executable(sensor devices/src/sensor_node.cpp)

target_include_directories(sensor PRIVATE
    ${MOSQUITTO_INCLUDE_DIRS}
)

target_link_libraries(sensor
    ssdp
    ${MOSQUITTO_LIBRARIES}
)


add_executable(humidity_sensor devices/src/humidity_sensor.cpp)

target_include_directories(humidity_sensor PRIVATE
    json
    ${CMAKE_CURRENT_SOURCE_DIR}/devices/include
    ${MOSQUITTO_INCLUDE_DIRS}
)

target_link_libraries(humidity_sensor
    ssdp
    ${MOSQUITTO_LIBRARIES}
)