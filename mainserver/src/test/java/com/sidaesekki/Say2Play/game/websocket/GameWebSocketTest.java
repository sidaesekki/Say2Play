package com.sidaesekki.Say2Play.game.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GameWebSocketTest {

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
    private final BlockingQueue<Integer> closes = new LinkedBlockingQueue<>();
    private WebSocket webSocket;
    private String sessionId;
    private String playerId;
    private String enemyId;

    @BeforeEach
    void connect() throws Exception {
        String body = send("POST", "/api/v1/sessions", null).body();
        sessionId = JsonPath.read(body, "$.data.session.id");
        playerId = JsonPath.read(body, "$.data.characters[0].id");
        enemyId = JsonPath.read(body, "$.data.characters[1].id");
        webSocket = openWebSocket(sessionId);
    }

    @AfterEach
    void close() {
        webSocket.abort();
    }

    @Test
    void 동작의_시작과_완료를_순서대로_보낸다() throws Exception {
        command(playerId, "구르기");
        command(playerId, "오른쪽으로 돌아");

        String started = next();
        assertThat((String) JsonPath.read(started, "$.type")).isEqualTo("ACTION_STARTED");
        assertThat((String) JsonPath.read(started, "$.data.characterId")).isEqualTo(playerId);
        assertThat((String) JsonPath.read(started, "$.data.action.actionType")).isEqualTo("roll");
        assertThat((String) JsonPath.read(started, "$.data.action.status")).isEqualTo("RUNNING");
        assertThat((Integer) JsonPath.read(started, "$.data.durationMs")).isEqualTo(500);
        assertThat((Integer) JsonPath.read(started, "$.data.from.y")).isEqualTo(7);
        assertThat((Integer) JsonPath.read(started, "$.data.to.y")).isEqualTo(6);
        assertThat((String) JsonPath.read(started, "$.data.to.facing")).isEqualTo("N");
        String rollId = JsonPath.read(started, "$.data.action.id");

        String finished = next();
        assertThat((String) JsonPath.read(finished, "$.type")).isEqualTo("ACTION_FINISHED");
        assertThat((String) JsonPath.read(finished, "$.data.actionId")).isEqualTo(rollId);
        assertThat((Integer) JsonPath.read(finished, "$.data.character.y")).isEqualTo(6);
        assertThat((String) JsonPath.read(finished, "$.data.character.state")).isEqualTo("IDLE");
        assertThat((Integer) JsonPath.read(finished, "$.data.queueLength")).isEqualTo(1);

        String turnStarted = next();
        assertThat((String) JsonPath.read(turnStarted, "$.data.action.actionType")).isEqualTo("turn_right");
        assertThat((Object) JsonPath.read(turnStarted, "$.data.action.targetX")).isNull();
        assertThat((String) JsonPath.read(turnStarted, "$.data.to.facing")).isEqualTo("E");

        String turnFinished = next();
        assertThat((String) JsonPath.read(turnFinished, "$.data.character.facing")).isEqualTo("E");
        assertThat((Integer) JsonPath.read(turnFinished, "$.data.queueLength")).isZero();
    }

    @Test
    void 초기화와_종료_이벤트를_보내고_종료하면_연결을_닫는다() throws Exception {
        command(enemyId, "방어");
        assertThat((String) JsonPath.read(next(), "$.type")).isEqualTo("ACTION_STARTED");

        send("POST", "/api/v1/sessions/" + sessionId + "/reset", null);
        String reset = next();
        assertThat((String) JsonPath.read(reset, "$.type")).isEqualTo("SESSION_RESET");
        assertThat((Integer) JsonPath.read(reset, "$.data.characters.length()")).isEqualTo(2);
        assertThat(reset).doesNotContain("\"queue\"");

        send("DELETE", "/api/v1/sessions/" + sessionId, null);
        String ended = next();
        assertThat((String) JsonPath.read(ended, "$.type")).isEqualTo("SESSION_ENDED");
        assertThat((String) JsonPath.read(ended, "$.data.reason")).isEqualTo("CLOSED");
        assertThat(closes.poll(3, TimeUnit.SECONDS)).isEqualTo(1000);

        // 초기화로 취소된 방어 동작의 완료 이벤트는 오지 않는다.
        assertThat(messages.poll(1500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void 없는_세션이면_연결을_404로_거절한다() {
        assertThatThrownBy(() -> openWebSocket(UUID.randomUUID().toString()))
                .isInstanceOf(CompletionException.class)
                .cause()
                .isInstanceOfSatisfying(WebSocketHandshakeException.class,
                        e -> assertThat(e.getResponse().statusCode()).isEqualTo(404));
    }

    @Test
    void 허용된_출처에서만_연결할_수_있다() {
        openWebSocket(sessionId, "http://localhost:5173").abort();

        assertThatThrownBy(() -> openWebSocket(sessionId, "http://localhost:8000"))
                .isInstanceOf(CompletionException.class)
                .cause()
                .isInstanceOfSatisfying(WebSocketHandshakeException.class,
                        e -> assertThat(e.getResponse().statusCode()).isEqualTo(403));
    }

    private WebSocket openWebSocket(String id) {
        return openWebSocket(id, null);
    }

    private WebSocket openWebSocket(String id, String origin) {
        WebSocket.Builder builder = http.newWebSocketBuilder();
        if (origin != null) {
            builder.header("Origin", origin);
        }
        return builder
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/sessions/" + id), new WebSocket.Listener() {
                    private final StringBuilder buffer = new StringBuilder();

                    @Override
                    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                        buffer.append(data);
                        if (last) {
                            messages.add(buffer.toString());
                            buffer.setLength(0);
                        }
                        ws.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                        closes.add(statusCode);
                        return null;
                    }
                })
                .join();
    }

    private String next() throws InterruptedException {
        String message = messages.poll(3, TimeUnit.SECONDS);
        assertThat(message).as("WebSocket message").isNotNull();
        return message;
    }

    private void command(String characterId, String text) throws Exception {
        HttpResponse<String> response = send("POST",
                "/api/v1/sessions/" + sessionId + "/characters/" + characterId + "/commands",
                "{\"text\":\"" + text + "\"}");
        assertThat(response.statusCode()).isEqualTo(201);
    }

    private HttpResponse<String> send(String method, String path, String json) throws Exception {
        HttpRequest.BodyPublisher body = json == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body)
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
