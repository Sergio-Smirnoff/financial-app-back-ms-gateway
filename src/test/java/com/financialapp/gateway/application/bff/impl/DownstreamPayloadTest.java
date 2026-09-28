package com.financialapp.gateway.application.bff.impl;

import com.financialapp.gateway.domain.exception.DownstreamContractViolationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DownstreamPayloadTest {

    private static DownstreamPayload payload(Map<String, Object> fields) {
        return new DownstreamPayload("ms-test summary", fields);
    }

    private static Map<String, Object> withNull(String key) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(key, null);
        return fields;
    }

    @Test
    void decimalReadsStringsAndJsonNumbers() {
        assertThat(payload(Map.of("a", "904779.00")).decimal("a")).isEqualByComparingTo("904779.00");
        assertThat(payload(Map.of("a", 0.5)).decimal("a")).isEqualByComparingTo("0.5");
        assertThat(payload(Map.of("a", 687)).decimal("a")).isEqualByComparingTo("687");
    }

    @Test
    void aMissingRequiredKeyIsAViolationNamingTheSourceAndTheKey() {
        assertThatThrownBy(() -> payload(Map.of("totalMarketValue", 5000)).decimal("totalValue"))
                .isInstanceOf(DownstreamContractViolationException.class)
                .hasMessageContaining("ms-test summary")
                .hasMessageContaining("totalValue")
                .hasMessageContaining("is missing");
    }

    @Test
    void aJsonNullCountsAsMissing() {
        assertThatThrownBy(() -> payload(withNull("amount")).decimal("amount"))
                .isInstanceOf(DownstreamContractViolationException.class);
        assertThat(payload(withNull("note")).optionalText("note")).isEmpty();
        assertThat(payload(withNull("note")).textOr("note", "")).isEmpty();
    }

    @Test
    void decimalOrZeroDefaultsOnlyWhenAbsent() {
        assertThat(payload(Map.of()).decimalOrZero("balance")).isEqualByComparingTo("0");
        assertThatThrownBy(() -> payload(Map.of("balance", "n/a")).decimalOrZero("balance"))
                .isInstanceOf(DownstreamContractViolationException.class)
                .hasMessageContaining("is not a number");
    }

    @Test
    void longValueRejectsFractions() {
        assertThat(payload(Map.of("id", 101)).longValue("id")).isEqualTo(101L);
        assertThat(payload(Map.of("id", "101")).longValue("id")).isEqualTo(101L);
        assertThatThrownBy(() -> payload(Map.of("id", "1.5")).longValue("id"))
                .isInstanceOf(DownstreamContractViolationException.class);
    }

    @Test
    void dateAcceptsDatesAndLocalDateTimes() {
        assertThat(payload(Map.of("date", "2026-09-05")).date("date")).isEqualTo(LocalDate.of(2026, 9, 5));
        assertThat(payload(Map.of("createdAt", "2026-09-01T13:45:10")).date("createdAt"))
                .isEqualTo(LocalDate.of(2026, 9, 1));
        assertThatThrownBy(() -> payload(Map.of("date", "05/09/2026")).date("date"))
                .isInstanceOf(DownstreamContractViolationException.class);
    }

    @Test
    void instantReadsLocalDateTimesAsUtcAndKeepsExplicitOffsets() {
        Instant expected = Instant.parse("2026-09-28T11:15:00Z");
        assertThat(payload(Map.of("at", "2026-09-28T11:15:00")).instant("at")).isEqualTo(expected);
        assertThat(payload(Map.of("at", "2026-09-28T11:15:00Z")).instant("at")).isEqualTo(expected);
        assertThat(payload(Map.of("at", "2026-09-28T08:15:00-03:00")).instant("at")).isEqualTo(expected);
    }

    @Test
    void flagRequiresABooleanAndFlagOrFallsBack() {
        assertThat(payload(Map.of("matches", true)).flag("matches")).isTrue();
        assertThatThrownBy(() -> payload(Map.of("matches", "yes")).flag("matches"))
                .isInstanceOf(DownstreamContractViolationException.class);
        assertThat(payload(Map.of()).flagOr("colorForAmounts", true)).isTrue();
        assertThat(payload(Map.of("colorForAmounts", false)).flagOr("colorForAmounts", true)).isFalse();
    }

    @Test
    void listsAndObjectsNestTheSourceName() {
        DownstreamPayload summary = payload(Map.of("byCurrency", List.of(Map.of("currency", "ARS"))));

        assertThat(summary.list("byCurrency")).singleElement()
                .satisfies(bucket -> assertThat(bucket.text("currency")).isEqualTo("ARS"));
        assertThatThrownBy(() -> summary.list("byCurrency").get(0).decimal("totalValue"))
                .hasMessageContaining("ms-test summary.byCurrency");
        assertThat(payload(Map.of()).listOrEmpty("subcategories")).isEmpty();
        assertThatThrownBy(() -> payload(Map.of("byCurrency", "ARS")).list("byCurrency"))
                .isInstanceOf(DownstreamContractViolationException.class)
                .hasMessageContaining("is not a list");
        assertThat(payload(Map.of("reconciliation", Map.of("matches", true))).optionalObject("reconciliation"))
                .hasValueSatisfying(reconciliation -> assertThat(reconciliation.flag("matches")).isTrue());
    }

    @Test
    void amountOrZeroParsesTypedViewAmounts() {
        assertThat(DownstreamPayload.amountOrZero("25000.00")).isEqualByComparingTo("25000.00");
        assertThat(DownstreamPayload.amountOrZero(null)).isEqualByComparingTo("0");
        assertThatThrownBy(() -> DownstreamPayload.amountOrZero("abc"))
                .isInstanceOf(DownstreamContractViolationException.class);
    }

    @Test
    void rowsWrapEveryMapAndTolerateANullList() {
        assertThat(DownstreamPayload.rows("ms-test rows", List.of(Map.of("id", 1), Map.of("id", 2)))).hasSize(2);
        assertThat(DownstreamPayload.rows("ms-test rows", null)).isEmpty();
        assertThat(payload(Map.of()).isEmpty()).isTrue();
    }
}
