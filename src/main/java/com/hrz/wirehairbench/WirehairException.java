package com.hrz.wirehairbench;

/** Unchecked exception raised when a Wirehair JNI operation fails. */
public final class WirehairException extends RuntimeException {
    public WirehairException(String message) {
        super(message);
    }

    public WirehairException(String message, Throwable cause) {
        super(message, cause);
    }
}
