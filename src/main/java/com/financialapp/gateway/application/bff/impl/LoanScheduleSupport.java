package com.financialapp.gateway.application.bff.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class LoanScheduleSupport {

    public record ParsedInstallment(Long id, Integer number, BigDecimal amount, LocalDate dueDate, Boolean paid, LocalDate paidDate) {}

    private LoanScheduleSupport() {}

    public static List<ParsedInstallment> parse(List<Map<String, Object>> raw) {
        return raw.stream()
                .map(m -> new ParsedInstallment(
                        asLong(m.get("id")),
                        asInt(m.get("installmentNumber")),
                        asDecimal(m.get("amount")),
                        asDate(m.get("dueDate")),
                        Boolean.TRUE.equals(m.get("paid")),
                        asDate(m.get("paidDate"))))
                .sorted(Comparator.comparing(ParsedInstallment::number))
                .toList();
    }

    public static BigDecimal outstanding(List<ParsedInstallment> schedule) {
        return schedule.stream()
                .filter(i -> !Boolean.TRUE.equals(i.paid()))
                .map(ParsedInstallment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static Optional<ParsedInstallment> nextUnpaid(List<ParsedInstallment> schedule) {
        return schedule.stream()
                .filter(i -> !Boolean.TRUE.equals(i.paid()))
                .min(Comparator.comparing(ParsedInstallment::dueDate));
    }

    private static Long asLong(Object v) { return v == null ? null : Long.valueOf(String.valueOf(v)); }

    private static Integer asInt(Object v) { return v == null ? null : Integer.valueOf(String.valueOf(v)); }

    private static BigDecimal asDecimal(Object v) { return v == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(v)); }

    private static LocalDate asDate(Object v) { return v == null ? null : LocalDate.parse(String.valueOf(v)); }
}
