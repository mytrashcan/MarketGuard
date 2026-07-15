package com.marketguard.dashboard;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    static <T> PageView<T> from(Page<T> value) {
        return new PageView<>(value.getContent(), value.getNumber(), value.getSize(),
                value.getTotalElements(), value.getTotalPages());
    }
}
