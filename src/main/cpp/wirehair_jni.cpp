#include <jni.h>

#include <algorithm>
#include <cstdint>
#include <cstdlib>
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

struct Ctx {
    WirehairCodec codec = nullptr;
    std::vector<uint8_t> input;

    ~Ctx() {
        if (codec != nullptr) {
            wirehair_free(codec);
            codec = nullptr;
        }
    }
};

Ctx* fromHandle(jlong handle) {
    return reinterpret_cast<Ctx*>(static_cast<uintptr_t>(handle));
}

jlong toHandle(Ctx* ctx) {
    return static_cast<jlong>(reinterpret_cast<uintptr_t>(ctx));
}

void throwRuntime(JNIEnv* env, const char* message) {
    jclass cls = env->FindClass("java/lang/RuntimeException");
    if (cls != nullptr) {
        env->ThrowNew(cls, message);
    }
}

jlongArray makeMemoryStats(JNIEnv* env, jlong current, jlong peak) {
    jlongArray result = env->NewLongArray(2);
    if (result == nullptr) {
        return nullptr;
    }
    const jlong values[2] = {current, peak};
    env->SetLongArrayRegion(result, 0, 2, values);
    return result;
}

#ifndef _WIN32
uint64_t readProcStatusKb(const char* key) {
    std::ifstream input("/proc/self/status");
    if (!input.is_open()) {
        return 0;
    }

    std::string line;
    while (std::getline(input, line)) {
        if (line.compare(0, std::char_traits<char>::length(key), key) == 0) {
            std::istringstream stream(line.substr(std::char_traits<char>::length(key)));
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

JNIEXPORT jint JNICALL
Java_com_hrz_wirehairbench_WirehairNative_init(JNIEnv*, jclass) {
    const WirehairResult result = wirehair_init();
    return result == Wirehair_Success ? 0 : -static_cast<jint>(result);
}

JNIEXPORT jlong JNICALL
Java_com_hrz_wirehairbench_WirehairNative_createEncoder(
        JNIEnv* env, jclass, jbyteArray message, jint blockBytes) {
    if (message == nullptr || blockBytes <= 0) {
        throwRuntime(env, "Invalid encoder arguments.");
        return 0;
    }

    const jsize length = env->GetArrayLength(message);
    if (length <= 0) {
        throwRuntime(env, "Input message is empty.");
        return 0;
    }

    Ctx* ctx = nullptr;
    try {
        ctx = new Ctx();
        ctx->input.resize(static_cast<size_t>(length));
    } catch (...) {
        delete ctx;
        throwRuntime(env, "Unable to allocate encoder context.");
        return 0;
    }

    env->GetByteArrayRegion(message, 0, length,
            reinterpret_cast<jbyte*>(ctx->input.data()));
    if (env->ExceptionCheck()) {
        delete ctx;
        return 0;
    }

    WirehairCodec codec = nullptr;
    const WirehairResult result = wirehair_encoder_create_ex(
            nullptr,
            ctx->input.data(),
            static_cast<uint64_t>(length),
            static_cast<uint32_t>(blockBytes),
            &codec);

    if (result != Wirehair_Success) {
        delete ctx;
        return 0;
    }

    ctx->codec = codec;
    return toHandle(ctx);
}

JNIEXPORT jint JNICALL
Java_com_hrz_wirehairbench_WirehairNative_encodeInto(
        JNIEnv* env, jclass, jlong handle, jint blockId, jbyteArray output) {
    Ctx* ctx = fromHandle(handle);
    if (ctx == nullptr || ctx->codec == nullptr || output == nullptr || blockId < 0) {
        throwRuntime(env, "Invalid encoder handle, block ID, or output.");
        return -1;
    }

    const jsize capacity = env->GetArrayLength(output);
    if (capacity <= 0) {
        throwRuntime(env, "Output buffer is empty.");
        return -1;
    }

    std::vector<uint8_t> buffer;
    try {
        buffer.resize(static_cast<size_t>(capacity));
    } catch (...) {
        throwRuntime(env, "Unable to allocate encoding buffer.");
        return -1;
    }

    uint32_t dataBytes = 0;
    const WirehairResult result = wirehair_encode(
            ctx->codec,
            static_cast<uint32_t>(blockId),
            buffer.data(),
            static_cast<uint32_t>(capacity),
            &dataBytes);

    if (result != Wirehair_Success) {
        return -static_cast<jint>(result);
    }
    if (dataBytes > static_cast<uint32_t>(capacity)) {
        throwRuntime(env, "Wirehair returned an invalid encoded length.");
        return -1;
    }

    env->SetByteArrayRegion(output, 0, static_cast<jsize>(dataBytes),
            reinterpret_cast<const jbyte*>(buffer.data()));
    if (env->ExceptionCheck()) {
        return -1;
    }
    return static_cast<jint>(dataBytes);
}

JNIEXPORT jlong JNICALL
Java_com_hrz_wirehairbench_WirehairNative_createDecoder(
        JNIEnv* env, jclass, jlong messageBytes, jint blockBytes) {
    if (messageBytes <= 0 || blockBytes <= 0) {
        throwRuntime(env, "Invalid decoder arguments.");
        return 0;
    }

    Ctx* ctx = new (std::nothrow) Ctx();
    if (ctx == nullptr) {
        throwRuntime(env, "Unable to allocate decoder context.");
        return 0;
    }

    WirehairCodec codec = nullptr;
    const WirehairResult result = wirehair_decoder_create_ex(
            nullptr,
            static_cast<uint64_t>(messageBytes),
            static_cast<uint32_t>(blockBytes),
            &codec);

    if (result != Wirehair_Success) {
        delete ctx;
        return 0;
    }

    ctx->codec = codec;
    return toHandle(ctx);
}

JNIEXPORT jint JNICALL
Java_com_hrz_wirehairbench_WirehairNative_decode(
        JNIEnv* env, jclass, jlong handle, jint blockId,
        jbyteArray packet, jint length) {
    Ctx* ctx = fromHandle(handle);
    if (ctx == nullptr || ctx->codec == nullptr || packet == nullptr || blockId < 0) {
        throwRuntime(env, "Invalid decoder handle, block ID, or packet.");
        return -1;
    }

    const jsize packetLength = env->GetArrayLength(packet);
    if (length <= 0 || length > packetLength) {
        throwRuntime(env, "Invalid packet length.");
        return -1;
    }

    std::vector<uint8_t> buffer;
    try {
        buffer.resize(static_cast<size_t>(length));
    } catch (...) {
        throwRuntime(env, "Unable to allocate decoding buffer.");
        return -1;
    }
    env->GetByteArrayRegion(packet, 0, length,
            reinterpret_cast<jbyte*>(buffer.data()));
    if (env->ExceptionCheck()) {
        return -1;
    }

    const WirehairResult result = wirehair_decode(
            ctx->codec,
            static_cast<uint32_t>(blockId),
            buffer.data(),
            static_cast<uint32_t>(length));

    if (result == Wirehair_Success) {
        return 1; // Decoder has enough data to recover the full message.
    }
    if (result == Wirehair_NeedMore) {
        return 0;
    }
    return -static_cast<jint>(result);
}

JNIEXPORT jint JNICALL
Java_com_hrz_wirehairbench_WirehairNative_recoverInto(
        JNIEnv* env, jclass, jlong handle, jbyteArray output) {
    Ctx* ctx = fromHandle(handle);
    if (ctx == nullptr || ctx->codec == nullptr || output == nullptr) {
        throwRuntime(env, "Invalid decoder handle or output.");
        return -1;
    }

    const jsize outputLength = env->GetArrayLength(output);
    if (outputLength <= 0) {
        throwRuntime(env, "Recovery output is empty.");
        return -1;
    }

    std::vector<uint8_t> buffer;
    try {
        buffer.resize(static_cast<size_t>(outputLength));
    } catch (...) {
        throwRuntime(env, "Unable to allocate recovery buffer.");
        return -1;
    }

    const WirehairResult result = wirehair_recover(
            ctx->codec,
            buffer.data(),
            static_cast<uint64_t>(outputLength));
    if (result != Wirehair_Success) {
        return -static_cast<jint>(result);
    }

    env->SetByteArrayRegion(output, 0, outputLength,
            reinterpret_cast<const jbyte*>(buffer.data()));
    return env->ExceptionCheck() ? -1 : 0;
}

JNIEXPORT void JNICALL
Java_com_hrz_wirehairbench_WirehairNative_free(JNIEnv*, jclass, jlong handle) {
    delete fromHandle(handle);
}

JNIEXPORT jstring JNICALL
Java_com_hrz_wirehairbench_WirehairNative_resultString(
        JNIEnv* env, jclass, jint result) {
    const jint code = result < 0 ? -result : result;
    const char* text = wirehair_result_string(static_cast<WirehairResult>(code));
    return env->NewStringUTF(text == nullptr ? "unknown" : text);
}

JNIEXPORT jlongArray JNICALL
Java_com_hrz_wirehairbench_WirehairNative_memoryStats(JNIEnv* env, jclass) {
#ifdef _WIN32
    PROCESS_MEMORY_COUNTERS_EX pmc{};
    pmc.cb = sizeof(pmc);
    if (!GetProcessMemoryInfo(GetCurrentProcess(),
            reinterpret_cast<PROCESS_MEMORY_COUNTERS*>(&pmc), sizeof(pmc))) {
        return makeMemoryStats(env, 0, 0);
    }
    return makeMemoryStats(env,
            static_cast<jlong>(pmc.WorkingSetSize),
            static_cast<jlong>(pmc.PeakWorkingSetSize));
#elif defined(__linux__)
    // /proc/self/status reports VmRSS and VmHWM in KiB.
    const uint64_t currentKb = readProcStatusKb("VmRSS:");
    const uint64_t peakKb = readProcStatusKb("VmHWM:");
    return makeMemoryStats(env,
            static_cast<jlong>(currentKb * 1024ULL),
            static_cast<jlong>(peakKb * 1024ULL));
#else
    return makeMemoryStats(env, 0, 0);
#endif
}

} // extern "C"
