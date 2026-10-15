package com.sidaesekki.Say2Play.global.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "명령 텍스트는 공백을 제외하고 1~500자로 입력해 주세요."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "세션을 찾을 수 없습니다."),
    CHARACTER_NOT_FOUND(HttpStatus.NOT_FOUND, "캐릭터를 찾을 수 없습니다."),
    SESSION_ENDED(HttpStatus.CONFLICT, "이미 종료된 세션입니다."),
    CHARACTER_DEAD(HttpStatus.CONFLICT, "쓰러진 캐릭터는 명령을 받을 수 없습니다. 초기화해 주세요."),
    QUEUE_FULL(HttpStatus.TOO_MANY_REQUESTS, "대기 중인 동작이 너무 많습니다. 잠시 뒤 다시 입력해 주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    AI_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "AI 서버 호출에 실패했습니다."),
    AI_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI 서버가 제시간에 응답하지 않았습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
