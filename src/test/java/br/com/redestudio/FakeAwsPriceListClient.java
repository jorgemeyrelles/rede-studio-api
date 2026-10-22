package br.com.redestudio;

import br.com.redestudio.components.pricing.AwsPriceListClient;
import br.com.redestudio.components.pricing.CsvRecordReader;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Test double for {@link AwsPriceListClient}: no network. Only AmazonEC2 in
 * sa-east-1 is "published", served from {@code src/test/resources/pricing/}
 * (same CSV layout as the AWS Price List, 5 metadata lines included).
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class FakeAwsPriceListClient extends AwsPriceListClient {

    static final String EC2_VERSION_URL = "/offers/v1.0/aws/AmazonEC2/20261001000000/sa-east-1/index.json";

    @Override
    public Map<String, RegionEntry> regionIndex(String offer) {
        return "AmazonEC2".equals(offer)
                ? Map.of("sa-east-1", new RegionEntry("sa-east-1", EC2_VERSION_URL))
                : Map.of();
    }

    @Override
    public void streamRows(String csvUrl, Consumer<Map<String, String>> consumer) {
        String[] parts = csvUrl.split("/");
        String resource = "pricing/" + parts[parts.length - 4] + "-" + parts[parts.length - 2] + ".csv";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource);
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            CsvRecordReader csv = new CsvRecordReader(reader);
            csv.skipLines(5);
            Map<String, String> row;
            while ((row = csv.nextRow()) != null) {
                consumer.accept(row);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
