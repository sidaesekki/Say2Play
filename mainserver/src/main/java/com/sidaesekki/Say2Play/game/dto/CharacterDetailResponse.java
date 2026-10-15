package com.sidaesekki.Say2Play.game.dto;

import com.sidaesekki.Say2Play.game.domain.CharacterState;
import com.sidaesekki.Say2Play.game.domain.CharacterType;
import com.sidaesekki.Say2Play.game.domain.Facing;
import com.sidaesekki.Say2Play.game.domain.GameCharacter;
import java.util.List;
import java.util.UUID;

/** 캐릭터 상태와 대기 중인 동작 목록. */
public record CharacterDetailResponse(
        UUID id,
        CharacterType type,
        int x,
        int y,
        Facing facing,
        CharacterState state,
        int hp,
        List<ActionResponse> queue) {

    public static CharacterDetailResponse from(GameCharacter character) {
        return new CharacterDetailResponse(character.getId(), character.getType(), character.getX(),
                character.getY(), character.getFacing(), character.getState(), character.getHp(),
                character.getQueue().stream().map(ActionResponse::from).toList());
    }
}
