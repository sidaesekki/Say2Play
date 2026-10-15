package com.sidaesekki.Say2Play.game.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class SessionApiTest {

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GameSessionService gameSessionService;

    private String sessionId;
    private String playerId;
    private String enemyId;

    @BeforeEach
    void createSession() throws Exception {
        String body = mockMvc.perform(post("/api/v1/sessions"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        sessionId = JsonPath.read(body, "$.data.session.id");
        playerId = JsonPath.read(body, "$.data.characters[0].id");
        enemyId = JsonPath.read(body, "$.data.characters[1].id");
    }

    @Test
    void 세션을_만들면_캐릭터_2개가_시작_위치에_배치된다() throws Exception {
        mockMvc.perform(post("/api/v1/sessions"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.data.session.id").value(matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.data.session.status").value("PLAYING"))
                .andExpect(jsonPath("$.data.session.mapCols").value(13))
                .andExpect(jsonPath("$.data.session.mapRows").value(9))
                .andExpect(jsonPath("$.data.characters", hasSize(2)))
                .andExpect(jsonPath("$.data.characters[0].type").value("PLAYER"))
                .andExpect(jsonPath("$.data.characters[0].x").value(6))
                .andExpect(jsonPath("$.data.characters[0].y").value(7))
                .andExpect(jsonPath("$.data.characters[0].facing").value("N"))
                .andExpect(jsonPath("$.data.characters[0].state").value("IDLE"))
                .andExpect(jsonPath("$.data.characters[0].hp").value(100))
                .andExpect(jsonPath("$.data.characters[0].queue").doesNotExist())
                .andExpect(jsonPath("$.data.characters[1].type").value("ENEMY"))
                .andExpect(jsonPath("$.data.characters[1].y").value(1))
                .andExpect(jsonPath("$.data.characters[1].facing").value("S"));
    }

    @Test
    void 명령을_보내면_Action_1개를_큐에_넣는다() throws Exception {
        command(playerId, "{\"text\":\"  공격  \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.characterId").value(playerId))
                .andExpect(jsonPath("$.data.action.id").value(matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.data.action.seq").value(1))
                .andExpect(jsonPath("$.data.action.actionType").value("attack"))
                .andExpect(jsonPath("$.data.queueLength").value(1));

        // 쉬고 있던 캐릭터라 바로 실행이 시작된다.
        command(playerId, "{\"text\":\"방어\"}")
                .andExpect(jsonPath("$.data.action.seq").value(2))
                .andExpect(jsonPath("$.data.action.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.action.targetX").value((Object) null))
                .andExpect(jsonPath("$.data.action.blocked").value(false))
                .andExpect(jsonPath("$.data.action.startedAt").value((Object) null))
                .andExpect(jsonPath("$.data.queueLength").value(2));

        mockMvc.perform(get("/api/v1/sessions/{id}", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.characters[0].state").value("ATTACKING"))
                .andExpect(jsonPath("$.data.characters[0].queue", hasSize(2)))
                .andExpect(jsonPath("$.data.characters[0].queue[0].status").value("RUNNING"))
                .andExpect(jsonPath("$.data.characters[0].queue[0].startedAt")
                        .value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*Z")))
                .andExpect(jsonPath("$.data.characters[0].queue[1].actionType").value("defend"))
                .andExpect(jsonPath("$.data.characters[1].queue", hasSize(0)));
    }

    @Test
    void 이동_동작은_시작할_때_목표_칸이_정해진다() throws Exception {
        command(enemyId, "{\"text\":\"앞으로 가\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.action.actionType").value("move_forward"))
                .andExpect(jsonPath("$.data.action.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.action.targetX").value(6))
                .andExpect(jsonPath("$.data.action.targetY").value(2));
    }

    @Test
    void 텍스트가_없거나_공백이거나_500자를_넘으면_400() throws Exception {
        for (String body : new String[] {"{}", "{\"text\":\"   \"}", "{\"text\":\"" + "가".repeat(501) + "\"}", "not-json"}) {
            command(playerId, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data").doesNotExist())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        command(playerId, "{\"text\":\"" + "가".repeat(500) + "\"}").andExpect(status().isCreated());
    }

    @Test
    void 세션이나_캐릭터가_없으면_404() throws Exception {
        mockMvc.perform(get("/api/v1/sessions/{id}", "3f2a1c9e-7b1d-4c2e-9a51-0d6e8f4b2c10"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/sessions/{id}", "not-a-uuid"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
        command("b9d5f3c2-6a4e-4f8b-87da-2e3f4a5b6c7d", "{\"text\":\"공격\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CHARACTER_NOT_FOUND"));
    }

    @Test
    void 큐가_100개면_429() throws Exception {
        for (int i = 0; i < 100; i++) {
            command(playerId, "{\"text\":\"방어\"}").andExpect(status().isCreated());
        }
        command(playerId, "{\"text\":\"방어\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("QUEUE_FULL"));
    }

    @Test
    void 초기화하면_큐를_비우고_시작_위치로_돌아간다() throws Exception {
        command(playerId, "{\"text\":\"앞으로 가\"}");
        command(playerId, "{\"text\":\"공격\"}");

        mockMvc.perform(post("/api/v1/sessions/{id}/reset", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.characters", hasSize(2)))
                .andExpect(jsonPath("$.data.characters[0].id").value(playerId))
                .andExpect(jsonPath("$.data.characters[0].y").value(7))
                .andExpect(jsonPath("$.data.characters[0].state").value("IDLE"))
                .andExpect(jsonPath("$.data.characters[0].queue", hasSize(0)));
    }

    @Test
    void 세션을_삭제하면_204이고_이후에는_404() throws Exception {
        mockMvc.perform(delete("/api/v1/sessions/{id}", sessionId))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mockMvc.perform(get("/api/v1/sessions/{id}", sessionId))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/sessions/{id}", sessionId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void 마지막_입력_후_30분이_지나면_만료된다() throws Exception {
        gameSessionService.expireIdleSessions(Instant.now().plus(Duration.ofMinutes(29)));
        mockMvc.perform(get("/api/v1/sessions/{id}", sessionId)).andExpect(status().isOk());

        gameSessionService.expireIdleSessions(Instant.now().plus(Duration.ofMinutes(31)));
        mockMvc.perform(get("/api/v1/sessions/{id}", sessionId)).andExpect(status().isNotFound());
    }

    @Test
    void CORS는_localhost_5173만_허용한다() throws Exception {
        mockMvc.perform(options("/api/v1/sessions")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mockMvc.perform(options("/api/v1/sessions")
                        .header("Origin", "http://localhost:8000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 없는_경로는_404() throws Exception {
        mockMvc.perform(get("/api/v1/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    private ResultActions command(String characterId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/sessions/{sid}/characters/{cid}/commands", sessionId, characterId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
