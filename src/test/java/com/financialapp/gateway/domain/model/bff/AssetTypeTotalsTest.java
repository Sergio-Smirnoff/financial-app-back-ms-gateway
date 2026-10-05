package com.financialapp.gateway.domain.model.bff;

import com.financialapp.gateway.domain.model.currency.Currency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssetTypeTotalsTest {

    @Test
    void plusMergesValueCostAndPnlPerCurrencyAndAddsTheCounts() {
        AssetTypeTotals ars = new AssetTypeTotals(
                CurrencyAmounts.none().plus(Currency.ARS, new BigDecimal("1000")),
                CurrencyAmounts.none().plus(Currency.ARS, new BigDecimal("800")),
                CurrencyAmounts.none().plus(Currency.ARS, new BigDecimal("199.99")), 2);
        AssetTypeTotals usd = new AssetTypeTotals(
                CurrencyAmounts.none().plus(Currency.USD, new BigDecimal("100")),
                CurrencyAmounts.none().plus(Currency.USD, new BigDecimal("120")),
                CurrencyAmounts.none().plus(Currency.USD, new BigDecimal("-20.01")), 1);

        AssetTypeTotals merged = ars.plus(usd);

        assertThat(merged.marketValue().amounts())
                .containsEntry(Currency.ARS, new BigDecimal("1000"))
                .containsEntry(Currency.USD, new BigDecimal("100"));
        assertThat(merged.cost().amounts())
                .containsEntry(Currency.ARS, new BigDecimal("800"))
                .containsEntry(Currency.USD, new BigDecimal("120"));
        assertThat(merged.pnl().amounts())
                .containsEntry(Currency.ARS, new BigDecimal("199.99"))
                .containsEntry(Currency.USD, new BigDecimal("-20.01"));
        assertThat(merged.count()).isEqualTo(3);
    }

    @Test
    void rejectsMissingAmountsAndNegativeCounts() {
        assertThatThrownBy(() -> new AssetTypeTotals(null, CurrencyAmounts.none(), CurrencyAmounts.none(), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AssetTypeTotals(CurrencyAmounts.none(), null, CurrencyAmounts.none(), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AssetTypeTotals(CurrencyAmounts.none(), CurrencyAmounts.none(), null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AssetTypeTotals(
                CurrencyAmounts.none(), CurrencyAmounts.none(), CurrencyAmounts.none(), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
