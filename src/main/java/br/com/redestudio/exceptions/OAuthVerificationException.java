package br.com.redestudio.exceptions;

/**
 * Thrown when an OAuth ID token (Google / Microsoft) fails verification —
 * bad signature, expired, unexpected issuer/audience, or an unsupported
 * {@code provider} path value.
 *
 * <p>Distinct from {@link InvalidCredentialsException} (wrong password) and
 * {@link UnauthorizedException} (protected route without a valid app JWT):
 * this one is specifically about the third-party token presented at
 * OAuth login time.
 *
 * <p>Mapped to HTTP 401 Unauthorized by {@code GlobalExceptionHandler}.
 * The message is intentionally generic — never echoes provider error
 * details back to the client.
 */
public class OAuthVerificationException extends RuntimeException {

    public OAuthVerificationException() {
        super("OAuth token verification failed");
    }
}
