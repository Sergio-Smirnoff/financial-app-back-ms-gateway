package com.financialapp.gateway.domain.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PercentagesTest {

    @Test
    void aShareIsRoundedToTwoDecimals() {
        assertThat(Percentages.percentOf(new BigDecimal("486014.29"), new BigDecimal("10175878.31")))
                .isEqualByComparingTo("4.78");
    }

    @Test
    void aNegativePartGivesANegativeShare() {
        assertThat(Percentages.percentOf(new BigDecimal("-81601.86"), new BigDecimal("986380.86")))
                .isEqualByComparingTo("-8.27");
    }

    @Test
    void aZeroOrMissingOperandGivesZero() {
        assertThat(Percentages.percentOf(BigDecimal.TEN, BigDecimal.ZERO)).isEqualByComparingTo("0");
        assertThat(Percentages.percentOf(BigDecimal.TEN, null)).isEqualByComparingTo("0");
        assertThat(Percentages.percentOf(null, BigDecimal.TEN)).isEqualByComparingTo("0");
    }
}
