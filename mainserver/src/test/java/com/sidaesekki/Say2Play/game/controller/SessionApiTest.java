package com.sidaesekki.Say2Play.game.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sidaesekki.Say2Play.game.service.GameSessionService;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// REST만 쓰는 테스트라 STOMP 연결이 없어도 참가자가 나간 것으로 처리되지 않게 유예를 길게 둔다.
@SpringBootTest(properties = "say2play.session.reconnect-grace=1h")
@AutoConfigureMockMvc
class SessionApiTest {

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final String TOKEN = "X-Player-Token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GameSessionService gameSessionService;

    @Test
    void SOLO_세션을_만들면_한_명이_두_캐릭터를_맡는다() throws Exception {
        mockMvc.perform(create("SOLO"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.session.id").value(matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.data.session.mode").value("SOLO"))
                .andExpect(jsonPath("$.data.session.status").value("PLAYING"))
                .andExpect(jsonPath("$.data.session.mapCols").value(13))
                .andExpect(jsonPath("$.data.characters", hasSize(2)))
                .andExpect(jsonPath("$.data.characters[0].type").value("PLAYER"))
                .andExpect(jsonPath("$.data.characters[0].x").value(6))
                .andExpect(jsonPath("$.data.characters[0].y").value(7))
                .andExpect(jsonPath("$.data.characters[0].hp").value(100))
                .andExpect(jsonPath("$.data.characters[0].queue", hasSize(0)))
                .andExpect(jsonPath("$.data.characters[1].type").value("ENEMY"))
                .andExpect(jsonPath("$.data.players", hasSize(1)))
                .andExpect(jsonPath("$.data.players[0].controls", contains("PLAYER", "ENEMY")))
                .andExpect(jsonPath("$.data.players[0].connected").value(false))
                .andExpect(jsonPath("$.data.me.playerToken").isString())
                .andExpect(jsonPath("$.data.me.controls", contains("PLAYER", "ENEMY")));
    }

    @Test
    void 모드가_없거나_잘못되면_400() throws Exception {
        for (String body : new String[] {"{}", "{\"mode\":\"TRIO\"}", ""}) {
            mockMvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void SOLO는_두_캐릭터_모두에_명령할_수_있다() throws Exception {
        Session solo = Session.of(mockMvc.perform(create("SOLO")));

        command(solo, solo.token, solo.playerId, "공격")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.characterId").value(solo.playerId))
                .andExpect(jsonPath("$.data.action.actionType").value("attack"))
                .andExpect(jsonPath("$.data.queueLength").value(1));
        command(solo, solo.token, solo.enemyId, "앞으로 가")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.action.actionType").value("move_forward"))
                .andExpect(jsonPath("$.data.action.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.action.targetY").value(2));

        mockMvc.perform(get("/api/v1/sessions/{id}", solo.id).header(TOKEN, solo.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.characters[0].state").value("ATTACKING"))
                .andExpect(jsonPath("$.data.characters[0].queue[0].status").value("RUNNING"))
                .andExpect(jsonPath("$.data.me.playerToken").doesNotExist())
                .andExpect(jsonPath("$.data.me.controls", contains("PLAYER", "ENEMY")));
    }

    @Test
    void 토큰이_없거나_틀리면_401() throws Exception {
        Session solo = Session.of(mockMvc.perform(create("SOLO")));

        mockMvc.perform(get("/api/v1/sessions/{id}", solo.id))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_PLAYER_TOKEN"));
        command(solo, "wrong", solo.playerId, "공격")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_PLAYER_TOKEN"));
        mockMvc.perform(post("/api/v1/sessions/{id}/leave", solo.id))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void MULTI는_상대가_참가할_때까지_기다린다() throws Exception {
        Session host = Session.of(mockMvc.perform(create("MULTI"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.session.mode").value("MULTI"))
                .andExpect(jsonPath("$.data.session.status").value("WAITING"))
                .andExpect(jsonPath("$.data.me.controls", contains("PLAYER"))));

        command(host, host.token, host.playerId, "공격")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WAITING_FOR_OPPONENT"));

        String guestToken = JsonPath.read(join(host.id)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.session.status").value("PLAYING"))
                .andExpect(jsonPath("$.data.players", hasSize(2)))
                .andExpect(jsonPath("$.data.me.controls", contains("ENEMY")))
                .andReturn().getResponse().getContentAsString(), "$.data.me.playerToken");

        command(host, host.token, host.playerId, "공격").andExpect(status().isCreated());
        command(host, guestToken, host.enemyId, "방어").andExpect(status().isCreated());
    }

    @Test
    void MULTI에서는_자기_캐릭터에만_명령하고_초기화할_수_없다() throws Exception {
        Session host = Session.of(mockMvc.perform(create("MULTI")));
        String guestToken = JsonPath.read(join(host.id).andReturn().getResponse().getContentAsString(),
                "$.data.me.playerToken");

        command(host, host.token, host.enemyId, "공격")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_YOUR_CHARACTER"));
        command(host, guestToken, host.playerId, "공격")
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/sessions/{id}/reset", host.id).header(TOKEN, host.token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("RESET_NOT_ALLOWED"));
    }

    @Test
    void 자리가_다_찼거나_SOLO_세션이면_참가할_수_없다() throws Exception {
        Session multi = Session.of(mockMvc.perform(create("MULTI")));
        join(multi.id).andExpect(status().isCreated());
        join(multi.id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SESSION_FULL"));

        Session solo = Session.of(mockMvc.perform(create("SOLO")));
        join(solo.id).andExpect(status().isConflict());
        join("3f2a1c9e-7b1d-4c2e-9a51-0d6e8f4b2c10").andExpect(status().isNotFound());
    }

    @Test
    void MULTI에서_한_명이_나가면_남은_사람은_다시_기다리고_마지막_사람이_나가면_세션이_끝난다() throws Exception {
        Session host = Session.of(mockMvc.perform(create("MULTI")));
        String guestToken = JsonPath.read(join(host.id).andReturn().getResponse().getContentAsString(),
                "$.data.me.playerToken");

        mockMvc.perform(post("/api/v1/sessions/{id}/leave", host.id).header(TOKEN, host.token))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mockMvc.perform(get("/api/v1/sessions/{id}", host.id).header(TOKEN, host.token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/sessions/{id}", host.id).header(TOKEN, guestToken))
                .andExpect(jsonPath("$.data.session.status").value("WAITING"))
                .andExpect(jsonPath("$.data.players", hasSize(1)));

        // 나간 자리(PLAYER)에 새 상대가 들어온다.
        join(host.id).andExpect(jsonPath("$.data.me.controls", contains("PLAYER")));

        mockMvc.perform(post("/api/v1/sessions/{id}/leave", host.id).header(TOKEN, guestToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void SOLO에서_나가면_세션이_끝난다() throws Exception {
        Session solo = Session.of(mockMvc.perform(create("SOLO")));

        mockMvc.perform(post("/api/v1/sessions/{id}/leave", solo.id).header(TOKEN, solo.token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/sessions/{id}", solo.id).header(TOKEN, solo.token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void SOLO는_수동_초기화할_수_있다() throws Exception {
        Session solo = Session.of(mockMvc.perform(create("SOLO")));
        command(solo, solo.token, solo.playerId, "앞으로 가");

        mockMvc.perform(post("/api/v1/sessions/{id}/reset", solo.id).header(TOKEN, solo.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.characters[0].y").value(7))
                .andExpect(jsonPath("$.data.characters[0].state").value("IDLE"))
                .andExpect(jsonPath("$.data.characters[0].queue", hasSize(0)));
    }

    @Test
    void 텍스트가_없거나_공백이거나_500자를_넘으면_400() throws Exception {
        Session solo = Session.of(mockMvc.perform(create("SOLO")));
        for (String body : new String[] {"{}", "{\"text\":\"   \"}", "{\"text\":\"" + "가".repeat(501) + "\"}", "x"}) {
            mockMvc.perform(post("/api/v1/sessions/{sid}/characters/{cid}/commands", solo.id, solo.playerId)
                            .header(TOKEN, solo.token).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void 세션이나_캐릭터가_없으면_404() throws Exception {
        Session solo = Session.of(mockMvc.perform(create("SOLO")));
        mockMvc.perform(get("/api/v1/sessions/{id}", "not-a-uuid").header(TOKEN, solo.token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
        command(solo, solo.token, "b9d5f3c2-6a4e-4f8b-87da-2e3f4a5b6c7d", "공격")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CHARACTER_NOT_FOUND"));
    }

    @Test
    void 큐가_100개면_429() throws Exception {
        Session solo = Session.of(mockMvc.perform(create("SOLO")));
        for (int i = 0; i < 100; i++) {
            command(solo, solo.token, solo.playerId, "방어").andExpect(status().isCreated());
        }
        command(solo, solo.token, solo.playerId, "방어")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("QUEUE_FULL"));
    }

    @Test
    void 마지막_입력_후_30분이_지나면_만료된다() throws Exception {
        Session solo = Session.of(mockMvc.perform(create("SOLO")));

        gameSessionService.expireIdleSessions(Instant.now().plus(Duration.ofMinutes(29)));
        mockMvc.perform(get("/api/v1/sessions/{id}", solo.id).header(TOKEN, solo.token))
                .andExpect(status().isOk());

        gameSessionService.expireIdleSessions(Instant.now().plus(Duration.ofMinutes(31)));
        mockMvc.perform(get("/api/v1/sessions/{id}", solo.id).header(TOKEN, solo.token))
                .andExpect(status().isNotFound());
    }

    @Test
    void CORS는_localhost_5173만_허용하고_토큰_헤더를_받는다() throws Exception {
        mockMvc.perform(options("/api/v1/sessions")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "x-player-token,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mockMvc.perform(options("/api/v1/sessions")
                        .header("Origin", "http://localhost:8000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    private static org.springframework.test.web.servlet.RequestBuilder create(String mode) {
        return post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"" + mode + "\"}");
    }

    private ResultActions join(String sessionId) throws Exception {
        return mockMvc.perform(post("/api/v1/sessions/{id}/join", sessionId));
    }

    private ResultActions command(Session session, String token, String characterId, String text) throws Exception {
        return mockMvc.perform(post("/api/v1/sessions/{sid}/characters/{cid}/commands", session.id, characterId)
                .header(TOKEN, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"" + text + "\"}"));
    }

    private record Session(String id, String token, String playerId, String enemyId) {
        static Session of(ResultActions created) throws Exception {
            String body = created.andReturn().getResponse().getContentAsString();
            return new Session(JsonPath.read(body, "$.data.session.id"), JsonPath.read(body, "$.data.me.playerToken"),
                    JsonPath.read(body, "$.data.characters[0].id"), JsonPath.read(body, "$.data.characters[1].id"));
        }
    }
}
