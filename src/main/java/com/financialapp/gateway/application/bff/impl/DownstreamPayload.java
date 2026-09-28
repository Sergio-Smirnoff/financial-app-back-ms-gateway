package com.financialapp.gateway.application.bff.impl;

import com.financialapp.gateway.domain.exception.DownstreamContractViolationException;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
public final class DownstreamPayload {

    private static final String TYPED_VIEW_SOURCE = "typed downstream view";
    private static final int ISO_DATE_LENGTH = 10;

    private final String source;
    private final Map<String, Object> fields;

    public DownstreamPayload(String source, Map<String, Object> fields) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source required");
        }
        this.source = source;
        this.fields = fields != null ? fields : Map.of();
    }

    public static List<DownstreamPayload> rows(String source, List<Map<String, Object>> rows) {
        return rows == null ? List.of() : rows.stream().map(row -> new DownstreamPayload(source, row)).toList();
    }

    public static BigDecimal amountOrZero(String raw) {
        return raw == null ? BigDecimal.ZERO : decimalOf(TYPED_VIEW_SOURCE, "amount", raw);
    }

    public boolean isEmpty() {
        return fields.isEmpty();
    }

    public BigDecimal decimal(String key) {
        return optionalDecimal(key).orElseThrow(() -> missing(key));
    }

    public Optional<BigDecimal> optionalDecimal(String key) {
        return value(key).map(raw -> decimalOf(source, key, raw));
    }

    public BigDecimal decimalOrZero(String key) {
        return optionalDecimal(key).orElse(BigDecimal.ZERO);
    }

    public long longValue(String key) {
        return optionalLong(key).orElseThrow(() -> missing(key));
    }

    public Optional<Long> optionalLong(String key) {
        return value(key).map(raw -> {
            try {
                return new BigDecimal(raw.toString()).longValueExact();
            } catch (NumberFormatException | ArithmeticException e) {
                throw violation(source, key, "is not a whole number: " + raw);
            }
        });
    }

    public String text(String key) {
        return optionalText(key).orElseThrow(() -> missing(key));
    }

    public Optional<String> optionalText(String key) {
        return value(key).map(Object::toString);
    }

    public String textOr(String key, String fallback) {
        return optionalText(key).orElse(fallback);
    }

    public boolean flag(String key) {
        return optionalFlag(key).orElseThrow(() -> missing(key));
    }

    public boolean flagOr(String key, boolean fallback) {
        return optionalFlag(key).orElse(fallback);
    }

    private Optional<Boolean> optionalFlag(String key) {
        return value(key).map(raw -> {
            if (raw instanceof Boolean bool) {
                return bool;
            }
            throw violation(source, key, "is not a boolean: " + raw);
        });
    }

    public LocalDate date(String key) {
        return optionalDate(key).orElseThrow(() -> missing(key));
    }

    public Optional<LocalDate> optionalDate(String key) {
        return value(key).map(raw -> {
            String text = raw.toString();
            try {
                return text.length() == ISO_DATE_LENGTH
                        ? LocalDate.parse(text)
                        : instantOf(text).atOffset(ZoneOffset.UTC).toLocalDate();
            } catch (DateTimeParseException e) {
                throw violation(source, key, "is not an ISO date: " + raw);
            }
        });
    }

    public Instant instant(String key) {
        return optionalInstant(key).orElseThrow(() -> missing(key));
    }

    public Optional<Instant> optionalInstant(String key) {
        return value(key).map(raw -> {
            try {
                return instantOf(raw.toString());
            } catch (DateTimeParseException e) {
                throw violation(source, key, "is not an ISO date-time: " + raw);
            }
        });
    }

    public List<DownstreamPayload> list(String key) {
        return optionalList(key).orElseThrow(() -> missing(key));
    }

    public List<DownstreamPayload> listOrEmpty(String key) {
        return optionalList(key).orElse(List.of());
    }

    public Optional<DownstreamPayload> optionalObject(String key) {
        return value(key).map(raw -> objectOf(key, raw));
    }

    private Optional<List<DownstreamPayload>> optionalList(String key) {
        return value(key).map(raw -> {
            if (!(raw instanceof List<?> items)) {
                throw violation(source, key, "is not a list");
            }
            return items.stream().map(item -> objectOf(key, item)).toList();
        });
    }

    private DownstreamPayload objectOf(String key, Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw violation(source, key, "is not an object");
        }
        Map<String, Object> copy = new HashMap<>();
        map.forEach((name, fieldValue) -> copy.put(String.valueOf(name), fieldValue));
        return new DownstreamPayload(source + "." + key, copy);
    }

    private Optional<Object> value(String key) {
        return Optional.ofNullable(fields.get(key));
    }

    private DownstreamContractViolationException missing(String key) {
        return violation(source, key, "is missing");
    }

    private static BigDecimal decimalOf(String source, String key, Object raw) {
        try {
            return new BigDecimal(raw.toString());
        } catch (NumberFormatException e) {
            throw violation(source, key, "is not a number: " + raw);
        }
    }

    private static Instant instantOf(String text) {
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException withoutOffset) {
            return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
        }
    }

    private static DownstreamContractViolationException violation(String source, String key, String problem) {
        log.warn("Downstream contract violation: {} field '{}' {}", source, key, problem);
        return new DownstreamContractViolationException(source, key, problem);
    }
}
