package com.sidaesekki.Say2Play.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sidaesekki.Say2Play.game.domain.ActionType;
import com.sidaesekki.Say2Play.global.error.ErrorCode;
import com.sidaesekki.Say2Play.global.error.GameException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * aiserver를 HTTP로 호출한다(erd.md "AI 호출 규칙").
 *
 * <p>아래 스펙은 aiserver에 HTTP 서버가 생기기 전까지 쓰는 <b>임시(가짜) 스펙</b>이다. 실제 스펙이 정해지면
 * {@link DecideRequest}, {@link DecideResponse}와 경로만 바꾸면 된다.
 *
 * <pre>
 * POST {base-url}/v1/decide
 * Content-Type: application/json
 *
 * { "text": "오른쪽으로 한 발짝 가볼까" }
 *
 * 200 OK
 * {
 *   "choice": "move_right",
 *   "probabilities": { "move_right": 0.93, ... },
 *   "confidence": 0.88,
 *   "isCommand": true,
 *   "commandScore": 0.81,
 *   "model": "jev-1.13.0"
 * }
 * </pre>
 *
 * v1은 {@code choice}만 쓴다. 재시도는 하지 않는다.
 */
public class HttpActionResolver implements ActionResolver {

    static final String DECIDE_PATH = "/v1/decide";

    private final RestClient restClient;

    public HttpActionResolver(URI baseUrl, Duration timeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl.toString())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public ActionType resolve(String text) {
        DecideResponse response;
        try {
            response = restClient.post()
                    .uri(DECIDE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new DecideRequest(text))
                    .retrieve()
                    .body(DecideResponse.class);
        } catch (ResourceAccessException e) {
            throw new GameException(isTimeout(e) ? ErrorCode.AI_TIMEOUT : ErrorCode.AI_UNAVAILABLE, e);
        } catch (RestClientException e) {
            throw new GameException(ErrorCode.AI_UNAVAILABLE, e);
        }
        if (response == null || response.choice() == null) {
            throw new GameException(ErrorCode.AI_UNAVAILABLE);
        }
        return ActionType.fromKey(response.choice())
                .orElseThrow(() -> new GameException(ErrorCode.AI_UNAVAILABLE));
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    record DecideRequest(String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DecideResponse(String choice) {
    }
}
