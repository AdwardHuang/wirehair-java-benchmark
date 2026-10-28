# Wirehair JNI Benchmark v3

这是一个**独立的 Wirehair + Java JNI 内存/性能 Benchmark**。

当前版本专门针对：

- Windows
- GitHub Actions
- Visual Studio 2022 / MSVC
- JDK 17
- 官方 Wirehair
- 26 MiB synthetic data
- block size = 2500 bytes
- 约 10% systematic block 丢失
- repair block 连续发送
- Windows Working Set / Peak Working Set 采样
- SHA-256 + byte-for-byte 验证

## 1. 本地不需要安装 Visual Studio

推荐直接上传整个项目到 GitHub。

Workflow：

`.github/workflows/build-windows.yml`

使用：

```text
runs-on: windows-2022
```

GitHub-hosted Windows Runner 自带 Visual Studio / MSVC / CMake。

## 2. GitHub Actions 运行

进入：

```text
GitHub 仓库
→ Actions
→ Wirehair JNI Benchmark
→ Run workflow
```

可以修改：

```text
message_mb
block_bytes
repair_limit
loss_every
```

默认：

```text
message_mb   = 26
block_bytes  = 2500
repair_limit = 2000
loss_every   = 10
```

## 3. Benchmark 输出

重点观察：

```text
MEM start
MEM after input + reusable buffers
MEM after encoder create
MEM after decoder create
MEM after systematic
MEM after repair 1
MEM after repair 100
MEM after repair 200
...
MEM after repair phase
MEM after recover
MEM after free + GC
```

例如重点比较：

```text
after input + reusable buffers
        ↓
after encoder create
```

这部分可以观察 Wirehair Encoder 初始化是否产生明显 native memory 增长。

然后比较：

```text
after encoder create
        ↓
after first repair
```

以及：

```text
repair 100
repair 200
...
```

观察 Repair 过程是否继续扩大 Working Set。

## 4. 参数格式

命令行：

```text
Benchmark <messageMB> <blockBytes> <repairLimit> <lossEvery>
```

例如：

```text
Benchmark 26 2500 2000 10
```

或者：

```text
Benchmark 50 2500 3000 10
```

## 5. 当前设计的重要说明

为了尽量减少 Java GC 对实验结果的干扰：

- 输入数据只创建一个 `byte[]`
- QR/packet buffer 只创建一个可复用 `byte[]`
- recovered 只创建一个 `byte[]`
- Wirehair Encoder 的 native context 自己保存一份 input
- Encoder / Decoder 生命周期在 benchmark 中明确控制

当前**没有调用**：

```cpp
wirehair_encoder_detach_input()
```

原因是第一轮测试需要观察 Wirehair 原始 Encoder 的完整内存占用。

后续如果第一轮结果正常，再增加 detach-input 对照实验。

## 6. 重要

这个版本的目标是：

> 先回答 Wirehair 在 26 MiB / 2500-byte block 场景下到底需要多少内存。

暂时：

- 不接 QOcr
- 不接 libcimbar
- 不接二维码
- 不接 ZXing
- 不做 GUI
- 不做文件读取
- 不做网络传输

先把 Fountain Code 本身的内存行为测清楚。

## 7. Benchmark 结果如何判断

重点不是只看最终：

```text
MEM after free + GC
```

因为 Windows CRT / JVM / allocator 可能不会立即把已经释放的内存还给操作系统。

重点看：

```text
Peak Working Set
```

以及不同阶段的：

```text
Current Working Set
```

尤其是：

```text
after input
after encoder create
after decoder create
after systematic
after first repair
after repair phase
after recover
```

如果 Wirehair 在这个场景下只有几百 MB，而 OpenRQ 达到约 1.3 GB，那么就非常有价值：

> 可以基本确认 QOcr 当前的大内存峰值并不是 Fountain Code 本身必然如此，而更可能与 OpenRQ 的具体实现/矩阵数据结构有关。

如果 Wirehair 同样出现非常高的峰值，再进一步分析 Wirehair 的 recovery matrix / block 数量 / block size 参数。

## 8. 注意

当前 JNI 是为了 Benchmark 编写的最小实现，不是生产级 Java Wirehair Binding。

Benchmark 第一阶段优先保证：

1. 编译简单
2. native 内存可观察
3. 输入可重复
4. 参数可调整
5. 恢复结果可验证
