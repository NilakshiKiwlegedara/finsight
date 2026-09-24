package com.finsight_backend.exception;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;

public record ApiError(int status, String message, String timestamp, Map<String, String> errors) {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    public static ApiError of(int status, String message) {
        return new ApiError(status, message, Instant.now().toString(), Map.of());
    }

    // Security filters run outside controller advice, but use the same response shape.
    public static void write(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(JSON.writeValueAsString(of(status, message)));
    }
}
