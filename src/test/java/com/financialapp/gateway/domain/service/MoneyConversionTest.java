package com.financialapp.gateway.domain.service;

import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.model.currency.DisplayMoney;
import com.financialapp.gateway.domain.model.currency.FxRate;
import com.financialapp.gateway.domain.model.currency.FxRateMode;
import com.financialapp.gateway.domain.model.currency.ManualCurrencyRate;
import com.financialapp.gateway.domain.model.currency.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyConversionTest {

    private final MoneyConversion conversion = new MoneyConversion();
    private final LocalDate today = LocalDate.of(2026, 7, 30);
    // Buy 1000.00 ARS per USD, Sell 1050.00 ARS per USD
    private final FxRate spreadRate = new FxRate(today, FxRateMode.MEP, new BigDecimal("1000.00"), new BigDecimal("1050.00"));

    @Test
    void passthrough_returns_same_amount_and_currency_without_consulting_rates() {
        Money money = new Money(new BigDecimal("500.00"), Currency.ARS);
        DisplayMoney result = conversion.convert(money, Currency.ARS, null, null);

        assertThat(result.currency()).isEqualTo(Currency.ARS);
        assertThat(result.amount()).isEqualTo(new BigDecimal("500.00"));
    }

    @Test
    void ars_to_usd_uses_sell_rate() {
        // 10500 ARS / 1050.00 sell = 10.00 USD
        Money money = new Money(new BigDecimal("10500.00"), Currency.ARS);
        DisplayMoney result = conversion.convert(money, Currency.USD, spreadRate, null);

        assertThat(result.currency()).isEqualTo(Currency.USD);
        assertThat(result.amount()).isEqualTo(new BigDecimal("10.00"));
    }

    @Test
    void usd_to_ars_uses_buy_rate() {
        // 10.00 USD * 1000.00 buy = 10000.00 ARS
        Money money = new Money(new BigDecimal("10.00"), Currency.USD);
        DisplayMoney result = conversion.convert(money, Currency.ARS, spreadRate, null);

        assertThat(result.currency()).isEqualTo(Currency.ARS);
        assertThat(result.amount()).isEqualTo(new BigDecimal("10000.00"));
    }

    @Test
    void eur_to_ars_via_manual_rate() {
        // 100 EUR * 1200.00 ratePerArs = 120000.00 ARS
        Money money = new Money(new BigDecimal("100.00"), Currency.EUR);
        ManualCurrencyRate eurRate = new ManualCurrencyRate(Currency.EUR, new BigDecimal("1200.00"));

        DisplayMoney result = conversion.convert(money, Currency.ARS, null, eurRate);

        assertThat(result.currency()).isEqualTo(Currency.ARS);
        assertThat(result.amount()).isEqualTo(new BigDecimal("120000.00"));
    }

    @Test
    void eur_to_usd_composed_manual_then_automatic() {
        // 100 EUR * 1050.00 manual = 105000.00 ARS -> / 1050.00 sell = 100.00 USD
        Money money = new Money(new BigDecimal("100.00"), Currency.EUR);
        ManualCurrencyRate eurRate = new ManualCurrencyRate(Currency.EUR, new BigDecimal("1050.00"));

        DisplayMoney result = conversion.convert(money, Currency.USD, spreadRate, eurRate);

        assertThat(result.currency()).isEqualTo(Currency.USD);
        assertThat(result.amount()).isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    void null_ars_usd_rate_on_ars_to_usd_ask_returns_unconvertible_original() {
        Money money = new Money(new BigDecimal("1000.00"), Currency.ARS);
        DisplayMoney result = conversion.convert(money, Currency.USD, null, null);

        assertThat(result.currency()).isEqualTo(Currency.ARS);
        assertThat(result.amount()).isEqualTo(new BigDecimal("1000.00"));
    }

    @Test
    void eur_without_manual_rate_returns_unconvertible_original() {
        Money money = new Money(new BigDecimal("100.00"), Currency.EUR);
        DisplayMoney result = conversion.convert(money, Currency.USD, spreadRate, null);

        assertThat(result.currency()).isEqualTo(Currency.EUR);
        assertThat(result.amount()).isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    void applies_scale_2_half_even_rounding() {
        // 1000 ARS / 1050.00 sell = 0.95238... -> 0.95 USD
        Money money = new Money(new BigDecimal("1000.00"), Currency.ARS);
        DisplayMoney result = conversion.convert(money, Currency.USD, spreadRate, null);

        assertThat(result.currency()).isEqualTo(Currency.USD);
        assertThat(result.amount()).isEqualTo(new BigDecimal("0.95"));
    }
}
