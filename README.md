# Wirehair JNI Benchmark v4 (Windows + Linux)

这是一个独立的 Wirehair + Java JNI 内存/性能 Benchmark，支持 Windows x64 和 Linux x86-64。Java 目标版本为 JDK 17；Wirehair 源码在构建时从官方仓库 `https://github.com/catid/wirehair` 获取。

## 项目结构

- `src/main/java/com/hrz/wirehairbench/Benchmark.java`：Java 基准测试、丢包模拟、SHA-256 与逐字节验证。
- `src/main/java/com/hrz/wirehairbench/WirehairNative.java`：统一 JNI API 和动态库加载。
- `src/main/cpp/wirehair_jni.cpp`：跨平台 JNI 实现。
- `CMakeLists.txt`：统一 CMake 构建；Windows 定义 `NOMINMAX` 并链接 Psapi，Linux 从 `/proc/self/status` 读取 RSS / HWM。
- `.github/workflows/build-windows.yml`：Windows GitHub Actions。
- `.github/workflows/build-linux.yml`：Linux GitHub Actions。

## GitHub Actions

进入仓库的 **Actions**，选择 `Wirehair JNI Benchmark - Windows` 或 `Wirehair JNI Benchmark - Linux`，点击 **Run workflow**。可配置：

- `message_mb`：输入数据大小 MiB，默认 26。
- `block_bytes`：Wirehair block 大小，默认 2500。
- `repair_limit`：repair 包数量，默认 2000。
- `loss_every`：每 N 个 systematic block 丢弃一个；0 表示不丢包，默认 10。

push 和 pull request 也会触发默认参数构建及运行。

## Windows 本地构建

需要 JDK 17、Maven、Git、CMake、Visual Studio 2022 C++ Build Tools：

```bat
build-windows.bat
run-windows.bat
```

## Linux 本地构建

需要 JDK 17、Maven、Git、CMake、Ninja、C++ 编译器。Ubuntu/Debian 可安装：

```bash
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk maven git cmake ninja-build build-essential
./build-linux.sh
java -Dwirehairjni.path="$PWD/build/native/libwirehairjni.so" \
  -cp target/wirehair-java-benchmark-1.0.0.jar \
  com.hrz.wirehairbench.Benchmark 26 2500 2000 10
```

## 输出说明

程序打印各阶段的当前内存和峰值内存，以及编码/恢复时间、输入和恢复数据的 SHA-256，最后进行逐字节比较。Windows 内存值使用 Working Set / Peak Working Set；Linux 使用 `/proc/self/status` 中的 `VmRSS` / `VmHWM`。两者都是进程驻留内存的近似观测值，操作系统的统计语义不完全相同，跨平台比较时应关注趋势，不应把微小差异视为严格等价。

## 注意事项

这是用于研究 Wirehair 内存行为的最小 JNI Benchmark，不是生产级 Java Wirehair Binding；目前只针对常见 Windows x64 与 Linux x86-64 工作流。原生库需要与 JVM 的操作系统和 CPU 架构一致。由于运行环境无法访问 GitHub 并执行原生构建，本次打包未能实际运行 Windows/Linux CI 验证；首次运行工作流后应依据实际 Wirehair 版本的 API/编译日志继续校准。
