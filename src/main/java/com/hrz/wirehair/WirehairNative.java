package com.hrz.wirehair;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Wirehair JNI 底层接口。
 *
 * 默认从 JAR 内加载原生库，也允许通过 JVM 参数指定原生库路径：
 *
 * -Dwirehairjni.path=D:\path\wirehairjni.dll
 *
 * 当前 GitHub Actions 目标平台：
 * Windows x86_64：native/windows-x86_64/wirehairjni.dll
 * Linux x86_64：native/linux-x86_64/libwirehairjni.so
 */
final class WirehairNative {

    /** 标记原生库是否已成功加载并初始化。 */
    private static volatile boolean loaded;

    private WirehairNative() {
    }

    /**
     * 加载原生库并初始化 Wirehair。
     * synchronized 保证多线程首次调用时不会重复加载。
     */
    static synchronized void load() {
        if (loaded) {
            return;
        }

        String explicitPath = System.getProperty("wirehairjni.path");

        if (explicitPath != null && !explicitPath.isBlank()) {
            // 用户显式指定原生库路径。
            System.load(Path.of(explicitPath).toAbsolutePath().toString());
        } else {
            // 从当前 JAR 的资源目录中提取对应平台的原生库。
            loadBundledLibrary();
        }

        // Wirehair 初始化成功时返回 0。
        int result = init();

        if (result != 0) {
            throw new WirehairException(
                    "Wirehair 初始化失败，原生返回码：" + result);
        }

        loaded = true;
    }

    /**
     * 根据操作系统和 CPU 架构，提取并加载 JAR 中的原生库。
     */
    private static void loadBundledLibrary() {
        String osName = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT);

        String archName = System.getProperty("os.arch", "")
                .toLowerCase(Locale.ROOT);

        String platform;
        String libraryName;

        if (osName.startsWith("windows")) {
            platform = "windows-" + normalizeArch(archName);
            libraryName = "wirehairjni.dll";
        } else if (osName.startsWith("linux")) {
            platform = "linux-" + normalizeArch(archName);
            libraryName = "libwirehairjni.so";
        } else {
            throw new WirehairException(
                    "暂不支持的操作系统：" + osName);
        }

        String resourcePath =
                "/native/" + platform + "/" + libraryName;

        try (InputStream input =
                     WirehairNative.class.getResourceAsStream(resourcePath)) {

            if (input == null) {
                throw new WirehairException(
                        "JAR 中没有找到原生库：" + resourcePath
                        + "。请检查 GitHub Actions 是否成功打包原生库。");
            }

            String suffix =
                    libraryName.substring(libraryName.lastIndexOf('.'));

            Path extracted =
                    Files.createTempFile("wirehairjni-", suffix);

            extracted.toFile().deleteOnExit();

            Files.copy(
                    input,
                    extracted,
                    StandardCopyOption.REPLACE_EXISTING);

            System.load(extracted.toAbsolutePath().toString());

        } catch (IOException e) {
            throw new WirehairException(
                    "提取原生库失败：" + resourcePath, e);
        }
    }

    /**
     * 统一识别 Windows/Linux 常见的 64 位 x86 架构名称。
     */
    private static String normalizeArch(String archName) {
        if ("amd64".equals(archName)
                || "x86_64".equals(archName)
                || "x64".equals(archName)) {
            return "x86_64";
        }

        throw new WirehairException(
                "当前未打包此 CPU 架构的原生库：" + archName);
    }

    // 以下方法由 JNI 的 C++ 实现提供。

    /** 初始化 Wirehair 原生运行环境，成功返回 0。 */
    static native int init();

    /** 创建编码器，返回原生句柄；创建失败返回 0。 */
    static native long createEncoder(byte[] message, int blockBytes);

    /** 编码指定 ID 的数据块，返回写入的字节数。 */
    static native int encodeInto(
            long encoder,
            int blockId,
            byte[] output);

    /** 创建解码器，返回原生句柄；创建失败返回 0。 */
    static native long createDecoder(
            long messageBytes,
            int blockBytes);

    /**
     * 提交一个编码块：
     * 1：已经可以恢复完整消息；
     * 0：还需要更多编码块；
     * 负数：原生错误码的负值。
     */
    static native int decode(
            long decoder,
            int blockId,
            byte[] packet,
            int length);

    /** 恢复完整消息，成功返回 0。 */
    static native int recoverInto(
            long decoder,
            byte[] output);

    /** 释放编码器或解码器的原生资源。 */
    static native void free(long handle);

    /** 将 Wirehair 错误码转换为可读文本。 */
    static native String resultString(int result);

    /**
     * 获取进程内存统计。
     * 返回值：[当前驻留内存字节数, 历史峰值驻留内存字节数]。
     */
    static native long[] memoryStats();
}