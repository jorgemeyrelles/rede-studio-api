package br.com.redestudio.components.pricing;

import br.com.redestudio.components.pricing.AwsPriceClassifier.Classification;
import br.com.redestudio.components.pricing.AwsPriceListClient.RegionEntry;
import br.com.redestudio.components.pricing.PtaxClient.FxQuote;
import br.com.redestudio.dtos.request.CloudPriceBulkUpsertRequest;
import br.com.redestudio.dtos.request.CloudPriceItemRequest;
import br.com.redestudio.dtos.request.CloudPriceValueRequest;
import br.com.redestudio.entities.CloudProvider;
import br.com.redestudio.entities.PriceSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Carga de preços AWS: para cada Região × oferta, lê o CSV da Price List,
 * classifica ({@link AwsPriceClassifier}) e entrega os itens no formato do
 * {@code POST /api/cloud-prices/bulk-upsert}, com USD e BRL, em lotes de
 * até {@link CloudPriceBulkUpsertRequest#MAX_ITEMS}.
 *
 * <p>Quem chama decide o destino do lote: a migration grava no Mongo local;
 * a linha de comando envia ao endpoint de produção ou escreve um arquivo.
 */
public class AwsPriceLoader {

    /** Serviços lidos por Região (o de EC2 também traz EBS, NAT Gateway e transferência de dados). */
    public static final List<String> OFFERS = List.of(
            "AmazonEC2", "AmazonVPC", "AWSELB", "AmazonRDS", "AmazonS3", "awswaf",
            "awskms", "AWSSecretsManager", "AWSSystemsManager", "AmazonCognito",
            "AWSLambda", "AmazonApiGateway", "AmazonECS", "AmazonEKS", "AmazonECR",
            "AmazonElastiCache", "AmazonDynamoDB", "AWSQueueService", "AmazonSNS", "AmazonEFS",
            "AmazonCloudWatch", "AWSCloudTrail", "AWSBackup", "AmazonGuardDuty", "AWSSecurityHub",
            "AWSConfig");

    /** Serviços globais: a AWS publica na pseudo-Região {@code aws-other}, gravada como {@code global}. */
    public static final List<String> GLOBAL_OFFERS = List.of("AmazonCloudFront", "AmazonRoute53", "awswaf", "AWSShield");
    public static final String GLOBAL_SOURCE = "aws-other";
    public static final String GLOBAL_REGION = "global";

    /** Região comercial (fora local zones, wavelength, GovCloud e China). */
    static final Pattern COMMERCIAL_REGION = Pattern.compile("^(?!us-gov-)[a-z]{2}-[a-z]+-\\d$");

    /**
     * O que carregar. {@code regions} vazio + {@code allCommercial} = todas as
     * Regiões comerciais; {@code offers} vazio = todas.
     */
    public record Options(List<String> regions, boolean allCommercial, boolean includeGlobal, Set<String> offers) {
    }

    private final AwsPriceListClient client;

    public AwsPriceLoader(AwsPriceListClient client) {
        this.client = client;
    }

    /**
     * Executa a carga entregando lotes ao {@code sink}; {@code log} recebe
     * uma linha por Região × oferta.
     *
     * @return total de itens entregues
     */
    public int load(Options options, FxQuote fx, Consumer<List<CloudPriceItemRequest>> sink, Consumer<String> log) {
        List<String> offers = OFFERS.stream().filter(o -> wanted(options, o)).toList();
        List<String> globalOffers = options.includeGlobal()
                ? GLOBAL_OFFERS.stream().filter(o -> wanted(options, o)).toList()
                : List.of();

        Set<String> indexOffers = new LinkedHashSet<>(offers);
        indexOffers.addAll(globalOffers);
        indexOffers.add("AmazonEC2");
        Map<String, Map<String, RegionEntry>> indexes = new HashMap<>();
        indexOffers.forEach(offer -> indexes.put(offer, client.regionIndex(offer)));

        List<String> regions = options.allCommercial()
                ? indexes.get("AmazonEC2").keySet().stream().filter(r -> COMMERCIAL_REGION.matcher(r).matches()).sorted().toList()
                : options.regions();
        log.accept("Regiões (" + regions.size() + "): " + String.join(", ", regions));

        List<String[]> jobs = new ArrayList<>();
        regions.forEach(region -> offers.forEach(offer -> jobs.add(new String[] {region, offer})));
        globalOffers.forEach(offer -> jobs.add(new String[] {GLOBAL_SOURCE, offer}));

        Batcher batcher = new Batcher(sink);
        for (String[] job : jobs) {
            String region = job[0];
            String offer = job[1];
            RegionEntry entry = indexes.get(offer).get(region);
            if (entry == null) {
                log.accept("  " + region + " " + offer + ": sem oferta na Região — pulando");
                continue;
            }
            int before = batcher.total;
            client.streamRows(entry.csvUrl(), row -> toItem(offer, region, entry, row, fx).ifPresent(batcher::add));
            log.accept("  " + region + " " + offer + ": " + (batcher.total - before) + " itens");
        }
        batcher.flush();
        return batcher.total;
    }

    private static boolean wanted(Options options, String offer) {
        return options.offers() == null || options.offers().isEmpty() || options.offers().contains(offer);
    }

    /** Linha do CSV → item do {@code bulk-upsert}, ou vazio se ela não entra no recorte. */
    static Optional<CloudPriceItemRequest> toItem(String offer, String region, RegionEntry entry,
            Map<String, String> row, FxQuote fx) {
        if (!"OnDemand".equals(row.get("TermType")) || !"USD".equals(row.get("Currency"))) {
            return Optional.empty();
        }
        String regionCode = row.get("Region Code");
        if (regionCode != null && !regionCode.isEmpty() && !regionCode.equals(region)) {
            return Optional.empty(); // linhas inter-Região (from/to) não entram
        }
        Optional<Classification> match = AwsPriceClassifier.classify(offer, row);
        if (match.isEmpty()) {
            return Optional.empty();
        }
        String storedRegion = GLOBAL_SOURCE.equals(region) ? GLOBAL_REGION : region;
        String location = row.get("Location");
        BigDecimal usd = new BigDecimal(row.get("PricePerUnit"));

        CloudPriceItemRequest item = new CloudPriceItemRequest();
        item.setProvider(CloudProvider.AWS);
        item.setServiceCode(offer);
        item.setResourceType(match.get().resourceType());
        item.setRegion(storedRegion);
        item.setRegionName(GLOBAL_REGION.equals(storedRegion) ? "Global" : blankToNull(location));
        item.setSku(row.get("SKU"));
        item.setRateCode(row.get("RateCode"));
        item.setDescription(row.get("PriceDescription"));
        item.setAttributes(match.get().attributes());
        item.setUnit(row.get("Unit"));
        item.setPrice(new CloudPriceValueRequest(money(usd), money(usd.multiply(fx.rate())), fx.rate(), fx.quotedAt()));
        item.setTierBegin(tier(row.get("StartingRange")));
        item.setTierEnd(tier(row.get("EndingRange")));
        item.setPricingModel("ON_DEMAND");
        item.setEffectiveDate(isoDate(row.get("EffectiveDate")));
        item.setSource(new PriceSource(offer, entry.version(), entry.csvUrl()));
        return Optional.of(item);
    }

    /** 10 casas, arredondamento comercial, sem zeros à direita (US$ 0,00000056 por GET do S3 cabe). */
    static BigDecimal money(BigDecimal value) {
        BigDecimal scaled = value.setScale(10, RoundingMode.HALF_UP).stripTrailingZeros();
        return scaled.scale() < 0 ? scaled.setScale(0) : scaled;
    }

    static BigDecimal tier(String value) {
        return value == null || value.isEmpty() || value.equals("Inf") ? null : new BigDecimal(value);
    }

    static Instant isoDate(String value) {
        return value == null || value.length() < 10 ? null : Instant.parse(value.substring(0, 10) + "T00:00:00Z");
    }

    private static String blankToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    /** Acumula itens e entrega ao destino em lotes do tamanho aceito pelo endpoint. */
    private static final class Batcher {
        private final Consumer<List<CloudPriceItemRequest>> sink;
        private List<CloudPriceItemRequest> batch = new ArrayList<>();
        private int total;

        Batcher(Consumer<List<CloudPriceItemRequest>> sink) {
            this.sink = sink;
        }

        void add(CloudPriceItemRequest item) {
            batch.add(item);
            total++;
            if (batch.size() >= CloudPriceBulkUpsertRequest.MAX_ITEMS) {
                flush();
            }
        }

        void flush() {
            if (!batch.isEmpty()) {
                sink.accept(batch);
                batch = new ArrayList<>();
            }
        }
    }
}
