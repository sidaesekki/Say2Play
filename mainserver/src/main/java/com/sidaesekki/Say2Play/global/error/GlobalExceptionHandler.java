package com.sidaesekki.Say2Play.global.error;

import com.sidaesekki.Say2Play.global.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(GameException.class)
    public ResponseEntity<ApiResponse<Void>> handleGame(GameException e) {
        if (e.errorCode().status().is5xxServerError()) {
            log.warn("{}: {}", e.errorCode(), e.getMessage(), e.getCause());
        }
        return toResponse(e.errorCode());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException e) {
        return toResponse(ErrorCode.VALIDATION_ERROR);
    }

    // UUID 형식이 아닌 ID는 존재하지 않는 ID와 똑같이 취급한다.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return toResponse("characterId".equals(e.getName())
                ? ErrorCode.CHARACTER_NOT_FOUND
                : ErrorCode.SESSION_NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOthers(Exception e) {
        // 없는 경로, 허용되지 않은 메서드처럼 Spring이 상태 코드를 정해 둔 예외는 그 코드를 그대로 쓴다.
        if (e instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            HttpStatus resolved = HttpStatus.resolve(status.value());
            String code = resolved != null ? resolved.name() : ErrorCode.INTERNAL_ERROR.name();
            return ResponseEntity.status(status).body(ApiResponse.fail(code, e.getMessage()));
        }
        log.error("Unhandled exception", e);
        return toResponse(ErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode code) {
        return ResponseEntity.status(code.status()).body(ApiResponse.fail(code.name(), code.message()));
    }
}
