package com.financialapp.gateway.infrastructure.gateway.Impl;

import com.financialapp.gateway.domain.common.model.TimeoutPolicy;
import com.financialapp.gateway.domain.gateway.InvestmentsGateway;
import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.model.currency.FxRate;
import com.financialapp.gateway.domain.model.currency.FxRateMode;
import com.financialapp.gateway.infrastructure.cache.TtlCache;
import com.financialapp.gateway.infrastructure.config.ServicesProperties;
import com.financialapp.gateway.infrastructure.gateway.dto.FxRateResponse;
import com.financialapp.gateway.infrastructure.gateway.dto.GatewayApiResponse;
import com.financialapp.gateway.infrastructure.gateway.dto.HoldingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Component
public class InvestmentsGatewayImpl implements InvestmentsGateway {

    private static final ParameterizedTypeReference<GatewayApiResponse<List<FxRateResponse>>> FX_RATES_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<GatewayApiResponse<List<HoldingResponse>>> HOLDINGS_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient webClient;
    private final String investmentsUrl;
    private final TimeoutPolicy timeoutPolicy;
    private final TtlCache<String, List<FxRate>> fxCache;

    public InvestmentsGatewayImpl(
            WebClient internalWebClient,
            ServicesProperties services,
            TimeoutPolicy timeoutPolicy,
            @Value("${cache.fx-ttl-seconds:30}") long fxTtlSeconds) {
        this.webClient = internalWebClient;
        this.investmentsUrl = services.getInvestmentsUrl();
        this.timeoutPolicy = timeoutPolicy;
        this.fxCache = new TtlCache<>(Duration.ofSeconds(fxTtlSeconds));
    }

    public InvestmentsGatewayImpl(WebClient internalWebClient, ServicesProperties services, TimeoutPolicy timeoutPolicy) {
        this(internalWebClient, services, timeoutPolicy, 30L);
    }

    @Override
    public CompletableFuture<List<FxRate>> latestFxRates() {
        return fxCache.get("LATEST_FX_RATES", this::fetchLatestFxRatesDirect);
    }

    private CompletableFuture<List<FxRate>> fetchLatestFxRatesDirect() {
        return webClient.get()
                .uri(investmentsUrl + "/api/v1/investments/fx/rates/latest")
                .retrieve()
                .bodyToMono(FX_RATES_TYPE)
                .map(response -> nullSafe(response.data()).stream()
                        .map(this::toFxRate)
                        .filter(Objects::nonNull)
                        .toList())
                .timeout(timeoutPolicy.perCall())
                .toFuture();
    }

    @Override
    public CompletableFuture<List<Currency>> holdingCurrencies(Long userId) {
        return webClient.get()
                .uri(investmentsUrl + "/api/v1/investments/holdings")
                .header("X-User-Id", userId.toString())
                .retrieve()
                .bodyToMono(HOLDINGS_TYPE)
                .map(response -> nullSafe(response.data()).stream()
                        .map(HoldingResponse::currency)
                        .filter(c -> c != null && !c.isBlank())
                        .map(Currency::of)
                        .distinct()
                        .toList())
                .timeout(timeoutPolicy.perCall())
                .toFuture();
    }

    private FxRate toFxRate(FxRateResponse r) {
        try {
            FxRateMode mode = FxRateMode.valueOf(r.view().toUpperCase());
            return new FxRate(r.date(), mode, new BigDecimal(r.buy()), new BigDecimal(r.sell()));
        } catch (Exception e) {
            return null;
        }
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
