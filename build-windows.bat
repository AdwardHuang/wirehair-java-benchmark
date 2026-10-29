@echo off
setlocal EnableExtensions

set "ROOT_DIR=%~dp0"
set "BUILD_DIR=%ROOT_DIR%build\native"
set "RESOURCE_DIR=%ROOT_DIR%src\main\resources\native\windows-x86_64"
set "WIREHAIR_SOURCE_DIR=%ROOT_DIR%wirehair-src"

where cmake >nul 2>nul
if errorlevel 1 (
    echo ERROR: 未找到 cmake，请先安装 CMake。
    exit /b 1
)

where mvn >nul 2>nul
if errorlevel 1 (
    echo ERROR: 未找到 mvn，请先安装 Maven。
    exit /b 1
)

if not exist "%WIREHAIR_SOURCE_DIR%\" (
    echo ERROR: 未找到 Wirehair 源码目录：
    echo        %WIREHAIR_SOURCE_DIR%
    echo 请先按项目配置初始化或克隆 wirehair-src。
    exit /b 1
)

if not exist "%RESOURCE_DIR%" mkdir "%RESOURCE_DIR%"
if not exist "%BUILD_DIR%" mkdir "%BUILD_DIR%"

echo ==^> 配置 JNI 原生工程
cmake -S "%ROOT_DIR%src\main\cpp" ^
      -B "%BUILD_DIR%" ^
      -G "Visual Studio 17 2022" ^
      -A x64 ^
      -DWIREHAIR_SOURCE_DIR="%WIREHAIR_SOURCE_DIR%"
if errorlevel 1 (
    echo ERROR: CMake 配置失败。
    exit /b 1
)

echo ==^> 编译 wirehairjni
cmake --build "%BUILD_DIR%" --config Release --parallel
if errorlevel 1 (
    echo ERROR: 原生库编译失败。
    exit /b 1
)

set "NATIVE_LIB=%BUILD_DIR%\Release\wirehairjni.dll"
if not exist "%NATIVE_LIB%" (
    set "NATIVE_LIB=%BUILD_DIR%\wirehairjni.dll"
)

if not exist "%NATIVE_LIB%" (
    echo ERROR: 编译完成后未找到 wirehairjni.dll。
    echo 请检查 src\main\cpp\CMakeLists.txt 中的 target 和输出目录。
    exit /b 1
)

echo ==^> 复制原生库到 JAR 资源目录
copy /Y "%NATIVE_LIB%" "%RESOURCE_DIR%\wirehairjni.dll" >nul
if errorlevel 1 (
    echo ERROR: 复制 wirehairjni.dll 失败。
    exit /b 1
)

echo ==^> 打包 Maven 项目
cd /d "%ROOT_DIR%"
call mvn --batch-mode clean package -DskipTests
if errorlevel 1 (
    echo ERROR: Maven 打包失败。
    exit /b 1
)

echo.
echo 构建完成：
echo JAR: %ROOT_DIR%target\wirehair-jni-1.0.0.jar
echo DLL: %RESOURCE_DIR%\wirehairjni.dll
exit /b 0
