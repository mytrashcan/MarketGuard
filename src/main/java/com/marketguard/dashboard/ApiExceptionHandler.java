package com.marketguard.dashboard;

import com.marketguard.collector.client.TossApiException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.validation.ConstraintViolationException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

/** Maps failures to safe, stable HTTP responses for the dashboard API. */
@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler {

    @ExceptionHandler({
            ConstraintViolationException.class,
            HandlerMethodValidationException.class,
            MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class
    })
    ResponseEntity<ApiError> badRequest(Exception ignored) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", "The request is invalid"));
    }

    @ExceptionHandler(TossApiException.class)
    ResponseEntity<ApiError> upstream(TossApiException exception) {
        HttpStatus status = exception.isRetryable() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status)
                .body(new ApiError("MARKET_DATA_UNAVAILABLE", "Market data is temporarily unavailable"));
    }

    @ExceptionHandler({ResourceAccessException.class, CallNotPermittedException.class, RequestNotPermitted.class})
    ResponseEntity<ApiError> transientUpstream(Exception ignored) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("MARKET_DATA_UNAVAILABLE", "Market data is temporarily unavailable"));
    }

    @ExceptionHandler(RestClientException.class)
    ResponseEntity<ApiError> invalidUpstreamResponse(RestClientException ignored) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError("INVALID_MARKET_DATA_RESPONSE", "The market data response was invalid"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception) {
        String errorId = UUID.randomUUID().toString();
        log.error("Unhandled API failure id={} type={}", errorId, exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "The request could not be completed (error " + errorId + ")"));
    }
}
