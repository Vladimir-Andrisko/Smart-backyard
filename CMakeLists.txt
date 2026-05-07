cmake_minimum_required(VERSION 3.16)

project(SmartBackyard)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_SOURCE_DIR}/bin)

# ========================
# JSON LIBRARY
# ========================
add_library(json
    json/json.cpp
)

target_include_directories(json PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/json
)

# ========================
# HTTPSERVER LIBRARY
# Header-only library
# ========================
add_library(httpserver INTERFACE)

target_include_directories(httpserver INTERFACE
    ${CMAKE_CURRENT_SOURCE_DIR}/httpserver
)

# ========================
# SSDP LIBRARY
# ========================
add_library(ssdp
    network/ssdp/SSDPController.cpp
    network/ssdp/SSDPDevice.cpp
)

target_include_directories(ssdp PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# ssdp depends on json + httpserver
target_link_libraries(ssdp PUBLIC
    json
    httpserver
)

# ========================
# CONTROLLER EXECUTABLE
# ========================
add_executable(controller
    backend/controler.cpp
)

target_link_libraries(controller
    ssdp
)

# ========================
# SENSOR EXECUTABLE
# ========================
add_executable(sensor
    devices/row_node/src/sensor_node.cpp
)

target_link_libraries(sensor
    ssdp
)