package br.com.redestudio.dtos.response;

import br.com.redestudio.entities.CloudProvider;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/** One region that has prices loaded — feeds the Studio's region selector. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CloudPriceRegionSummary", description = "Region with prices loaded")
public class CloudPriceRegionSummary {

    private CloudProvider provider;
    private String region;
    private String regionName;
    private long itemCount;
    private Instant updatedAt;
    private BigDecimal fxRate;
    private Instant fxQuotedAt;
}
