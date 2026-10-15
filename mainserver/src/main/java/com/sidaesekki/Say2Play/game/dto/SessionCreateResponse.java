package com.sidaesekki.Say2Play.game.dto;

import java.util.List;

public record SessionCreateResponse(SessionResponse session, List<CharacterResponse> characters) {
}
