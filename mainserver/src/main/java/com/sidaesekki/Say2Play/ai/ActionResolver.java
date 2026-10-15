package com.sidaesekki.Say2Play.ai;

import com.sidaesekki.Say2Play.game.domain.ActionType;

/**
 * 사용자 입력 텍스트를 Action Set 중 가장 비슷한 Action 1개로 바꾼다.
 * 구현체는 항상 Action 하나를 돌려주거나, 실패하면 AI_UNAVAILABLE/AI_TIMEOUT GameException을 던진다.
 */
public interface ActionResolver {

    ActionType resolve(String text);
}
