cmake_minimum_required(VERSION 3.18)

project(wirehair-jni LANGUAGES C CXX)

# 由 GitHub Actions 指定官方 Wirehair 源码目录。
if(NOT DEFINED WIREHAIR_SOURCE_DIR
        OR NOT EXISTS "${WIREHAIR_SOURCE_DIR}/CMakeLists.txt")
    message(FATAL_ERROR
        "请通过 -DWIREHAIR_SOURCE_DIR=<官方 Wirehair 源码目录> 指定路径")
endif()

# 关闭官方项目的测试和开发工具，减少构建时间。
set(BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(BUILD_CODEC_V2 OFF CACHE BOOL "" FORCE)
set(WIREHAIR_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(WIREHAIR_BUILD_BENCHMARKS OFF CACHE BOOL "" FORCE)
set(WIREHAIR_ENABLE_SCHEDULED_TESTS OFF CACHE BOOL "" FORCE)

# 将官方 Wirehair 作为子项目编译。
add_subdirectory(
    "${WIREHAIR_SOURCE_DIR}"
    "${CMAKE_BINARY_DIR}/wirehair-core"
)

# 查找当前 JDK 对应的 JNI 头文件。
find_package(JNI REQUIRED)

# 编译 JNI 动态库。
add_library(wirehairjni SHARED wirehair_jni.cpp)

target_include_directories(wirehairjni PRIVATE
    ${JNI_INCLUDE_DIRS}
)

# 官方 CMake 定义了 wirehair 目标。
target_link_libraries(wirehairjni PRIVATE
    wirehair
    ${JNI_LIBRARIES}
)

target_compile_features(wirehairjni PRIVATE cxx_std_17)

# 最终生成 wirehairjni.dll 或 libwirehairjni.so。
set_target_properties(wirehairjni PROPERTIES
    OUTPUT_NAME "wirehairjni"
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
)

if(WIN32)
    target_link_libraries(wirehairjni PRIVATE Psapi)
endif()