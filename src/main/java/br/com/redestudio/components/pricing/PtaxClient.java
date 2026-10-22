package br.com.redestudio.components.pricing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Cotação USD→BRL: PTAX venda do Banco Central (API Olinda, pública). Usa a
 * do dia ou a do último dia útil, até 10 dias para trás; sem cotação nesse
 * intervalo, falha — preço sem BRL nunca é gravado.
 */
@ApplicationScoped
public class PtaxClient {

    static final String PTAX_URL = "https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/odata/"
            + "CotacaoDolarDia(dataCotacao=@dataCotacao)?@dataCotacao='%s'&$format=json";

    private static final DateTimeFormatter BCB_DATE = DateTimeFormatter.ofPattern("MM-dd-yyyy");

    /** Cotação usada na carga e o dia a que ela se refere (UTC, meia-noite). */
    public record FxQuote(BigDecimal rate, Instant quotedAt) {

        /** Cotação informada à mão (linha de comando), datada de hoje. */
        public static FxQuote manual(BigDecimal rate, LocalDate today) {
            return new FxQuote(rate, Instant.parse(today + "T00:00:00Z"));
        }
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    private final ObjectMapper json = new ObjectMapper();

    public FxQuote quote(LocalDate today) {
        for (int back = 0; back < 10; back++) {
            LocalDate day = today.minusDays(back);
            JsonNode values = fetch(String.format(PTAX_URL, day.format(BCB_DATE))).path("value");
            if (values.isArray() && !values.isEmpty()) {
                JsonNode last = values.get(values.size() - 1);
                String date = last.path("dataHoraCotacao").asText().substring(0, 10);
                return new FxQuote(new BigDecimal(last.path("cotacaoVenda").asText()), Instant.parse(date + "T00:00:00Z"));
            }
        }
        throw new IllegalStateException("PTAX: nenhuma cotação nos últimos 10 dias");
    }

    private JsonNode fetch(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url.replace("'", "%27").replace("$", "%24")))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("PTAX: HTTP " + response.statusCode());
            }
            return json.readTree(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("PTAX: falha ao consultar o Banco Central", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("PTAX: interrompido", e);
        }
    }
}
