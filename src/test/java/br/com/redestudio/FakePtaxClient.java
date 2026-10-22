package br.com.redestudio;

import br.com.redestudio.components.pricing.PtaxClient;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Test double for {@link PtaxClient}: fixed quote, no call to the Banco Central. */
@Alternative
@Priority(1)
@ApplicationScoped
public class FakePtaxClient extends PtaxClient {

    static final BigDecimal RATE = new BigDecimal("5.0826");

    @Override
    public FxQuote quote(LocalDate today) {
        return new FxQuote(RATE, Instant.parse("2026-10-16T00:00:00Z"));
    }
}
