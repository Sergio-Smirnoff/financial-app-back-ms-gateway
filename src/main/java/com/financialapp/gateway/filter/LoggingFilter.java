package com.financialapp.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@Order(0)
public class LoggingFilter implements WebFilter {

    private static final String START_TIME_ATTR = "requestStartTime";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String method = exchange.getRequest().getMethod().name();
        String path   = exchange.getRequest().getURI().getPath();

        log.info("→ {} {}", method, path);
        exchange.getAttributes().put(START_TIME_ATTR, System.currentTimeMillis());

        return chain.filter(exchange).doFinally(signalType -> {
            long startTime = (long) exchange.getAttributes().getOrDefault(START_TIME_ATTR, System.currentTimeMillis());
            long elapsed   = System.currentTimeMillis() - startTime;
            int  status    = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value()
                    : 0;

            log.info("← {} {} {}ms", status, path, elapsed);
        });
    }
}
