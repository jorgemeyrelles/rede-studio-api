package br.com.redestudio.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/** What the cloud price database currently holds: regions per provider family. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CloudPriceMetaResponse", description = "Regions with prices loaded, per provider")
public class CloudPriceMetaResponse {

    private List<CloudPriceRegionSummary> regions;
}
