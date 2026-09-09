package br.com.redestudio.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;

/**
 * Standardised error payload returned by {@code GlobalExceptionHandler}.
 * All error responses from the API share this structure.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ErrorResponse", description = "Standard error response body")
public class ErrorResponse {

    @Schema(description = "UTC timestamp of the error", example = "2026-05-18T12:00:00Z")
    private Instant timestamp;

    @Schema(description = "HTTP status code", example = "401")
    private int status;

    @Schema(description = "Short error label", example = "UNAUTHORIZED")
    private String error;

    @Schema(description = "Human-readable error message", example = "Invalid credentials")
    private String message;

    @Schema(description = "Request path that triggered the error", example = "/api/auth/login")
    private String path;

    /**
     * Convenience factory — sets timestamp to now.
     */
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path);
    }
}
