package com.marketguard.application;

public class CaseNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CaseNotFoundException(Long id) {
        super("case not found: " + id);
    }
}
