#include <jni.h>

#include <windows.h>
#include <psapi.h>

#include <cstdint>
#include <vector>

#include "wirehair/wirehair.h"

namespace {

struct Ctx {
    wirehair_codec_t codec = nullptr;
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
Java_com_hrz_wirehairbench_WirehairNative_init(JNIEnv*, jclass) {
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

    Ctx* ctx = new Ctx();
    ctx->input.resize(static_cast<size_t>(n));

    env->GetByteArrayRegion(
            message,
            0,
            n,
            reinterpret_cast<jbyte*>(ctx->input.data()));

    wirehair_codec_t codec = nullptr;

    WirehairResult result = wirehair_encoder_create_ex(
            nullptr,
            ctx->input.data(),
            static_cast<uint64_t>(n),
            static_cast<uint16_t>(blockBytes),
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

    if (ctx == nullptr || ctx->codec == nullptr || output == nullptr) {
        throwRuntime(env, "Invalid encoder handle or output.");
        return -1;
    }

    jsize cap = env->GetArrayLength(output);
    if (cap <= 0) {
        throwRuntime(env, "Output buffer is empty.");
        return -1;
    }

    std::vector<uint8_t> buffer(static_cast<size_t>(cap));

    WirehairResult result = wirehair_encode(
            ctx->codec,
            static_cast<uint32_t>(blockId),
            buffer.data());

    if (result < 0) {
        return static_cast<jint>(result);
    }

    env->SetByteArrayRegion(
            output,
            0,
            cap,
            reinterpret_cast<const jbyte*>(buffer.data()));

    return cap;
}

JNIEXPORT jlong JNICALL
Java_com_hrz_wirehairbench_WirehairNative_createDecoder(
        JNIEnv* env, jclass, jlong messageBytes, jint blockBytes) {

    if (messageBytes <= 0 || blockBytes <= 0) {
        throwRuntime(env, "Invalid decoder arguments.");
        return 0;
    }

    Ctx* ctx = new Ctx();

    wirehair_codec_t codec = nullptr;

    WirehairResult result = wirehair_decoder_create_ex(
            nullptr,
            static_cast<uint64_t>(messageBytes),
            static_cast<uint16_t>(blockBytes),
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

    if (ctx == nullptr || ctx->codec == nullptr || packet == nullptr) {
        throwRuntime(env, "Invalid decoder handle or packet.");
        return -1;
    }

    jsize packetLength = env->GetArrayLength(packet);
    if (length < 0 || length > packetLength) {
        throwRuntime(env, "Invalid packet length.");
        return -1;
    }

    std::vector<uint8_t> buffer(static_cast<size_t>(length));

    env->GetByteArrayRegion(
            packet,
            0,
            length,
            reinterpret_cast<jbyte*>(buffer.data()));

    return static_cast<jint>(wirehair_decode(
            ctx->codec,
            static_cast<uint32_t>(blockId),
            buffer.data(),
            static_cast<uint16_t>(length)));
}

JNIEXPORT jint JNICALL
Java_com_hrz_wirehairbench_WirehairNative_recoverInto(
        JNIEnv* env, jclass, jlong handle, jbyteArray output) {

    Ctx* ctx = fromHandle(handle);

    if (ctx == nullptr || ctx->codec == nullptr || output == nullptr) {
        throwRuntime(env, "Invalid decoder handle or output.");
        return -1;
    }

    jsize outputLength = env->GetArrayLength(output);

    std::vector<uint8_t> buffer(static_cast<size_t>(outputLength));

    WirehairResult result = wirehair_recover(
            ctx->codec,
            buffer.data());

    if (result < 0) {
        return static_cast<jint>(result);
    }

    env->SetByteArrayRegion(
            output,
            0,
            outputLength,
            reinterpret_cast<const jbyte*>(buffer.data()));

    return static_cast<jint>(result);
}

JNIEXPORT void JNICALL
Java_com_hrz_wirehairbench_WirehairNative_free(
        JNIEnv*, jclass, jlong handle) {

    delete fromHandle(handle);
}

JNIEXPORT jstring JNICALL
Java_com_hrz_wirehairbench_WirehairNative_resultString(
        JNIEnv* env, jclass, jint result) {

    const char* text = wirehair_result_string(
            static_cast<WirehairResult>(result));

    if (text == nullptr) {
        return env->NewStringUTF("unknown");
    }

    return env->NewStringUTF(text);
}

JNIEXPORT jlongArray JNICALL
Java_com_hrz_wirehairbench_WirehairNative_memoryStats(
        JNIEnv* env, jclass) {

    PROCESS_MEMORY_COUNTERS_EX pmc{};
    pmc.cb = sizeof(pmc);

    if (!GetProcessMemoryInfo(
            GetCurrentProcess(),
            reinterpret_cast<PROCESS_MEMORY_COUNTERS*>(&pmc),
            sizeof(pmc))) {

        jlongArray result = env->NewLongArray(2);
        jlong zero[2] = {0, 0};
        env->SetLongArrayRegion(result, 0, 2, zero);
        return result;
    }

    jlong values[2] = {
        static_cast<jlong>(pmc.WorkingSetSize),
        static_cast<jlong>(pmc.PeakWorkingSetSize)
    };

    jlongArray result = env->NewLongArray(2);
    env->SetLongArrayRegion(result, 0, 2, values);
    return result;
}

} // extern "C"
