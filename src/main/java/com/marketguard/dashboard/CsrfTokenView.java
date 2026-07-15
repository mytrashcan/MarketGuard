package com.marketguard.dashboard;

public record CsrfTokenView(String token, String headerName, String parameterName) {
}
