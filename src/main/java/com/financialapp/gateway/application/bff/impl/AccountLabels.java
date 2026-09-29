package com.financialapp.gateway.application.bff.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class AccountLabels {

    private AccountLabels() {
    }

    static String of(DownstreamPayload account) {
        return account.optionalText("alias").filter(alias -> !alias.isBlank())
                .or(() -> account.optionalText("name").filter(name -> !name.isBlank()))
                .orElseGet(() -> account.textOr("cbu", ""));
    }

    static Map<String, String> byCbu(List<DownstreamPayload> accounts) {
        Map<String, String> labels = new HashMap<>();
        for (DownstreamPayload account : accounts) {
            account.optionalText("cbu").ifPresent(cbu -> labels.put(cbu, of(account)));
        }
        return Map.copyOf(labels);
    }
}
