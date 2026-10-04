package com.financialapp.gateway.domain.model.bff;

import java.util.Arrays;
import java.util.Optional;

public enum HistoryRange {
    ONE_MONTH("1M", 30),
    THREE_MONTHS("3M", 90),
    ONE_YEAR("1A", 365);

    private final String selector;
    private final int days;

    HistoryRange(String selector, int days) {
        this.selector = selector;
        this.days = days;
    }

    public int days() {
        return days;
    }

    public static Optional<HistoryRange> fromSelector(String selector) {
        String trimmed = selector.trim();
        return Arrays.stream(values())
                .filter(range -> range.selector.equalsIgnoreCase(trimmed))
                .findFirst();
    }
}
