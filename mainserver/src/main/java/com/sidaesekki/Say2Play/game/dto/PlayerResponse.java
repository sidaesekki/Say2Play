package com.sidaesekki.Say2Play.game.dto;

import com.sidaesekki.Say2Play.game.domain.CharacterType;
import com.sidaesekki.Say2Play.game.domain.Participant;
import java.util.List;

/** 참가자 한 명. 토큰과 내부 ID는 노출하지 않고, 맡은 캐릭터와 연결 여부만 보낸다. */
public record PlayerResponse(List<CharacterType> controls, boolean connected) {

    public static PlayerResponse from(Participant participant) {
        return new PlayerResponse(participant.getControls(), participant.isConnected());
    }
}
