# Wirehair JNI

面向 Java 项目的 Wirehair Fountain Code JNI 封装。本仓库已从单纯的 Benchmark 示例扩展为可集成的 Java/JNI 库；`Benchmark` 保留用于本地自检和基本性能测试。

## 功能

- 通过 JNI 调用 Wirehair 原生编码/解码实现。
- Java API 位于 `com.hrz.wirehair` 包。
- 支持 Windows x86-64 与 Linux x86-64 原生库。
- 原生库可打包在 JAR 中自动加载，也可以通过 JVM 参数指定外部动态库路径。
- `WirehairEncoder` 和 `WirehairDecoder` 实现 `AutoCloseable`，建议使用 `try-with-resources` 释放原生资源。

## 目录结构

```text
.
├── .github/workflows/package-jar.yml
├── src/main/cpp/                 # JNI/CMake 原生代码
├── src/main/java/com/hrz/wirehair/
├── src/main/java/com/hrz/wirehairbench/Benchmark.java
├── src/main/resources/native/    # 构建时放入平台原生库
├── wirehair-src/                 # Wirehair 源码目录
├── build-linux.sh
└── build-windows.bat
```

## 构建要求

- JDK 17 或兼容的较新 JDK，用于构建 Java JAR。
- Maven、CMake、C/C++ 编译工具链。
- Wirehair 源码位于 `wirehair-src/`；如果该目录不是仓库自带内容，请按项目原有方式克隆或初始化。

### Linux x86-64

安装 JDK、Maven、CMake 和 C/C++ 编译工具链，然后执行：

```bash
chmod +x build-linux.sh
./build-linux.sh
```

脚本会配置并编译 `src/main/cpp` 下的 JNI 工程，将 `libwirehairjni.so` 放入 JAR 资源目录，再运行 Maven 打包。

### Windows x86-64

在安装了 JDK 17、Maven、CMake 和 Visual Studio C++ Build Tools 的终端中执行：

```bat
build-windows.bat
```

脚本默认使用 Visual Studio 2022 生成器。若本机安装的是其他版本，请编辑脚本中的 `-G "Visual Studio 17 2022"`。

## Maven 产物

成功打包后，主要产物为：

- `target/wirehair-jni-1.0.0.jar`：主 JAR，包含 Java 类和构建时放入的原生库。
- `target/wirehair-jni-1.0.0-sources.jar`：Java 源码包。
- `target/wirehair-jni-1.0.0-javadoc.jar`：Javadoc 包。

具体版本号以 `pom.xml` 为准。

## Java API 简介

核心入口是 `com.hrz.wirehair.Wirehair`：

- `Wirehair.load()`：加载原生库。
- `Wirehair.encoder(byte[] message, int blockBytes)`：创建编码器。
- `Wirehair.decoder(long messageBytes, int blockBytes)`：创建解码器。
- `Wirehair.memoryStats()`：读取原生内存统计信息，具体返回内容以当前源码为准。

编码端生成的数据包需要连同对应的 `blockId` 传输；接收端将 `blockId`、数据包字节和有效长度提交给解码器。发送端与接收端必须使用一致的消息长度、`blockBytes` 和数据包编号语义。解码器返回 `NEED_MORE` 表示仍需更多数据，返回 `READY` 表示可以恢复原始消息。

示意用法（具体方法签名请以当前 Java 源码为准）：

```java
Wirehair.load();

try (WirehairEncoder encoder = Wirehair.encoder(message, blockBytes)) {
    // 按 blockId 生成数据包，并发送 blockId 与数据包内容。
}

try (WirehairDecoder decoder = Wirehair.decoder(message.length, blockBytes)) {
    // 持续提交收到的数据包；状态变为 READY 后恢复原始消息。
}
```

## 在其他 Maven 项目中集成

将构建得到的 JAR 安装到本地 Maven 仓库：

```bash
mvn install:install-file \
  -Dfile=target/wirehair-jni-1.0.0.jar \
  -DgroupId=com.hrz \
  -DartifactId=wirehair-jni \
  -Dversion=1.0.0 \
  -Dpackaging=jar
```

然后在目标项目的 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>com.hrz</groupId>
    <artifactId>wirehair-jni</artifactId>
    <version>1.0.0</version>
</dependency>
```

目标项目使用 JDK 17 时，可将其作为普通 Maven JAR 引入。运行平台必须有与操作系统和 CPU 架构匹配的原生库。

## 原生库加载

默认情况下，加载器会从 JAR 资源目录中提取并加载原生库：

- Windows x86-64：`native/windows-x86_64/wirehairjni.dll`
- Linux x86-64：`native/linux-x86_64/libwirehairjni.so`

如需显式指定外部动态库，可使用：

```bash
java -Dwirehairjni.path=/absolute/path/to/libwirehairjni.so ...
```

Windows 示例：

```bat
java -Dwirehairjni.path=C:\path\to\wirehairjni.dll ...
```

指定路径时必须使用与当前操作系统和 CPU 架构匹配的文件。Linux 动态库还需要满足目标系统的 glibc、`GLIBCXX` 和 `CXXABI` 运行时要求。

## GitHub Actions 与 glibc 兼容性

Linux 二进制若要兼容 glibc 2.31，建议在 Ubuntu 20.04 等具有相应旧版 glibc 的构建环境中编译，并检查产物引用的符号版本：

```bash
readelf --version-info libwirehairjni.so | grep -oE 'GLIBC_[0-9.]+' | sort -Vu
```

仅检查 `GLIBC_*` 还不够；目标系统较旧时，也应检查 `GLIBCXX_*` 与 `CXXABI_*`。CI 通过符号版本检查不等于已在所有目标 Linux 发行版上完成运行验证。

## Benchmark

`com.hrz.wirehairbench.Benchmark` 是仓库附带的自检/性能测试入口，不是 JNI 库的唯一用途。构建完成后可执行：

```bash
java -jar target/wirehair-jni-1.0.0.jar 1048576 2500 10
```

参数格式以当前 `Benchmark.java` 实现为准。

## 使用注意事项

- 编码器、解码器使用完后应关闭，推荐 `try-with-resources`。
- 接收端应保留每个数据包对应的 `blockId`，不能仅按到达顺序随意重新编号。
- 恢复消息后，应验证业务层的长度、摘要或其他完整性信息。
- 不要将一个平台的原生库放到另一个平台的资源目录中。
