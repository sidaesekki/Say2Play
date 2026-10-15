package com.sidaesekki.Say2Play.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.sidaesekki.Say2Play.game.domain.ActionType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FakeActionResolverTest {

    private final FakeActionResolver resolver = new FakeActionResolver();

    @ParameterizedTest
    @CsvSource({
            "위로 가, MOVE_UP",
            "위로 올라가서 공격해, MOVE_UP",
            "아래로 이동, MOVE_DOWN",
            "왼쪽으로 이동, MOVE_LEFT",
            "오른쪽으로 한 발짝 가볼까, MOVE_RIGHT",
            "왼쪽으로 돌아, TURN_LEFT",
            "오른쪽으로 돌기, TURN_RIGHT",
            "Turn Right, TURN_RIGHT",
            "굴러!, ROLL",
            "막아, DEFEND",
            "ㄱㄱ 돌격, ATTACK",
            "attack, ATTACK",
            "오늘 날씨 좋다, DEFEND"
    })
    void 키워드로_Action_하나를_고른다(String text, ActionType expected) {
        assertThat(resolver.resolve(text)).isEqualTo(expected);
    }
}
