package com.sidaesekki.Say2Play.game.controller;

import com.sidaesekki.Say2Play.game.dto.CommandRequest;
import com.sidaesekki.Say2Play.game.dto.CommandResponse;
import com.sidaesekki.Say2Play.game.dto.ResetResponse;
import com.sidaesekki.Say2Play.game.dto.SessionCreateResponse;
import com.sidaesekki.Say2Play.game.dto.SessionDetailResponse;
import com.sidaesekki.Say2Play.game.service.GameSessionService;
import com.sidaesekki.Say2Play.global.response.ApiResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final GameSessionService gameSessionService;

    public SessionController(GameSessionService gameSessionService) {
        this.gameSessionService = gameSessionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SessionCreateResponse> create() {
        return ApiResponse.ok(gameSessionService.create());
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<SessionDetailResponse> get(@PathVariable UUID sessionId) {
        return ApiResponse.ok(gameSessionService.get(sessionId));
    }

    @PostMapping("/{sessionId}/characters/{characterId}/commands")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CommandResponse> command(@PathVariable UUID sessionId, @PathVariable UUID characterId,
                                                @RequestBody CommandRequest request) {
        return ApiResponse.ok(gameSessionService.command(sessionId, characterId, request.text()));
    }

    @PostMapping("/{sessionId}/reset")
    public ApiResponse<ResetResponse> reset(@PathVariable UUID sessionId) {
        return ApiResponse.ok(gameSessionService.reset(sessionId));
    }

    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID sessionId) {
        gameSessionService.delete(sessionId);
    }
}
