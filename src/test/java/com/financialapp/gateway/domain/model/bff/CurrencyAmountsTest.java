package com.financialapp.gateway.domain.model.bff;

import com.financialapp.gateway.domain.model.currency.Currency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CurrencyAmountsTest {

    @Test
    void plusMergesPerCurrency() {
        CurrencyAmounts amounts = CurrencyAmounts.none()
                .plus(Currency.ARS, new BigDecimal("100.00"))
                .plus(Currency.USD, new BigDecimal("2.00"))
                .plus(Currency.ARS, new BigDecimal("50.00"));

        assertThat(amounts.amounts())
                .containsEntry(Currency.ARS, new BigDecimal("150.00"))
                .containsEntry(Currency.USD, new BigDecimal("2.00"));
    }

    @Test
    void plusAnotherAddsEveryCurrency() {
        CurrencyAmounts left = CurrencyAmounts.none().plus(Currency.ARS, BigDecimal.ONE);
        CurrencyAmounts right = CurrencyAmounts.none().plus(Currency.ARS, BigDecimal.ONE).plus(Currency.USD, BigDecimal.ONE);

        assertThat(left.plus(right).amounts())
                .containsEntry(Currency.ARS, new BigDecimal("2"))
                .containsEntry(Currency.USD, BigDecimal.ONE);
    }

    @Test
    void onlyANonZeroForeignAmountNeedsAUsdRate() {
        assertThat(CurrencyAmounts.none().plus(Currency.ARS, BigDecimal.TEN).needsUsdRate()).isFalse();
        assertThat(CurrencyAmounts.none().plus(Currency.USD, BigDecimal.ZERO).needsUsdRate()).isFalse();
        assertThat(CurrencyAmounts.none().plus(Currency.USD, BigDecimal.ONE).needsUsdRate()).isTrue();
    }
}
