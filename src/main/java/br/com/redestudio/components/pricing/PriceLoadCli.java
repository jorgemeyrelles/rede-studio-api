package br.com.redestudio.components.pricing;

import br.com.redestudio.components.pricing.PtaxClient.FxQuote;
import br.com.redestudio.dtos.request.CloudPriceBulkUpsertRequest;
import br.com.redestudio.dtos.request.CloudPriceItemRequest;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.Console;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Carga manual do banco cloud (produção ou qualquer API), sem subir o
 * Quarkus — substitui os antigos scripts Python de carga.
 * Roda numa máquina de dev, nunca na VM, e só com aval (skill {@code cloud}).
 *
 * <pre>
 * ./mvnw -q -DskipTests package
 * java -cp "target/quarkus-app/app/*:target/quarkus-app/lib/main/*" \
 *   br.com.redestudio.components.pricing.PriceLoadCli \
 *   prices --regions=sa-east-1,us-east-1 --api=https://rede-studio-api.duckdns.org
 * </pre>
 *
 * <ul>
 *   <li>{@code prices --regions=a,b | --all-commercial [--no-global] [--offers=x,y] [--fx-rate=5.45]
 *       (--api=URL | --out=arquivo.json)} — lê a AWS Price List e envia ao
 *       {@code /api/cloud-prices/bulk-upsert} (ou grava o arquivo);</li>
 *   <li>{@code upload <arquivo.json> --api=URL [--route=/api/cloud-catalog/iam/bulk-upsert]} —
 *       envia um arquivo {@code {"items": [...]}} em lotes (catálogo IAM, por exemplo).</li>
 * </ul>
 *
 * <p>O envio faz login como ADMIN: {@code ADMIN_EMAIL} e {@code ADMIN_PASSWORD}
 * do ambiente, ou pergunta no terminal (a senha não ecoa). A gravação passa
 * pela fila da API, que também limpa o cache do Redis.
 */
public final class PriceLoadCli {

    static final String PRICES_ROUTE = "/api/cloud-prices/bulk-upsert";

    private static final ObjectMapper JSON = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

    private PriceLoadCli() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            usage();
            return;
        }
        Map<String, String> options = options(args);
        List<String> positional = Arrays.stream(args).filter(a -> !a.startsWith("--")).toList();
        PriceLoadCli cli = new PriceLoadCli();
        switch (positional.get(0)) {
            case "prices" -> cli.prices(options);
            case "upload" -> cli.upload(positional.size() > 1 ? positional.get(1) : null, options);
            default -> usage();
        }
    }

    private void prices(Map<String, String> options) {
        boolean all = options.containsKey("all-commercial");
        List<String> regions = csv(options.get("regions"));
        if (!all && regions.isEmpty()) {
            fail("informe --regions=a,b ou --all-commercial");
        }
        String api = options.get("api");
        String out = options.get("out");
        if ((api == null) == (out == null)) {
            fail("informe um destino: --api=URL ou --out=arquivo.json");
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        FxQuote fx = options.containsKey("fx-rate")
                ? FxQuote.manual(new BigDecimal(options.get("fx-rate")), today)
                : new PtaxClient().quote(today);
        log("Câmbio: US$ 1 = R$ " + fx.rate() + " (" + fx.quotedAt().toString().substring(0, 10) + ")");

        AwsPriceLoader.Options load = new AwsPriceLoader.Options(
                regions, all, !options.containsKey("no-global"), Set.copyOf(csv(options.get("offers"))));
        AwsPriceLoader loader = new AwsPriceLoader(new AwsPriceListClient());
        if (out != null) {
            List<CloudPriceItemRequest> items = new ArrayList<>();
            int total = loader.load(load, fx, items::addAll, PriceLoadCli::log);
            write(Path.of(out), Map.of("items", items));
            log(total + " itens → " + out);
        } else {
            String token = login(api);
            int[] sent = {0};
            int total = loader.load(load, fx, batch -> sent[0] += post(api, PRICES_ROUTE, batch, token), PriceLoadCli::log);
            log(sent[0] + " de " + total + " itens aceitos por " + api);
        }
    }

    private void upload(String file, Map<String, String> options) {
        String api = options.get("api");
        if (file == null || api == null) {
            fail("uso: upload <arquivo.json> --api=URL [--route=...]");
        }
        String route = options.getOrDefault("route", PRICES_ROUTE);
        ArrayNode items = (ArrayNode) read(Path.of(file)).path("items");
        String token = login(api);
        int sent = 0;
        for (int start = 0; start < items.size(); start += CloudPriceBulkUpsertRequest.MAX_ITEMS) {
            List<JsonNode> batch = new ArrayList<>();
            for (int i = start; i < Math.min(start + CloudPriceBulkUpsertRequest.MAX_ITEMS, items.size()); i++) {
                batch.add(items.get(i));
            }
            sent += post(api, route, batch, token);
            log("lote " + (start / CloudPriceBulkUpsertRequest.MAX_ITEMS + 1) + ": " + batch.size() + " itens");
        }
        log(sent + " de " + items.size() + " itens aceitos por " + api + route);
    }

    // -----------------------------------------------------------------------
    //  HTTP
    // -----------------------------------------------------------------------

    private String login(String api) {
        String email = System.getenv("ADMIN_EMAIL");
        String password = System.getenv("ADMIN_PASSWORD");
        Console console = System.console();
        if (email == null && console != null) {
            email = console.readLine("E-mail ADMIN: ");
        }
        if (password == null && console != null) {
            password = new String(console.readPassword("Senha ADMIN: "));
        }
        if (email == null || password == null) {
            fail("defina ADMIN_EMAIL e ADMIN_PASSWORD (sem terminal interativo)");
        }
        JsonNode response = send(api + "/api/auth/login", Map.of("email", email, "password", password), null);
        return response.path("token").asText();
    }

    private int post(String api, String route, List<?> batch, String token) {
        return send(api + route, Map.of("items", batch), token).path("received").asInt();
    }

    private JsonNode send(String url, Object body, String token) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMinutes(2))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
            if (token != null) {
                request.header("Authorization", "Bearer " + token);
            }
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                String text = response.body();
                fail("POST " + url + " → HTTP " + response.statusCode() + ": " + text.substring(0, Math.min(500, text.length())));
            }
            return JSON.readTree(response.body());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    // -----------------------------------------------------------------------
    //  Auxiliares
    // -----------------------------------------------------------------------

    /** {@code --chave=valor} e {@code --flag} → mapa (flag = ""). */
    static Map<String, String> options(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (String arg : args) {
            if (arg.startsWith("--")) {
                int eq = arg.indexOf('=');
                options.put(eq < 0 ? arg.substring(2) : arg.substring(2, eq), eq < 0 ? "" : arg.substring(eq + 1));
            }
        }
        return options;
    }

    private static List<String> csv(String value) {
        return value == null || value.isBlank()
                ? List.of()
                : Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    private static void write(Path path, Object value) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            JSON.writeValue(path.toFile(), value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonNode read(Path path) {
        try {
            return JSON.readTree(path.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void log(String line) {
        System.err.println(line);
    }

    private static void fail(String message) {
        System.err.println("erro: " + message);
        System.exit(2);
    }

    private static void usage() {
        log("""
                uso:
                  prices --regions=a,b | --all-commercial [--no-global] [--offers=x,y] [--fx-rate=5.45] (--api=URL | --out=arquivo.json)
                  upload <arquivo.json> --api=URL [--route=/api/cloud-catalog/iam/bulk-upsert]
                login ADMIN: ADMIN_EMAIL / ADMIN_PASSWORD (ou pergunta no terminal)""");
    }
}
