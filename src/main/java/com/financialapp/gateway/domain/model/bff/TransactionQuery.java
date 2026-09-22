package com.financialapp.gateway.domain.model.bff;

import java.time.LocalDate;
import java.util.List;

public record TransactionQuery(
        int page,
        int size,
        List<String> categories,
        List<String> accounts,
        String method,
        String query,
        LocalDate from,
        LocalDate to) {

    public static final String UNCATEGORISED = "none";

    public TransactionQuery {
        page = Math.max(page, 0);
        size = size > 0 ? size : 20;
        categories = categories != null ? List.copyOf(categories) : List.of();
        accounts = accounts != null ? List.copyOf(accounts) : List.of();
        method = method != null && !method.isBlank() ? method.trim() : null;
        query = query != null && !query.isBlank() ? query.trim() : null;
    }

    public boolean onlyUncategorised() {
        return categories.contains(UNCATEGORISED);
    }

    public List<String> categoryIds() {
        return categories.stream().filter(c -> !UNCATEGORISED.equals(c)).toList();
    }
}
