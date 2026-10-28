@echo off
setlocal
if not exist "wirehair-src\CMakeLists.txt" (
    echo Cloning official Wirehair...
    git clone --depth 1 https://github.com/catid/wirehair.git wirehair-src
    if errorlevel 1 exit /b 1
)
cmake -S . -B build\native -G "Visual Studio 17 2022" -A x64 -DWIREHAIR_SOURCE_DIR="%CD%\wirehair-src"
if errorlevel 1 exit /b 1
cmake --build build\native --config Release --parallel
if errorlevel 1 exit /b 1
mvn -B package -DskipTests
if errorlevel 1 exit /b 1
echo BUILD OK
endlocal
