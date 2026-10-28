#include <jni.h>

#include <cstdint>
#include <fstream>
#include <new>
#include <sstream>
#include <string>
#include <vector>

#ifdef _WIN32
#ifndef NOMINMAX
#define NOMINMAX
#endif
#include <windows.h>
#include <psapi.h>
#endif

#include "wirehair/wirehair.h"

namespace {

/**
 * 一个 Java 编码器或解码器对应一个原生上下文。
 *
 * 编码器使用 wirehair_encoder_create_ex() 时会借用 input 的内存，
 * 因此必须让 input 在整个编码器生命周期内保持有效。
 */
struct Ctx {
    WirehairCodec codec = nullptr;

    // 保存编码器输入，避免 Java 方法返回后输入内存失效。
    std::vector<uint8_t> input;

    ~Ctx() {
        if (codec != nullptr) {
            wirehair_free(codec);
            codec = nullptr;
        }
    }
};

/** 将 Java long 转换为原生上下文指针。 */
Ctx* fromHandle(jlong handle) {
    return reinterpret_cast<Ctx*>(
            static_cast<uintptr_t>(handle));
}

/** 将原生上下文指针转换为 Java long。 */
jlong toHandle(Ctx* ctx) {
    return static_cast<jlong>(
            reinterpret_cast<uintptr_t>(ctx));
}

/** 向 Java 抛出 RuntimeException。 */
void throwRuntime(JNIEnv* env, const char* message) {
    jclass cls = env->FindClass("java/lang/RuntimeException");

    if (cls != nullptr) {
        env->ThrowNew(cls, message);
    }
}

/** 构造 [当前内存, 峰值内存] 数组，单位为字节。 */
jlongArray makeMemoryStats(
        JNIEnv* env,
        jlong current,
        jlong peak) {

    jlongArray result = env->NewLongArray(2);

    if (result == nullptr) {
        return nullptr;
    }

    const jlong values[2] = {current, peak};

    env->SetLongArrayRegion(result, 0, 2, values);

    return result;
}

#ifndef _WIN32

/**
 * 从 Linux /proc/self/status 读取内存统计。
 * VmRSS 和 VmHWM 的原始单位是 KiB。
 */
uint64_t readProcStatusKb(const char* key) {
    std::ifstream input("/proc/self/status");

    if (!input.is_open()) {
        return 0;
    }

    std::string line;

    while (std::getline(input, line)) {
        size_t keyLength = std::char_traits<char>::length(key);

        if (line.compare(0, keyLength, key) == 0) {
            std::istringstream stream(line.substr(keyLength));

            uint64_t value = 0;

            stream >> value;

            return value;
        }
    }

    return 0;
}

#endif

} // namespace

extern "C" {

/**
 * 初始化 Wirehair。
 * Java 端约定：成功返回 0，失败返回负错误码。
 */
JNIEXPORT jint JNICALL
Java_com_hrz_wirehair_WirehairNative_init(
        JNIEnv*,
        jclass) {

    const WirehairResult result = wirehair_init();

    if (result == Wirehair_Success) {
        return 0;
    }

    return -static_cast<jint>(result);
}

/**
 * 创建编码器。
 *
 * Java byte[] 复制到 C++ 上下文的 input 中。
 * Wirehair 编码器借用 input，因此 Ctx 必须一直存活到 free()。
 */
JNIEXPORT jlong JNICALL
Java_com_hrz_wirehair_WirehairNative_createEncoder(
        JNIEnv* env,
        jclass,
        jbyteArray message,
        jint blockBytes) {

    if (message == nullptr || blockBytes <= 0) {
        throwRuntime(env, "编码器参数无效");
        return 0;
    }

    const jsize length = env->GetArrayLength(message);

    if (length <= 0) {
        throwRuntime(env, "输入消息不能为空");
        return 0;
    }

    Ctx* ctx = nullptr;

    try {
        ctx = new Ctx();

        ctx->input.resize(
                static_cast<size_t>(length));

    } catch (...) {
        delete ctx;

        throwRuntime(env, "分配编码器上下文失败");

        return 0;
    }

    // 将 Java 数组复制到原生上下文。
    env->GetByteArrayRegion(
            message,
            0,
            length,
            reinterpret_cast<jbyte*>(ctx->input.data()));

    if (env->ExceptionCheck()) {
        delete ctx;
        return 0;
    }

    WirehairCodec codec = nullptr;

    const WirehairResult result =
            wirehair_encoder_create_ex(
                    nullptr,
                    ctx->input.data(),
                    static_cast<uint64_t>(length),
                    static_cast<uint32_t>(blockBytes),
                    &codec);

    if (result != Wirehair_Success || codec == nullptr) {
        delete ctx;

        throwRuntime(
                env,
                "Wirehair 创建编码器失败");

        return 0;
    }

    ctx->codec = codec;

    return toHandle(ctx);
}

/**
 * 编码指定 ID 的编码块。
 *
 * 返回：
 * 正数：实际编码长度；
 * 负数：Wirehair 错误码的负值。
 */
JNIEXPORT jint JNICALL
Java_com_hrz_wirehair_WirehairNative_encodeInto(
        JNIEnv* env,
        jclass,
        jlong handle,
        jint blockId,
        jbyteArray output) {

    Ctx* ctx = fromHandle(handle);

    if (ctx == nullptr
            || ctx->codec == nullptr
            || output == nullptr
            || blockId < 0) {

        throwRuntime(
                env,
                "编码器句柄、块 ID 或输出数组无效");

        return -1;
    }

    const jsize capacity =
            env->GetArrayLength(output);

    if (capacity <= 0) {
        throwRuntime(env, "输出缓冲区不能为空");
        return -1;
    }

    // 先采用临时缓冲区实现，便于稳定验证 JNI 行为。
    // 后续可以在性能测试通过后优化为复用缓冲区。
    std::vector<uint8_t> buffer;

    try {
        buffer.resize(
                static_cast<size_t>(capacity));

    } catch (...) {
        throwRuntime(env, "分配编码缓冲区失败");
        return -1;
    }

    uint32_t dataBytes = 0;

    const WirehairResult result =
            wirehair_encode(
                    ctx->codec,
                    static_cast<unsigned>(blockId),
                    buffer.data(),
                    static_cast<uint32_t>(capacity),
                    &dataBytes);

    if (result != Wirehair_Success) {
        return -static_cast<jint>(result);
    }

    if (dataBytes > static_cast<uint32_t>(capacity)) {
        throwRuntime(
                env,
                "Wirehair 返回的编码长度超出缓冲区容量");

        return -1;
    }

    // 将有效编码数据复制回 Java 数组。
    env->SetByteArrayRegion(
            output,
            0,
            static_cast<jsize>(dataBytes),
            reinterpret_cast<const jbyte*>(buffer.data()));

    if (env->ExceptionCheck()) {
        return -1;
    }

    return static_cast<jint>(dataBytes);
}

/** 创建解码器。 */
JNIEXPORT jlong JNICALL
Java_com_hrz_wirehair_WirehairNative_createDecoder(
        JNIEnv* env,
        jclass,
        jlong messageBytes,
        jint blockBytes) {

    if (messageBytes <= 0 || blockBytes <= 0) {
        throwRuntime(env, "解码器参数无效");
        return 0;
    }

    Ctx* ctx = new (std::nothrow) Ctx();

    if (ctx == nullptr) {
        throwRuntime(env, "分配解码器上下文失败");
        return 0;
    }

    WirehairCodec codec = nullptr;

    const WirehairResult result =
            wirehair_decoder_create_ex(
                    nullptr,
                    static_cast<uint64_t>(messageBytes),
                    static_cast<uint32_t>(blockBytes),
                    &codec);

    if (result != Wirehair_Success || codec == nullptr) {
        delete ctx;

        throwRuntime(
                env,
                "Wirehair 创建解码器失败");

        return 0;
    }

    ctx->codec = codec;

    return toHandle(ctx);
}

/**
 * 向解码器提交编码块。
 *
 * Java 端约定：
 * 1：Wirehair_Success，数据已经足够；
 * 0：Wirehair_NeedMore，还需要更多编码块；
 * 负数：其他原生错误码的负值。
 */
JNIEXPORT jint JNICALL
Java_com_hrz_wirehair_WirehairNative_decode(
        JNIEnv* env,
        jclass,
        jlong handle,
        jint blockId,
        jbyteArray packet,
        jint length) {

    Ctx* ctx = fromHandle(handle);

    if (ctx == nullptr
            || ctx->codec == nullptr
            || packet == nullptr
            || blockId < 0) {

        throwRuntime(
                env,
                "解码器句柄、块 ID 或数据包无效");

        return -1;
    }

    const jsize packetLength =
            env->GetArrayLength(packet);

    if (length <= 0 || length > packetLength) {
        throwRuntime(env, "数据包有效长度无效");
        return -1;
    }

    std::vector<uint8_t> buffer;

    try {
        buffer.resize(static_cast<size_t>(length));

    } catch (...) {
        throwRuntime(env, "分配解码缓冲区失败");
        return -1;
    }

    env->GetByteArrayRegion(
            packet,
            0,
            length,
            reinterpret_cast<jbyte*>(buffer.data()));

    if (env->ExceptionCheck()) {
        return -1;
    }

    const WirehairResult result =
            wirehair_decode(
                    ctx->codec,
                    static_cast<unsigned>(blockId),
                    buffer.data(),
                    static_cast<uint32_t>(length));

    if (result == Wirehair_Success) {
        return 1;
    }

    if (result == Wirehair_NeedMore) {
        return 0;
    }

    return -static_cast<jint>(result);
}

/**
 * 恢复完整消息。
 * 成功返回 0，失败返回负错误码。
 */
JNIEXPORT jint JNICALL
Java_com_hrz_wirehair_WirehairNative_recoverInto(
        JNIEnv* env,
        jclass,
        jlong handle,
        jbyteArray output) {

    Ctx* ctx = fromHandle(handle);

    if (ctx == nullptr
            || ctx->codec == nullptr
            || output == nullptr) {

        throwRuntime(
                env,
                "解码器句柄或恢复输出数组无效");

        return -1;
    }

    const jsize outputLength =
            env->GetArrayLength(output);

    if (outputLength <= 0) {
        throwRuntime(env, "恢复输出数组不能为空");
        return -1;
    }

    std::vector<uint8_t> buffer;

    try {
        buffer.resize(
                static_cast<size_t>(outputLength));

    } catch (...) {
        throwRuntime(env, "分配恢复缓冲区失败");
        return -1;
    }

    const WirehairResult result =
            wirehair_recover(
                    ctx->codec,
                    buffer.data(),
                    static_cast<uint64_t>(outputLength));

    if (result != Wirehair_Success) {
        return -static_cast<jint>(result);
    }

    env->SetByteArrayRegion(
            output,
            0,
            outputLength,
            reinterpret_cast<const jbyte*>(buffer.data()));

    if (env->ExceptionCheck()) {
        return -1;
    }

    return 0;
}

/** 释放原生上下文及其内部的 Wirehair 编码器/解码器。 */
JNIEXPORT void JNICALL
Java_com_hrz_wirehair_WirehairNative_free(
        JNIEnv*,
        jclass,
        jlong handle) {

    delete fromHandle(handle);
}

/** 将 Wirehair 错误码转换为文本。 */
JNIEXPORT jstring JNICALL
Java_com_hrz_wirehair_WirehairNative_resultString(
        JNIEnv* env,
        jclass,
        jint result) {

    // Java 端传入的是负错误码，这里转换回 WirehairResult。
    jint code = result < 0 ? -result : result;

    const char* message =
            wirehair_result_string(
                    static_cast<WirehairResult>(code));

    if (message == nullptr) {
        message = "未知 Wirehair 错误";
    }

    return env->NewStringUTF(message);
}

/** 获取进程内存统计，返回 [当前驻留内存, 历史峰值驻留内存]。 */
JNIEXPORT jlongArray JNICALL
Java_com_hrz_wirehair_WirehairNative_memoryStats(
        JNIEnv* env,
        jclass) {

#ifdef _WIN32

    PROCESS_MEMORY_COUNTERS_EX counters{};

    counters.cb = sizeof(counters);

    if (!GetProcessMemoryInfo(
            GetCurrentProcess(),
            reinterpret_cast<PROCESS_MEMORY_COUNTERS*>(&counters),
            sizeof(counters))) {

        return makeMemoryStats(env, 0, 0);
    }

    return makeMemoryStats(
            env,
            static_cast<jlong>(counters.WorkingSetSize),
            static_cast<jlong>(counters.PeakWorkingSetSize));

#elif defined(__linux__)

    // Linux 的 VmRSS 和 VmHWM 单位为 KiB，转换为字节。
    const uint64_t currentKb =
            readProcStatusKb("VmRSS:");

    const uint64_t peakKb =
            readProcStatusKb("VmHWM:");

    return makeMemoryStats(
            env,
            static_cast<jlong>(currentKb * 1024ULL),
            static_cast<jlong>(peakKb * 1024ULL));

#else

    return makeMemoryStats(env, 0, 0);

#endif
}

} // extern "C"