package com.sidaesekki.Say2Play.game.dto;

import java.util.List;

public record SessionDetailResponse(SessionResponse session, List<CharacterDetailResponse> characters) {
}
