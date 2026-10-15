package com.sidaesekki.Say2Play.game.dto;

import com.sidaesekki.Say2Play.game.domain.ActionStatus;
import com.sidaesekki.Say2Play.game.domain.ActionType;
import com.sidaesekki.Say2Play.game.domain.QueuedAction;
import java.time.Instant;
import java.util.UUID;

public record ActionResponse(
        UUID id,
        long seq,
        ActionType actionType,
        ActionStatus status,
        Integer targetX,
        Integer targetY,
        boolean blocked,
        Instant startedAt) {

    public static ActionResponse from(QueuedAction action) {
        return new ActionResponse(action.getId(), action.getSeq(), action.getActionType(), action.getStatus(),
                action.getTargetX(), action.getTargetY(), action.isBlocked(), action.getStartedAt());
    }
}
