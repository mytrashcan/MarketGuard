package com.marketguard.collector.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OAuth2 토큰 발급 응답.
 * 필드명은 공식 문서의 응답(snake_case)에 맞춰 매핑한다.
 */
public record TokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn
) {
}
