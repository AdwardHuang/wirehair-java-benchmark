
#include <jni.h>

#include <windows.h>
#include <psapi.h>

#include <cstdint>
#include <limits>
#include <vector>

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

} // namespace

extern "C" {

JNIEXPORT jint JNICALL
Java_com_hrz_wirehairbench_WirehairNative_init(
        JNIEnv*, jclass) {
    return static_cast<jint>(wirehair_init());
}

JNIEXPORT jlong JNICALL
Java_com_hrz_wirehairbench_WirehairNative_createEncoder(
        JNIEnv* env, jclass, jbyteArray message, jint blockBytes) {

    if (message == nullptr || blockBytes <= 0) {
        throwRuntime(env, "Invalid encoder arguments.");
        return 0;
    }

    jsize n = env->GetArrayLength(message);
    if (n <= 0) {
        throwRuntime(env, "Input message is empty.");
        return 0;
    }

    Ctx* ctx = new Ctx();

    try {
        ctx->input.resize(static_cast<size_t>(n));
    } catch (...) {
        delete ctx;
        throwRuntime(env, "Failed to allocate encoder input.");
        return 0;
    }

    env->GetByteArrayRegion(
            message,
            0,
            n,
            reinterpret_cast<jbyte*>(ctx->input.data()));

    if (env->ExceptionCheck()) {
        delete ctx;
        return 0;
    }

    WirehairCodec codec = nullptr;

    WirehairResult result = wirehair_encoder_create_ex(
            nullptr,
            ctx->input.data(),
            static_cast<uint64_t>(n),
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
        JNIEnv* env,
        jclass,
        jlong handle,
        jint blockId,
        jbyteArray output) {

    Ctx* ctx = fromHandle(handle);

    if (ctx == nullptr || ctx->codec == nullptr || output == nullptr) {
        throwRuntime(env, "Invalid encoder handle or output.");
        return -1;
    }

    if (blockId < 0) {
        throwRuntime(env, "Invalid block ID.");
        return -1;
    }

    jsize cap = env->GetArrayLength(output);

    if (cap <= 0 ||
            static_cast<uint64_t>(cap) >
                    std::numeric_limits<uint32_t>::max()) {
        throwRuntime(env, "Invalid output buffer size.");
        return -1;
    }

    std::vector<uint8_t> buffer;

    try {
        buffer.resize(static_cast<size_t>(cap));
    } catch (...) {
        throwRuntime(env, "Failed to allocate encoding buffer.");
        return -1;
    }

    uint32_t dataBytes = 0;

    WirehairResult result = wirehair_encode(
            ctx->codec,
            static_cast<unsigned>(blockId),
            buffer.data(),
            static_cast<uint32_t>(cap),
            &dataBytes);

    if (result != Wirehair_Success) {
        return static_cast<jint>(result);
    }

    if (dataBytes > static_cast<uint32_t>(cap)) {
        throwRuntime(env, "Wirehair returned an invalid encoded length.");
        return -1;
    }

    if (dataBytes > 0) {
        env->SetByteArrayRegion(
                output,
                0,
                static_cast<jsize>(dataBytes),
                reinterpret_cast<const jbyte*>(buffer.data()));
    }

    if (env->ExceptionCheck()) {
        return -1;
    }

    return static_cast<jint>(dataBytes);
}

JNIEXPORT jlong JNICALL
Java_com_hrz_wirehairbench_WirehairNative_createDecoder(
        JNIEnv* env,
        jclass,
        jlong messageBytes,
        jint blockBytes) {

    if (messageBytes <= 0 || blockBytes <= 0) {
        throwRuntime(env, "Invalid decoder arguments.");
        return 0;
    }

    Ctx* ctx = new Ctx();

    WirehairCodec codec = nullptr;

    WirehairResult result = wirehair_decoder_create_ex(
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
        JNIEnv* env,
        jclass,
        jlong handle,
        jint blockId,
        jbyteArray packet,
        jint length) {

    Ctx* ctx = fromHandle(handle);

    if (ctx == nullptr || ctx->codec == nullptr || packet == nullptr) {
        throwRuntime(env, "Invalid decoder handle or packet.");
        return -1;
    }

    if (blockId < 0) {
        throwRuntime(env, "Invalid block ID.");
        return -1;
    }

    jsize packetLength = env->GetArrayLength(packet);

    if (length <= 0 || length > packetLength) {
        throwRuntime(env, "Invalid packet length.");
        return -1;
    }

    std::vector<uint8_t> buffer;

    try {
        buffer.resize(static_cast<size_t>(length));
    } catch (...) {
        throwRuntime(env, "Failed to allocate decoding buffer.");
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

    WirehairResult result = wirehair_decode(
            ctx->codec,
            static_cast<unsigned>(blockId),
            buffer.data(),
            static_cast<uint32_t>(length));

    return static_cast<jint>(result);
}

JNIEXPORT jint JNICALL
Java_com_hrz_wirehairbench_WirehairNative_recoverInto(
        JNIEnv* env,
        jclass,
        jlong handle,
        jbyteArray output) {

    Ctx* ctx = fromHandle(handle);

    if (ctx == nullptr || ctx->codec == nullptr || output == nullptr) {
        throwRuntime(env, "Invalid decoder handle or output.");
        return -1;
    }

    jsize outputLength = env->GetArrayLength(output);

    if (outputLength <= 0) {
        throwRuntime(env, "Output buffer is empty.");
        return -1;
    }

    std::vector<uint8_t> buffer;

    try {
        buffer.resize(static_cast<size_t>(outputLength));
    } catch (...) {
        throwRuntime(env, "Failed to allocate recovery buffer.");
        return -1;
    }

    WirehairResult result = wirehair_recover(
            ctx->codec,
            buffer.data(),
            static_cast<uint64_t>(outputLength));

    if (result != Wirehair_Success) {
        return static_cast<jint>(result);
    }

    env->SetByteArrayRegion(
            output,
            0,
            outputLength,
            reinterpret_cast<const jbyte*>(buffer.data()));

    if (env->ExceptionCheck()) {
        return -1;
    }

    return static_cast<jint>(result);
}

JNIEXPORT void JNICALL
Java_com_hrz_wirehairbench_WirehairNative_free(
        JNIEnv*, jclass, jlong handle) {

    delete fromHandle(handle);
}

JNIEXPORT jstring JNICALL
Java_com_hrz_wirehairbench_WirehairNative_resultString(
        JNIEnv* env,
        jclass,
        jint result) {

    const char* message = wirehair_result_string(
            static_cast<WirehairResult>(result));

    if (message == nullptr) {
        return env->NewStringUTF("unknown");
    }

    return env->NewStringUTF(message);
}

JNIEXPORT jlongArray JNICALL
Java_com_hrz_wirehairbench_WirehairNative_memoryStats(
        JNIEnv* env,
        jclass) {

    PROCESS_MEMORY_COUNTERS_EX pmc{};
    pmc.cb = sizeof(pmc);

    if (!GetProcessMemoryInfo(
            GetCurrentProcess(),
            reinterpret_cast<PROCESS_MEMORY_COUNTERS*>(&pmc),
            sizeof(pmc))) {

        jlongArray result = env->NewLongArray(2);

        if (result == nullptr) {
            return nullptr;
        }

        jlong zero[2] = {0, 0};

        env->SetLongArrayRegion(result, 0, 2, zero);
        return result;
    }

    jlong values[2] = {
        static_cast<jlong>(pmc.WorkingSetSize),
        static_cast<jlong>(pmc.PeakWorkingSetSize)
    };

    jlongArray result = env->NewLongArray(2);

    if (result == nullptr) {
        return nullptr;
    }

    env->SetLongArrayRegion(result, 0, 2, values);
    return result;
}

} // extern "C"
