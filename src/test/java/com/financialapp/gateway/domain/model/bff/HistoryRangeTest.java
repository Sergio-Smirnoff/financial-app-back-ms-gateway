package com.financialapp.gateway.domain.model.bff;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class HistoryRangeTest {

    @ParameterizedTest
    @CsvSource({"1M,30", "3M,90", "1A,365", "1a,365", " 3m ,90"})
    void eachSelectorMapsToItsDays(String raw, int days) {
        assertThat(HistoryRange.fromParam(raw).days()).isEqualTo(days);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"6M", "ALL", "1Y", "30"})
    void anythingElseFallsBackToOneMonth(String raw) {
        assertThat(HistoryRange.fromParam(raw)).isEqualTo(HistoryRange.ONE_MONTH);
    }
}
