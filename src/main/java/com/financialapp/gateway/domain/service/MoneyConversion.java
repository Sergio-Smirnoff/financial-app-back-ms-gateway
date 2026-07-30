package com.financialapp.gateway.domain.service;

import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.model.currency.DisplayMoney;
import com.financialapp.gateway.domain.model.currency.FxRate;
import com.financialapp.gateway.domain.model.currency.ManualCurrencyRate;
import com.financialapp.gateway.domain.model.currency.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyConversion {

    public DisplayMoney convert(Money money, Currency target, FxRate arsUsdRate, ManualCurrencyRate manualRate) {
        if (money == null || target == null) {
            throw new IllegalArgumentException("money and target currency required");
        }

        Currency source = money.currency();
        BigDecimal amount = money.amount();

        // 1. Passthrough: same currency
        if (source.equals(target)) {
            return new DisplayMoney(amount, target);
        }

        // 2. Automatic ARS <-> USD
        if (isArsUsdPair(source, target)) {
            return convertArsUsdPair(amount, source, target, arsUsdRate, money);
        }

        // 3. Any other pair: route through ARS
        if (!isArsOrUsd(source)) {
            if (manualRate == null || !manualRate.currency().equals(source)) {
                return unconvertible(money);
            }
            BigDecimal amountInArs = amount.multiply(manualRate.ratePerArs()).setScale(2, RoundingMode.HALF_EVEN);
            if (target.equals(Currency.ARS)) {
                return new DisplayMoney(amountInArs, Currency.ARS);
            }
            if (target.equals(Currency.USD)) {
                Money arsMoney = new Money(amountInArs, Currency.ARS);
                DisplayMoney usdResult = convertArsUsdPair(amountInArs, Currency.ARS, Currency.USD, arsUsdRate, arsMoney);
                if (!usdResult.currency().equals(Currency.USD)) {
                    return unconvertible(money);
                }
                return usdResult;
            }
            return unconvertible(money);
        }

        if (source.equals(Currency.ARS)) {
            if (manualRate == null || !manualRate.currency().equals(target)) {
                return unconvertible(money);
            }
            BigDecimal converted = amount.divide(manualRate.ratePerArs(), 2, RoundingMode.HALF_EVEN);
            return new DisplayMoney(converted, target);
        }

        if (source.equals(Currency.USD)) {
            if (arsUsdRate == null || arsUsdRate.buy() == null || arsUsdRate.buy().compareTo(BigDecimal.ZERO) <= 0) {
                return unconvertible(money);
            }
            BigDecimal amountInArs = amount.multiply(arsUsdRate.buy()).setScale(2, RoundingMode.HALF_EVEN);
            if (manualRate == null || !manualRate.currency().equals(target)) {
                return unconvertible(money);
            }
            BigDecimal converted = amountInArs.divide(manualRate.ratePerArs(), 2, RoundingMode.HALF_EVEN);
            return new DisplayMoney(converted, target);
        }

        return unconvertible(money);
    }

    private boolean isArsUsdPair(Currency c1, Currency c2) {
        return (c1.equals(Currency.ARS) && c2.equals(Currency.USD)) ||
                (c1.equals(Currency.USD) && c2.equals(Currency.ARS));
    }

    private boolean isArsOrUsd(Currency c) {
        return c.equals(Currency.ARS) || c.equals(Currency.USD);
    }

    private DisplayMoney convertArsUsdPair(BigDecimal amount, Currency source, Currency target, FxRate arsUsdRate, Money fallbackMoney) {
        if (arsUsdRate == null) {
            return unconvertible(fallbackMoney);
        }
        if (source.equals(Currency.ARS) && target.equals(Currency.USD)) {
            if (arsUsdRate.sell() == null || arsUsdRate.sell().compareTo(BigDecimal.ZERO) <= 0) {
                return unconvertible(fallbackMoney);
            }
            BigDecimal converted = amount.divide(arsUsdRate.sell(), 2, RoundingMode.HALF_EVEN);
            return new DisplayMoney(converted, Currency.USD);
        }
        if (source.equals(Currency.USD) && target.equals(Currency.ARS)) {
            if (arsUsdRate.buy() == null || arsUsdRate.buy().compareTo(BigDecimal.ZERO) <= 0) {
                return unconvertible(fallbackMoney);
            }
            BigDecimal converted = amount.multiply(arsUsdRate.buy()).setScale(2, RoundingMode.HALF_EVEN);
            return new DisplayMoney(converted, Currency.ARS);
        }
        return unconvertible(fallbackMoney);
    }

    private DisplayMoney unconvertible(Money money) {
        return new DisplayMoney(money.amount(), money.currency());
    }
}
