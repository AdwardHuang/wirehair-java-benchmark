#!/usr/bin/env bash
set -euo pipefail

if [[ ! -f wirehair-src/CMakeLists.txt ]]; then
  git clone --depth 1 https://github.com/catid/wirehair.git wirehair-src
fi
cmake -S . -B build/native -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DWIREHAIR_SOURCE_DIR="$PWD/wirehair-src"
cmake --build build/native --parallel
mvn -B package -DskipTests
