package com.marketguard.detection.casework;

public enum CaseStatus {
    NEW,
    REVIEWING,
    WATCHING,
    DISMISSED,
    ESCALATED,
    CLOSED;

    public boolean isTerminal() {
        return this == DISMISSED || this == CLOSED;
    }
}
