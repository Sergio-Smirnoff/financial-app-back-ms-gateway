package com.financialapp.gateway.web.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AvailableCurrenciesResponse(
        List<String> available,
        @JsonProperty("default") String defaultCurrency) {}
