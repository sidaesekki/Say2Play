package com.sidaesekki.Say2Play.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sidaesekki.Say2Play.game.domain.ActionType;
import com.sidaesekki.Say2Play.global.error.ErrorCode;
import com.sidaesekki.Say2Play.global.error.GameException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpActionResolverTest {

    private static final Duration TIMEOUT = Duration.ofMillis(500);

    private HttpServer server;
    private final AtomicReference<Handler> handler = new AtomicReference<>();
    private final AtomicReference<String> receivedBody = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(HttpActionResolver.DECIDE_PATH, exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            handler.get().handle(exchange);
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void choice를_ActionType으로_바꾼다() {
        handler.set(exchange -> respond(exchange, 200,
                "{\"choice\":\"move_forward\",\"probabilities\":{\"move_forward\":0.9},\"confidence\":0.8,"
                        + "\"isCommand\":true,\"commandScore\":0.7,\"model\":\"jev-1.13.0\"}"));

        assertThat(resolver().resolve("앞으로 가")).isEqualTo(ActionType.MOVE_FORWARD);
        assertThat(receivedBody.get()).isEqualTo("{\"text\":\"앞으로 가\"}");
    }

    @Test
    void Action_Set에_없는_값이면_AI_UNAVAILABLE() {
        handler.set(exchange -> respond(exchange, 200, "{\"choice\":\"jump\"}"));

        assertError(ErrorCode.AI_UNAVAILABLE);
    }

    @Test
    void 서버_오류면_AI_UNAVAILABLE() {
        handler.set(exchange -> respond(exchange, 500, "{}"));

        assertError(ErrorCode.AI_UNAVAILABLE);
    }

    @Test
    void 제한_시간_안에_응답이_없으면_AI_TIMEOUT() {
        handler.set(exchange -> {
            try {
                Thread.sleep(TIMEOUT.toMillis() * 3);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, "{\"choice\":\"attack\"}");
        });

        assertError(ErrorCode.AI_TIMEOUT);
    }

    @Test
    void 연결할_수_없으면_AI_UNAVAILABLE() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        HttpActionResolver resolver = new HttpActionResolver(URI.create("http://localhost:" + closedPort), TIMEOUT);

        assertThatThrownBy(() -> resolver.resolve("공격"))
                .isInstanceOfSatisfying(GameException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.AI_UNAVAILABLE));
    }

    private HttpActionResolver resolver() {
        return new HttpActionResolver(URI.create("http://localhost:" + server.getAddress().getPort()), TIMEOUT);
    }

    private void assertError(ErrorCode expected) {
        assertThatThrownBy(() -> resolver().resolve("공격"))
                .isInstanceOfSatisfying(GameException.class, e -> assertThat(e.errorCode()).isEqualTo(expected));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
