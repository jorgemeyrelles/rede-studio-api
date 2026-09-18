package br.com.redestudio.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.codecs.pojo.annotations.BsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Embedded sub-document holding a single equipment's approximate market
 * price — never a bare number on {@link EquipmentEntity}, always this
 * shape, so both currencies always carry the same {@code scannedAt} date
 * (the two prices were looked up together, at the same point in time).
 *
 * <p>Not a {@code @MongoEntity} itself — this is a nested POJO embedded
 * inside {@code EquipmentEntity.price}; the MongoDB driver's automatic POJO
 * codec provider (already active via {@code quarkus-mongodb-panache})
 * encodes/decodes it in place, the same way {@code ProjectResponse} nests
 * plain DTOs without any extra codec registration.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EquipmentPrice {

    @BsonProperty("approx_price_usd")
    private BigDecimal approxPriceUsd;

    @BsonProperty("approx_price_brl")
    private BigDecimal approxPriceBrl;

    /** Date the two prices above were looked up — covers both currencies. */
    @BsonProperty("scanned_at")
    private Instant scannedAt;
}
