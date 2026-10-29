#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="${ROOT_DIR}/build/native"
RESOURCE_DIR="${ROOT_DIR}/src/main/resources/native/linux-x86_64"
WIREHAIR_SOURCE_DIR="${ROOT_DIR}/wirehair-src"

if ! command -v cmake >/dev/null 2>&1; then
    echo "ERROR: 未找到 cmake，请先安装 CMake。" >&2
    exit 1
fi

if ! command -v mvn >/dev/null 2>&1; then
    echo "ERROR: 未找到 mvn，请先安装 Maven。" >&2
    exit 1
fi

if [[ ! -d "${WIREHAIR_SOURCE_DIR}" ]]; then
    echo "ERROR: 未找到 Wirehair 源码目录：${WIREHAIR_SOURCE_DIR}" >&2
    echo "请先按项目配置初始化或克隆 wirehair-src。" >&2
    exit 1
fi

mkdir -p "${BUILD_DIR}" "${RESOURCE_DIR}"

echo "==> 配置 JNI 原生工程"
cmake -S "${ROOT_DIR}/src/main/cpp" \
      -B "${BUILD_DIR}" \
      -DCMAKE_BUILD_TYPE=Release \
      -DWIREHAIR_SOURCE_DIR="${WIREHAIR_SOURCE_DIR}"

echo "==> 编译 wirehairjni"
cmake --build "${BUILD_DIR}" --config Release --parallel

NATIVE_LIB="${BUILD_DIR}/libwirehairjni.so"
if [[ ! -f "${NATIVE_LIB}" ]]; then
    NATIVE_LIB="$(find "${BUILD_DIR}" -type f -name 'libwirehairjni.so' -print -quit)"
fi

if [[ -z "${NATIVE_LIB}" || ! -f "${NATIVE_LIB}" ]]; then
    echo "ERROR: 编译完成后未找到 libwirehairjni.so。" >&2
    echo "请检查 src/main/cpp/CMakeLists.txt 中的 target 和输出目录。" >&2
    exit 1
fi

echo "==> 复制原生库到 JAR 资源目录"
install -m 755 "${NATIVE_LIB}" "${RESOURCE_DIR}/libwirehairjni.so"

echo "==> 打包 Maven 项目"
cd "${ROOT_DIR}"
mvn --batch-mode clean package -DskipTests

echo "==> 构建完成"
echo "JAR: ${ROOT_DIR}/target/wirehair-jni-1.0.0.jar"
echo "SO : ${RESOURCE_DIR}/libwirehairjni.so"
