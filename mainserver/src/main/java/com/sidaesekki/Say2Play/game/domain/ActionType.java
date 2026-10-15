package com.sidaesekki.Say2Play.game.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * Action Set. 키는 aiserver/actions.json의 키와 같다(erd.md "ACTION_TYPE").
 */
public enum ActionType {
    MOVE_FORWARD("move_forward", 320, CharacterState.MOVING),
    MOVE_BACKWARD("move_backward", 320, CharacterState.MOVING),
    TURN_LEFT("turn_left", 300, CharacterState.TURNING),
    TURN_RIGHT("turn_right", 300, CharacterState.TURNING),
    ATTACK("attack", 480, CharacterState.ATTACKING),
    DEFEND("defend", 1100, CharacterState.DEFENDING),
    ROLL("roll", 500, CharacterState.ROLLING);

    private final String key;
    private final Duration duration;
    private final CharacterState state;

    ActionType(String key, long durationMillis, CharacterState state) {
        this.key = key;
        this.duration = Duration.ofMillis(durationMillis);
        this.state = state;
    }

    @JsonValue
    public String key() {
        return key;
    }

    public Duration duration() {
        return duration;
    }

    public CharacterState state() {
        return state;
    }

    public static Optional<ActionType> fromKey(String key) {
        return Arrays.stream(values()).filter(type -> type.key.equals(key)).findFirst();
    }

    /** 칸을 옮기는 동작이면 이동 방향을, 아니면 empty를 돌려준다. */
    public Optional<Facing> moveDirection(Facing facing) {
        return switch (this) {
            case MOVE_FORWARD -> Optional.of(facing);
            case MOVE_BACKWARD -> Optional.of(facing.opposite());
            case ROLL -> Optional.of(facing);
            default -> Optional.empty();
        };
    }

    public Facing facingAfter(Facing facing) {
        return switch (this) {
            case TURN_LEFT -> facing.turnLeft();
            case TURN_RIGHT -> facing.turnRight();
            default -> facing;
        };
    }
}
