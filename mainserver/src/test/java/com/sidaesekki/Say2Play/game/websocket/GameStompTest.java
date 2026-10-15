package com.sidaesekki.Say2Play.game.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "say2play.session.reconnect-grace=1s",
        "say2play.session.round-reset-delay=500ms"})
class GameStompTest {

    private static final long WAIT_SECONDS = 5;

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private final List<StompSession> opened = new ArrayList<>();
    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new CompositeMessageConverter(
                List.of(new StringMessageConverter(), new JacksonJsonMessageConverter())));
    }

    @AfterEach
    void tearDown() {
        opened.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
    }

    @Test
    void 상대가_참가하면_새_판이_시작되고_두_사람이_같은_이벤트를_받는다() throws Exception {
        Player host = create("MULTI");
        connect(host);

        Player guest = join(host.sessionId);
        Map<?, ?> joined = host.next("PLAYER_JOINED");
        assertThat(read(joined, "$.data.controls[0]")).isEqualTo("ENEMY");
        assertThat(read(joined, "$.data.session.status")).isEqualTo("PLAYING");

        connect(guest);
        Map<?, ?> connection = host.next("PLAYER_CONNECTION");
        assertThat(read(connection, "$.data.controls[0]")).isEqualTo("ENEMY");
        assertThat(read(connection, "$.data.connected")).isEqualTo(true);

        assertThat(command(host, host.playerId, "공격").statusCode()).isEqualTo(201);
        assertThat(command(guest, host.enemyId, "방어").statusCode()).isEqualTo(201);

        for (Player each : List.of(host, guest)) {
            List<Object> started = List.of(
                    read(each.next("ACTION_STARTED"), "$.data.action.actionType"),
                    read(each.next("ACTION_STARTED"), "$.data.action.actionType"));
            assertThat(started).containsExactlyInAnyOrder("attack", "defend");
        }
    }

    @Test
    void 한쪽이_쓰러지면_ROUND_OVER_뒤_자동으로_새_판이_시작된다() throws Exception {
        Player host = create("MULTI");
        connect(host);
        Player guest = join(host.sessionId);
        connect(guest);

        for (int i = 0; i < 5; i++) {
            command(host, host.playerId, "앞으로 가");
        }
        for (int i = 0; i < 10; i++) {
            command(host, host.playerId, "공격");
        }

        Map<?, ?> roundOver = guest.next("ROUND_OVER", 15);
        assertThat(read(roundOver, "$.data.winnerId")).isEqualTo(host.playerId);
        assertThat(read(roundOver, "$.data.loserId")).isEqualTo(host.enemyId);
        assertThat(read(roundOver, "$.data.delayMs")).isEqualTo(500);
        assertThat(command(guest, host.enemyId, "공격").body()).contains("ROUND_OVER");
        assertThat(command(host, host.playerId, "공격").body()).contains("ROUND_OVER");

        Map<?, ?> reset = guest.next("SESSION_RESET");
        assertThat(read(reset, "$.data.session.status")).isEqualTo("PLAYING");
        assertThat(read(reset, "$.data.characters[1].hp")).isEqualTo(100);
        assertThat(read(reset, "$.data.characters[1].state")).isEqualTo("IDLE");
        assertThat(read(reset, "$.data.characters[0].y")).isEqualTo(7);
        assertThat(command(guest, host.enemyId, "공격").statusCode()).isEqualTo(201);
    }

    @Test
    void 연결이_끊기고_유예_시간이_지나면_나간_것으로_보고_남은_사람은_다시_기다린다() throws Exception {
        Player host = create("MULTI");
        connect(host);
        Player guest = join(host.sessionId);
        connect(guest);
        host.next("PLAYER_CONNECTION");

        guest.stomp.disconnect();

        Map<?, ?> lost = host.next("PLAYER_CONNECTION");
        assertThat(read(lost, "$.data.connected")).isEqualTo(false);
        Map<?, ?> left = host.next("PLAYER_LEFT");
        assertThat(read(left, "$.data.reason")).isEqualTo("DISCONNECTED");
        assertThat(read(left, "$.data.controls[0]")).isEqualTo("ENEMY");
        assertThat(read(left, "$.data.session.status")).isEqualTo("WAITING");
        assertThat(command(guest, host.enemyId, "공격").statusCode()).isEqualTo(401);
    }

    @Test
    void 유예_시간_안에_다시_연결하면_자리가_유지된다() throws Exception {
        Player host = create("MULTI");
        connect(host);
        Player guest = join(host.sessionId);
        connect(guest);

        guest.stomp.disconnect();
        connect(guest);
        Thread.sleep(1500);

        assertThat(command(guest, host.enemyId, "방어").statusCode()).isEqualTo(201);
        assertThat(host.drain()).noneMatch(event -> "PLAYER_LEFT".equals(event.get("type")));
    }

    @Test
    void 나가기를_누르면_상대에게_알리고_나간_사람의_연결을_닫는다() throws Exception {
        Player host = create("MULTI");
        connect(host);
        Player guest = join(host.sessionId);
        connect(guest);

        assertThat(send("POST", "/api/v1/sessions/" + host.sessionId + "/leave", guest.token, null).statusCode())
                .isEqualTo(204);

        Map<?, ?> left = host.next("PLAYER_LEFT");
        assertThat(read(left, "$.data.reason")).isEqualTo("LEFT");
        awaitDisconnected(guest);
        assertThat(host.stomp.isConnected()).isTrue();
    }

    @Test
    void 마지막_사람이_나가면_SESSION_ENDED를_받고_연결이_닫힌다() throws Exception {
        Player solo = create("SOLO");
        connect(solo);

        send("POST", "/api/v1/sessions/" + solo.sessionId + "/leave", solo.token, null);

        assertThat(read(solo.next("SESSION_ENDED"), "$.data.reason")).isEqualTo("CLOSED");
        awaitDisconnected(solo);
    }

    @Test
    void 토큰이_틀리면_연결을_거절한다() throws Exception {
        Player host = create("MULTI");
        Player intruder = new Player(host.sessionId, "wrong-token", null, null);

        BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        stompClient.connectAsync(url(), new WebSocketHttpHeaders(), connectHeaders(intruder),
                new StompSessionHandlerAdapter() {
                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        errors.add(headers.getFirst("code") + ": " + headers.getFirst("message"));
                    }
                });

        assertThat(errors.poll(WAIT_SECONDS, TimeUnit.SECONDS)).startsWith("INVALID_PLAYER_TOKEN: ").contains("토큰");
    }

    @Test
    void 다른_세션의_토픽은_구독할_수_없다() throws Exception {
        Player mine = create("SOLO");
        Player other = create("SOLO");
        BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        StompSession session = stompClient.connectAsync(url(), new WebSocketHttpHeaders(), connectHeaders(mine),
                new StompSessionHandlerAdapter() {
                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        errors.add(headers.getFirst("code") + ": " + headers.getFirst("message"));
                    }
                }).get(WAIT_SECONDS, TimeUnit.SECONDS);
        opened.add(session);

        session.subscribe("/topic/sessions/" + other.sessionId, new StompSessionHandlerAdapter() {
        });

        assertThat(errors.poll(WAIT_SECONDS, TimeUnit.SECONDS)).startsWith("STOMP_NOT_ALLOWED: ").contains("구독");
    }

    private Player create(String mode) throws Exception {
        return Player.of(send("POST", "/api/v1/sessions", null, "{\"mode\":\"" + mode + "\"}").body());
    }

    private Player join(String sessionId) throws Exception {
        return Player.of(send("POST", "/api/v1/sessions/" + sessionId + "/join", null, null).body());
    }

    private void connect(Player player) throws Exception {
        StompSession session = stompClient.connectAsync(url(), new WebSocketHttpHeaders(), connectHeaders(player),
                new StompSessionHandlerAdapter() {
                }).get(WAIT_SECONDS, TimeUnit.SECONDS);
        opened.add(session);
        session.subscribe("/topic/sessions/" + player.sessionId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                player.events.add((Map<?, ?>) payload);
            }
        });
        player.stomp = session;
        // SUBSCRIBE는 비동기로 처리되므로 브로커에 등록될 시간을 준다.
        Thread.sleep(300);
    }

    private HttpResponse<String> command(Player player, String characterId, String text) throws Exception {
        return send("POST", "/api/v1/sessions/" + player.sessionId + "/characters/" + characterId + "/commands",
                player.token, "{\"text\":\"" + text + "\"}");
    }

    private HttpResponse<String> send(String method, String path, String token, String json) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json));
        if (token != null) {
            request.header("X-Player-Token", token);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String url() {
        return "ws://localhost:" + port + "/ws";
    }

    private static StompHeaders connectHeaders(Player player) {
        StompHeaders headers = new StompHeaders();
        headers.add(StompAuthInterceptor.SESSION_ID_HEADER, player.sessionId);
        headers.add(StompAuthInterceptor.PLAYER_TOKEN_HEADER, player.token);
        return headers;
    }

    private static void awaitDisconnected(Player player) throws InterruptedException {
        for (int i = 0; i < 50 && player.stomp.isConnected(); i++) {
            Thread.sleep(100);
        }
        assertThat(player.stomp.isConnected()).as("server closed the connection").isFalse();
    }

    private static Object read(Map<?, ?> event, String path) {
        return JsonPath.read(event, path);
    }

    private static final class Player {
        final String sessionId;
        final String token;
        final String playerId;
        final String enemyId;
        final BlockingQueue<Map<?, ?>> events = new LinkedBlockingQueue<>();
        StompSession stomp;

        Player(String sessionId, String token, String playerId, String enemyId) {
            this.sessionId = sessionId;
            this.token = token;
            this.playerId = playerId;
            this.enemyId = enemyId;
        }

        static Player of(String body) {
            return new Player(JsonPath.read(body, "$.data.session.id"), JsonPath.read(body, "$.data.me.playerToken"),
                    JsonPath.read(body, "$.data.characters[0].id"), JsonPath.read(body, "$.data.characters[1].id"));
        }

        Map<?, ?> next(String type) throws InterruptedException {
            return next(type, WAIT_SECONDS);
        }

        // 원하는 종류가 올 때까지 다른 이벤트는 건너뛴다.
        Map<?, ?> next(String type, long seconds) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
            while (System.nanoTime() < deadline) {
                Map<?, ?> event = events.poll(100, TimeUnit.MILLISECONDS);
                if (event != null && type.equals(event.get("type"))) {
                    return event;
                }
            }
            throw new AssertionError("No " + type + " event within " + seconds + "s");
        }

        List<Map<?, ?>> drain() {
            List<Map<?, ?>> drained = new ArrayList<>();
            events.drainTo(drained);
            return drained;
        }
    }
}
