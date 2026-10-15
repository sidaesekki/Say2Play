package com.sidaesekki.Say2Play.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.sidaesekki.Say2Play.game.domain.ActionType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FakeActionResolverTest {

    private final FakeActionResolver resolver = new FakeActionResolver();

    @ParameterizedTest
    @CsvSource({
            "앞으로 가, MOVE_FORWARD",
            "전진해, MOVE_FORWARD",
            "뒤로 물러나, MOVE_BACKWARD",
            "후진, MOVE_BACKWARD",
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
