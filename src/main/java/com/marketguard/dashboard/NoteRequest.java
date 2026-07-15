package com.marketguard.dashboard;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NoteRequest(@NotBlank @Size(max = 2_000) String note) {
}
