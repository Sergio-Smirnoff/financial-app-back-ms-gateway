package com.financialapp.gateway.domain.service;

import com.financialapp.gateway.domain.model.bff.MoneyFigure;
import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import com.financialapp.gateway.domain.model.currency.FxRate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

public class BffMoneyConverter {

    public static MoneyFigure convert(BigDecimal amount, Currency sourceCurrency, CurrencyView targetView, String secondarySetting, Optional<FxRate> fxRateOpt) {
        if (amount == null) {
            return new MoneyFigure(BigDecimal.ZERO, sourceCurrency != null ? sourceCurrency : Currency.ARS, null);
        }
        if (sourceCurrency == null) {
            sourceCurrency = Currency.ARS;
        }

        boolean isUsdTarget = targetView != null && targetView != CurrencyView.ARS;
        boolean wantsSecondaryArs = "ARS".equalsIgnoreCase(secondarySetting);

        if (!isUsdTarget) {
            if (sourceCurrency.equals(Currency.ARS)) {
                return new MoneyFigure(amount, Currency.ARS, null);
            }
            if (fxRateOpt.isPresent() && fxRateOpt.get().buy() != null && fxRateOpt.get().buy().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal convertedArs = amount.multiply(fxRateOpt.get().buy()).setScale(2, RoundingMode.HALF_EVEN);
                return new MoneyFigure(convertedArs, Currency.ARS, null);
            }
            return new MoneyFigure(amount, sourceCurrency, null);
        }

        if (sourceCurrency.equals(Currency.ARS)) {
            if (fxRateOpt.isPresent() && fxRateOpt.get().sell() != null && fxRateOpt.get().sell().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal usdAmount = amount.divide(fxRateOpt.get().sell(), 2, RoundingMode.HALF_EVEN);
                MoneyFigure secondary = wantsSecondaryArs ? new MoneyFigure(amount, Currency.ARS, null) : null;
                return new MoneyFigure(usdAmount, Currency.USD, secondary);
            }
            return new MoneyFigure(amount, Currency.ARS, null);
        }

        if (sourceCurrency.equals(Currency.USD)) {
            MoneyFigure secondary = null;
            if (wantsSecondaryArs && fxRateOpt.isPresent() && fxRateOpt.get().buy() != null && fxRateOpt.get().buy().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal arsAmount = amount.multiply(fxRateOpt.get().buy()).setScale(2, RoundingMode.HALF_EVEN);
                secondary = new MoneyFigure(arsAmount, Currency.ARS, null);
            }
            return new MoneyFigure(amount, Currency.USD, secondary);
        }

        return new MoneyFigure(amount, sourceCurrency, null);
    }
}
