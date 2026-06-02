/**
 * Outbound gateway interfaces (the domain's ports to other services). A "gateway" here is a
 * domain abstraction returning {@link java.util.concurrent.CompletableFuture}; it is NOT the
 * Spring Cloud Gateway runtime. Reactive WebClient implementations live in
 * {@code infrastructure.gateway.Impl}.
 */
package com.financialapp.gateway.domain.gateway;
