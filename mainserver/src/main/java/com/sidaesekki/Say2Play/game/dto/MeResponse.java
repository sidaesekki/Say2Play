package com.sidaesekki.Say2Play.game.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sidaesekki.Say2Play.game.domain.CharacterType;
import com.sidaesekki.Say2Play.game.domain.Participant;
import java.util.List;

/** 요청한 참가자 자신. {@code playerToken}은 세션을 만들거나 참가할 때만 내려준다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MeResponse(String playerToken, List<CharacterType> controls) {

    public static MeResponse withToken(Participant participant) {
        return new MeResponse(participant.getToken(), participant.getControls());
    }

    public static MeResponse from(Participant participant) {
        return new MeResponse(null, participant.getControls());
    }
}
