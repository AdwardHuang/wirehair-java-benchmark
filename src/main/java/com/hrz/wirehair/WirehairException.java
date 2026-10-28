package com.hrz.wirehair;

/**
 * Wirehair 原生库或 JNI 调用发生错误时抛出的运行时异常。
 */
public class WirehairException extends RuntimeException {

    public WirehairException(String message) {
        super(message);
    }

    public WirehairException(String message, Throwable cause) {
        super(message, cause);
    }
}