package com.sidaesekki.Say2Play.game.dto;

import com.sidaesekki.Say2Play.game.domain.CharacterState;
import com.sidaesekki.Say2Play.game.domain.CharacterType;
import com.sidaesekki.Say2Play.game.domain.Facing;
import com.sidaesekki.Say2Play.game.domain.GameCharacter;
import java.util.UUID;

public record CharacterResponse(UUID id, CharacterType type, int x, int y, Facing facing, CharacterState state) {

    public static CharacterResponse from(GameCharacter character) {
        return new CharacterResponse(character.getId(), character.getType(), character.getX(), character.getY(),
                character.getFacing(), character.getState());
    }
}
