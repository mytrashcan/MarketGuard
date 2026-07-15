package com.marketguard.dashboard;

/** Stable public error envelope that never includes internal exception details. */
public record ApiError(String code, String message) {
}
