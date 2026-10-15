package com.sidaesekki.Say2Play.global.error;

public class GameException extends RuntimeException {

    private final ErrorCode errorCode;

    public GameException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public GameException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.message(), cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
