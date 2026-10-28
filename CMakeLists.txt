cmake_minimum_required(VERSION 3.20)
project(wirehair-java-benchmark LANGUAGES CXX)

if(NOT WIREHAIR_SOURCE_DIR)
    message(FATAL_ERROR "WIREHAIR_SOURCE_DIR is required")
endif()

set(BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(BUILD_CODEC_V2 OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WIREHAIR_BUILD_BOTH OFF CACHE BOOL "" FORCE)
set(WIREHAIR_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(WIREHAIR_BUILD_BENCHMARKS OFF CACHE BOOL "" FORCE)

add_subdirectory("${WIREHAIR_SOURCE_DIR}" "${CMAKE_BINARY_DIR}/wirehair")

find_package(JNI REQUIRED)

add_library(wirehairjni SHARED
    src/main/cpp/wirehair_jni.cpp
)

target_include_directories(wirehairjni PRIVATE
    "${WIREHAIR_SOURCE_DIR}/include"
    ${JNI_INCLUDE_DIRS}
)

target_link_libraries(wirehairjni PRIVATE wirehair)

set_target_properties(wirehairjni PROPERTIES
    OUTPUT_NAME "wirehairjni"
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
)
