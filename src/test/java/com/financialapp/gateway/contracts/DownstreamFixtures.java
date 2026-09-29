package com.financialapp.gateway.contracts;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class DownstreamFixtures {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DownstreamFixtures() {
    }

    public static String json(String path) {
        try (InputStream in = DownstreamFixtures.class.getResourceAsStream("/contracts/" + path)) {
            if (in == null) {
                throw new IllegalStateException("Missing contract fixture: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Map<String, Object> object(String path) {
        return read(path, new TypeReference<>() {
        });
    }

    public static List<Map<String, Object>> list(String path) {
        return read(path, new TypeReference<>() {
        });
    }

    private static <T> T read(String path, TypeReference<T> type) {
        try {
            return MAPPER.readValue(json(path), type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
