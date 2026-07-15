package com.marketguard.application;

public class StaleCaseVersionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public StaleCaseVersionException(Long id) {
        super("case version conflict: " + id);
    }
}
