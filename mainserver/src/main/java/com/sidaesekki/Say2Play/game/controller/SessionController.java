package com.sidaesekki.Say2Play.game.controller;

import com.sidaesekki.Say2Play.game.dto.CommandRequest;
import com.sidaesekki.Say2Play.game.dto.CommandResponse;
import com.sidaesekki.Say2Play.game.dto.CreateSessionRequest;
import com.sidaesekki.Say2Play.game.dto.ResetResponse;
import com.sidaesekki.Say2Play.game.dto.SessionDetailResponse;
import com.sidaesekki.Say2Play.game.service.GameSessionService;
import com.sidaesekki.Say2Play.global.response.ApiResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 세션을 만들거나 참가할 때 받은 토큰을 이후 요청의 {@code X-Player-Token} 헤더로 보낸다. */
@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private static final String TOKEN_HEADER = "X-Player-Token";

    private final GameSessionService gameSessionService;

    public SessionController(GameSessionService gameSessionService) {
        this.gameSessionService = gameSessionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SessionDetailResponse> create(@RequestBody CreateSessionRequest request) {
        return ApiResponse.ok(gameSessionService.create(request.mode()));
    }

    @PostMapping("/{sessionId}/join")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SessionDetailResponse> join(@PathVariable UUID sessionId) {
        return ApiResponse.ok(gameSessionService.join(sessionId));
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<SessionDetailResponse> get(@PathVariable UUID sessionId,
                                                  @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        return ApiResponse.ok(gameSessionService.get(sessionId, token));
    }

    @PostMapping("/{sessionId}/characters/{characterId}/commands")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CommandResponse> command(@PathVariable UUID sessionId, @PathVariable UUID characterId,
                                                @RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                                @RequestBody CommandRequest request) {
        return ApiResponse.ok(gameSessionService.command(sessionId, characterId, token, request.text()));
    }

    @PostMapping("/{sessionId}/reset")
    public ApiResponse<ResetResponse> reset(@PathVariable UUID sessionId,
                                            @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        return ApiResponse.ok(gameSessionService.reset(sessionId, token));
    }

    @PostMapping("/{sessionId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@PathVariable UUID sessionId,
                      @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        gameSessionService.leave(sessionId, token);
    }
}
