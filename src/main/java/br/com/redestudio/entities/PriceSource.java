package br.com.redestudio.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Where a {@link CloudPriceEntity} was read from (AWS Price List Bulk API offer file). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PriceSource {

    /** Offer code, e.g. {@code AmazonEC2}. */
    private String offerCode;

    /** Publication version of the offer file, e.g. {@code 20261015123456}. */
    private String publicationVersion;

    private String url;
}
