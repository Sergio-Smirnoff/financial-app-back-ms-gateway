package com.financialapp.gateway.application.bff;

import com.financialapp.gateway.application.bff.impl.LoanScheduleSupport;
import com.financialapp.gateway.application.bff.impl.LoanScheduleSupport.LoanWithSchedule;
import com.financialapp.gateway.application.bff.impl.LoanScheduleSupport.ParsedInstallment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LoanScheduleSupportTest {

    private final List<Map<String, Object>> raw = List.of(
            Map.of("id", 12, "installmentNumber", 2, "amount", "1500.00", "dueDate", "2026-10-10", "paid", false),
            Map.of("id", 11, "installmentNumber", 1, "amount", "1500.00", "dueDate", "2026-09-10", "paid", true, "paidDate", "2026-09-09"),
            Map.of("id", 13, "installmentNumber", 3, "amount", "1500.00", "dueDate", "2026-11-10", "paid", false));

    @Test
    void parses_and_orders_by_installment_number() {
        List<ParsedInstallment> schedule = LoanScheduleSupport.parse(raw);
        assertThat(schedule).extracting(ParsedInstallment::number).containsExactly(1, 2, 3);
        assertThat(schedule.getFirst().paidDate()).isEqualTo(LocalDate.of(2026, 9, 9));
    }

    @Test
    void outstanding_sums_only_unpaid_amounts() {
        assertThat(LoanScheduleSupport.outstanding(LoanScheduleSupport.parse(raw)))
                .isEqualByComparingTo(new BigDecimal("3000.00"));
    }

    @Test
    void next_unpaid_is_earliest_due_unpaid_installment() {
        assertThat(LoanScheduleSupport.nextUnpaid(LoanScheduleSupport.parse(raw)))
                .hasValueSatisfying(i -> assertThat(i.dueDate()).isEqualTo(LocalDate.of(2026, 10, 10)));
    }

    @Test
    void empty_schedule_yields_zero_and_empty() {
        assertThat(LoanScheduleSupport.outstanding(List.of())).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(LoanScheduleSupport.nextUnpaid(List.of())).isEmpty();
    }

    @Test
    void enrich_pairs_every_loan_with_its_parsed_schedule() {
        List<Map<String, Object>> loans = List.of(Map.of("id", 1, "name", "Auto"), Map.of("id", 2, "name", "Casa"));

        List<LoanWithSchedule> enriched = LoanScheduleSupport
                .enrich(loans, id -> CompletableFuture.completedFuture(id == 1L ? raw : List.of()))
                .join();

        assertThat(enriched).hasSize(2);
        assertThat(enriched.getFirst().schedule()).extracting(ParsedInstallment::number).containsExactly(1, 2, 3);
        assertThat(enriched.get(1).schedule()).isEmpty();
    }

    @Test
    void enrich_of_no_loans_fetches_no_schedules() {
        AtomicInteger calls = new AtomicInteger();

        List<LoanWithSchedule> enriched = LoanScheduleSupport.enrich(List.of(), id -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(List.of());
        }).join();

        assertThat(enriched).isEmpty();
        assertThat(calls).hasValue(0);
    }
}
