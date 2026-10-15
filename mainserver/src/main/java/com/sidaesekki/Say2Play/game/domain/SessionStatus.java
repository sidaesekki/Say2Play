package com.sidaesekki.Say2Play.game.domain;

/**
 * WAITING: MULTI에서 상대를 기다리는 중. PLAYING: 명령을 받는 중.
 * ROUND_OVER: 한쪽이 쓰러져 자동 초기화를 기다리는 중. ENDED: 종료됨.
 */
public enum SessionStatus {
    WAITING, PLAYING, ROUND_OVER, ENDED
}
