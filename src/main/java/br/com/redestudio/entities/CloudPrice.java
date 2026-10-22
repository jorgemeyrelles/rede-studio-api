package br.com.redestudio.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Embedded {@code price} sub-document of a {@link CloudPriceEntity} — never
 * a bare number. Carries both currencies plus the exchange rate used to
 * derive BRL, so it's always explicit <em>when</em> the BRL value is from
 * (same spirit as {@link EquipmentPrice}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CloudPrice {

    /** Price published by the provider (AWS publishes USD only). */
    private BigDecimal usd;

    /** {@code usd × fxRate}, computed at load time. */
    private BigDecimal brl;

    /** USD→BRL rate used (PTAX venda, Banco Central do Brasil). */
    private BigDecimal fxRate;

    /** Date of the {@code fxRate} quote. */
    private Instant fxQuotedAt;
}
