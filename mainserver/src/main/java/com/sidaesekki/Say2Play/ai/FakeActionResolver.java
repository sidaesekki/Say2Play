package com.sidaesekki.Say2Play.ai;

import com.sidaesekki.Say2Play.game.domain.ActionType;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * aiserver 없이 개발하기 위한 가짜. 키워드로 Jev를 흉내 낸다.
 * Jev처럼 어떤 입력에도 Action 하나를 돌려주며, 맞는 키워드가 없으면 defend를 고른다.
 */
public class FakeActionResolver implements ActionResolver {

    private static final ActionType FALLBACK = ActionType.DEFEND;
    private static final String TURN = "(돌기|돌아|돌자|돌려|회전|turn)";

    // 위에서부터 먼저 맞는 규칙을 쓴다. "오른쪽으로 돌아"가 이동이 되지 않도록 회전을 이동보다 먼저 본다.
    private static final List<Rule> RULES = List.of(
            new Rule("(왼|좌|left).*" + TURN + "|turnleft|좌회전", ActionType.TURN_LEFT),
            new Rule("(오른|우|right).*" + TURN + "|turnright|우회전", ActionType.TURN_RIGHT),
            new Rule("앞으로|앞쪽|전진|나아가|forward", ActionType.MOVE_FORWARD),
            new Rule("뒤로|뒤쪽|후진|물러|backward", ActionType.MOVE_BACKWARD),
            new Rule("구르|굴러|굴려|회피|피하|roll|dodge", ActionType.ROLL),
            new Rule("방어|막아|막기|가드|block|defend|guard", ActionType.DEFEND),
            new Rule("공격|때려|때리|베어|찔러|돌격|attack|hit|strike|charge", ActionType.ATTACK));

    @Override
    public ActionType resolve(String text) {
        String normalized = text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        return RULES.stream()
                .filter(rule -> rule.pattern().matcher(normalized).find())
                .map(Rule::actionType)
                .findFirst()
                .orElse(FALLBACK);
    }

    private record Rule(Pattern pattern, ActionType actionType) {
        Rule(String regex, ActionType actionType) {
            this(Pattern.compile(regex), actionType);
        }
    }
}
