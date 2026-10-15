package com.sidaesekki.Say2Play.game.dto;

import java.util.UUID;

public record CommandResponse(UUID characterId, ActionResponse action, int queueLength) {
}
