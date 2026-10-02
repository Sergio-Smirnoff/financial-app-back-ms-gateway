package com.financialapp.gateway.domain.model.bff;

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

    public static HistoryRange fromParam(String raw) {
        if (raw == null) {
            return ONE_MONTH;
        }
        String trimmed = raw.trim();
        for (HistoryRange range : values()) {
            if (range.selector.equalsIgnoreCase(trimmed)) {
                return range;
            }
        }
        return ONE_MONTH;
    }
}
