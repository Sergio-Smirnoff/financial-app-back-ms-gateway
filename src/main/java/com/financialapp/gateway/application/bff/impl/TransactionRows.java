package com.financialapp.gateway.application.bff.impl;

import com.financialapp.gateway.domain.model.bff.BffDomainModels.TransactionDirection;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.TransactionRow;
import com.financialapp.gateway.domain.model.bff.MoneyFigure;
import com.financialapp.gateway.domain.model.currency.Currency;

import java.util.Map;
import java.util.function.UnaryOperator;

final class TransactionRows {

    private static final String INCOME = "INCOME";

    private TransactionRows() {
    }

    static TransactionRow from(
            DownstreamPayload transaction, Map<String, String> labelsByCbu, UnaryOperator<MoneyFigure> display) {
        boolean income = INCOME.equals(transaction.text("kind"));
        String ownAccountCbu = income ? transaction.text("toCbu") : transaction.text("fromCbu");
        MoneyFigure amount = MoneyFigure.of(transaction.decimal("amount"), Currency.of(transaction.text("currency")));
        return new TransactionRow(
                transaction.longValue("id"),
                transaction.date("date"),
                transaction.textOr("description", ""),
                ownAccountCbu,
                labelsByCbu.get(ownAccountCbu),
                transaction.optionalLong("categoryId").orElse(null),
                transaction.textOr("categoryName", ""),
                transaction.text("paymentMethod"),
                transaction.textOr("note", ""),
                display.apply(amount),
                income ? TransactionDirection.IN : TransactionDirection.OUT);
    }
}
