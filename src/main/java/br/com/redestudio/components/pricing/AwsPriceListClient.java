package br.com.redestudio.components.pricing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Leitura da AWS Price List Bulk API (pública, sem credenciais): o índice de
 * Regiões de cada oferta e o CSV de preços, lido em streaming — nada é
 * gravado em disco nem carregado inteiro na memória.
 *
 * <p>Classe comum com construtor sem argumentos: o CDI a injeta na
 * migration e a linha de comando ({@link PriceLoadCli}) a instancia direto.
 * Nos testes, uma {@code @Alternative} troca a rede por CSVs locais.
 */
@ApplicationScoped
public class AwsPriceListClient {

    public static final String PRICING_BASE = "https://pricing.us-east-1.amazonaws.com";

    /** Linhas de metadados antes do cabeçalho no CSV de oferta. */
    static final int CSV_PREAMBLE_LINES = 5;

    /** Entrada do índice de Regiões: URL da versão atual da oferta naquela Região. */
    public record RegionEntry(String regionCode, String currentVersionUrl) {

        /** URL do CSV (a mesma publicação em {@code .csv}). */
        public String csvUrl() {
            return PRICING_BASE + currentVersionUrl.replace(".json", ".csv");
        }

        /** Versão da publicação: {@code /offers/v1.0/aws/AmazonEC2/<versão>/sa-east-1/index.json}. */
        public String version() {
            String[] parts = currentVersionUrl.split("/");
            return parts.length >= 3 ? parts[parts.length - 3] : "";
        }
    }

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final ObjectMapper json = new ObjectMapper();

    /** Regiões em que a oferta é publicada (código → entrada). */
    public Map<String, RegionEntry> regionIndex(String offer) {
        String url = PRICING_BASE + "/offers/v1.0/aws/" + offer + "/current/region_index.json";
        try (InputStream body = open(url, Duration.ofSeconds(60))) {
            JsonNode regions = json.readTree(body).path("regions");
            Map<String, RegionEntry> index = new LinkedHashMap<>();
            regions.fields().forEachRemaining(entry -> index.put(entry.getKey(), new RegionEntry(
                    entry.getKey(), entry.getValue().path("currentVersionUrl").asText())));
            return index;
        } catch (IOException e) {
            throw new UncheckedIOException("Price List: falha ao ler o índice de " + offer, e);
        }
    }

    /** Entrega cada linha do CSV de oferta (coluna → valor) ao {@code consumer}. */
    public void streamRows(String csvUrl, Consumer<Map<String, String>> consumer) {
        try (InputStream body = open(csvUrl, Duration.ofMinutes(30));
                BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8), 1 << 16)) {
            CsvRecordReader csv = new CsvRecordReader(reader);
            csv.skipLines(CSV_PREAMBLE_LINES);
            Map<String, String> row;
            while ((row = csv.nextRow()) != null) {
                consumer.accept(row);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Price List: falha ao ler " + csvUrl, e);
        }
    }

    /** Abre a URL; falha de conexão (rede instável numa carga de dezenas de arquivos) tenta até 3 vezes. */
    private InputStream open(String url, Duration timeout) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET().build();
        for (int attempt = 1; ; attempt++) {
            try {
                HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() != 200) {
                    response.body().close();
                    throw new IOException("HTTP " + response.statusCode() + " em " + url);
                }
                return response.body();
            } catch (java.net.ConnectException | java.net.http.HttpConnectTimeoutException e) {
                if (attempt == 3) {
                    throw e;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrompido lendo " + url, e);
            }
        }
    }
}
