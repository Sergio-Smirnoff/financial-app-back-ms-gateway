package com.financialapp.gateway.domain.service;

import com.financialapp.gateway.domain.exception.UnconvertibleAmountException;
import com.financialapp.gateway.domain.model.bff.CurrencyAmounts;
import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.model.currency.FxRate;
import com.financialapp.gateway.domain.model.currency.FxRateMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BffMoneyConverterTest {

    private static final Optional<FxRate> MEP = Optional.of(new FxRate(
            LocalDate.of(2026, 9, 28), FxRateMode.MEP, new BigDecimal("1200"), new BigDecimal("1250")));

    @Test
    void arsAmountsNeedNoRate() {
        CurrencyAmounts amounts = CurrencyAmounts.none().plus(Currency.ARS, new BigDecimal("1000.00"));

        assertThat(BffMoneyConverter.toArs(amounts, Optional.empty())).isEqualByComparingTo("1000.00");
    }

    @Test
    void usdAmountsConvertAtTheBuyRate() {
        CurrencyAmounts amounts = CurrencyAmounts.none()
                .plus(Currency.ARS, new BigDecimal("1000000.00"))
                .plus(Currency.USD, new BigDecimal("100.00"));

        assertThat(BffMoneyConverter.toArs(amounts, MEP)).isEqualByComparingTo("1120000.00");
    }

    @Test
    void aUsdAmountWithoutARateIsUnconvertible() {
        CurrencyAmounts amounts = CurrencyAmounts.none().plus(Currency.USD, new BigDecimal("100.00"));

        assertThatThrownBy(() -> BffMoneyConverter.toArs(amounts, Optional.empty()))
                .isInstanceOf(UnconvertibleAmountException.class)
                .hasMessageContaining("USD");
    }

    @Test
    void aCurrencyOtherThanArsOrUsdIsUnconvertible() {
        CurrencyAmounts amounts = CurrencyAmounts.none().plus(Currency.EUR, new BigDecimal("10.00"));

        assertThatThrownBy(() -> BffMoneyConverter.toArs(amounts, MEP))
                .isInstanceOf(UnconvertibleAmountException.class);
    }

    @Test
    void zeroForeignAmountsAreIgnored() {
        CurrencyAmounts amounts = CurrencyAmounts.none().plus(Currency.USD, BigDecimal.ZERO);

        assertThat(BffMoneyConverter.toArs(amounts, Optional.empty())).isEqualByComparingTo("0");
    }
}
