package dev.umutaslan.lagerscan;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** The real application on a random port against PostgreSQL (DB_URL), driven like a browser/phone would. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiIT {
    @Value("${local.server.port}") int port;
    private final ObjectMapper json = new ObjectMapper();

    private Client client() { return new Client(); }

    final class Client {
        final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager()).followRedirects(HttpClient.Redirect.NORMAL).build();

        HttpResponse<String> get(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(), HttpResponse.BodyHandlers.ofString());
        }

        JsonNode getJson(String path) throws Exception {
            var res = get(path);
            assertThat(res.statusCode()).as(res.body()).isEqualTo(200);
            return json.readTree(res.body());
        }

        HttpResponse<String> post(String path, String body) throws Exception {
            return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> book(String type, String article, String from, String to, String qty, String counted, UUID clientId) throws Exception {
            return post("/api/bookings", """
                {"clientId":"%s","type":"%s","articleNumber":"%s","fromBin":%s,"toBin":%s,"quantity":%s,"counted":%s,"device":"Test"}"""
                .formatted(clientId, type, article, q(from), q(to), qty, counted));
        }

        BigDecimalAt qtyIn(String article, String bin) throws Exception {
            for (var line : getJson("/api/articles/" + article).get("stock"))
                if (line.get("binCode").asString().equals(bin)) return new BigDecimalAt(line.get("quantity").decimalValue());
            return new BigDecimalAt(java.math.BigDecimal.ZERO);
        }
    }

    record BigDecimalAt(java.math.BigDecimal value) {}

    private static String q(String s) { return s == null ? "null" : "\"" + s + "\""; }

    @Test
    void a_new_visitor_gets_a_private_demo_warehouse() throws Exception {
        var c = client();
        var d = c.getJson("/api/dashboard");
        assertThat(d.get("articles").asInt()).isEqualTo(36);
        assertThat(d.get("bins").asInt()).isEqualTo(32); // racks A 12, B 9, C 9 + receiving 2
        assertThat(d.get("belowMin").size()).isPositive();
        assertThat(d.get("receiving").size()).isEqualTo(3);
        assertThat(c.get("/api/dashboard").headers().allValues("set-cookie")).isEmpty(); // cookie only once
    }

    @Test
    void scans_resolve_labels_eans_and_plain_codes() throws Exception {
        var c = client();
        assertThat(c.getJson("/api/scan?code=ART:ET-1001").get("kind").asString()).isEqualTo("article");
        assertThat(c.getJson("/api/scan?code=BIN:WE-01").get("bin").get("contents").size()).isEqualTo(2);
        var ean = c.getJson("/api/articles/ET-1007").get("article").get("ean").asString();
        assertThat(c.getJson("/api/scan?code=" + ean).get("article").get("article").get("number").asString()).isEqualTo("ET-1007");
        assertThat(c.getJson("/api/scan?code=a-01-01").get("kind").asString()).isEqualTo("bin");
        var unknown = c.get("/api/scan?code=XYZ-42");
        assertThat(unknown.statusCode()).isEqualTo(422);
        assertThat(unknown.body()).contains("weder Artikel noch Lagerplatz");
    }

    @Test
    void putting_away_from_receiving_moves_stock_and_a_resent_booking_changes_nothing() throws Exception {
        var c = client();
        var id = UUID.randomUUID();
        var first = c.book("TRANSFER", "ET-1001", "WE-01", "A-01-01", "8", "null", id);
        assertThat(first.statusCode()).as(first.body()).isEqualTo(201);
        assertThat(c.qtyIn("ET-1001", "WE-01").value()).isEqualByComparingTo("12");
        var inA = c.qtyIn("ET-1001", "A-01-01").value();

        var again = c.book("TRANSFER", "ET-1001", "WE-01", "A-01-01", "8", "null", id);
        assertThat(again.statusCode()).isEqualTo(200);
        assertThat(json.readTree(again.body()).get("duplicate").asBoolean()).isTrue();
        assertThat(c.qtyIn("ET-1001", "WE-01").value()).isEqualByComparingTo("12");
        assertThat(c.qtyIn("ET-1001", "A-01-01").value()).isEqualByComparingTo(inA);
    }

    @Test
    void business_rules_are_explained() throws Exception {
        var c = client();
        var tooMuch = c.book("ISSUE", "ET-1001", "WE-01", null, "999", "null", UUID.randomUUID());
        assertThat(tooMuch.statusCode()).isEqualTo(422);
        assertThat(tooMuch.body()).contains("liegen nur 20");
        assertThat(c.book("TRANSFER", "ET-1001", "WE-01", "WE-01", "1", "null", UUID.randomUUID()).body()).contains("derselbe Lagerplatz");
        assertThat(c.book("RECEIPT", "ET-1001", null, "Z-99", "1", "null", UUID.randomUUID()).body()).contains("unbekannt");
        assertThat(c.book("RECEIPT", "ET-1001", null, "A-01-01", "0", "null", UUID.randomUUID()).body()).contains("größer als 0");
    }

    @Test
    void stocktaking_records_the_difference() throws Exception {
        var c = client();
        var before = c.getJson("/api/dashboard").get("countDifferencesToday").asInt(); // demo history may contain counts from today
        var res = c.book("COUNT", "ET-1025", null, "WE-01", "null", "10", UUID.randomUUID());
        assertThat(res.statusCode()).isEqualTo(201);
        assertThat(json.readTree(res.body()).get("booking").get("difference").decimalValue()).isEqualByComparingTo("-2");
        assertThat(c.qtyIn("ET-1025", "WE-01").value()).isEqualByComparingTo("10");
        assertThat(c.getJson("/api/dashboard").get("countDifferencesToday").asInt()).isEqualTo(before + 1);
    }

    @Test
    void the_phone_joins_the_laptops_workspace_through_the_qr_link() throws Exception {
        var laptop = client();
        var phone = client();
        var joinUrl = laptop.getJson("/api/workspace").get("joinUrl").asString();
        var joined = phone.get(URI.create(joinUrl).getPath());
        assertThat(joined.uri().toString()).endsWith("/scan?joined=1");

        phone.book("RECEIPT", "ET-1002", null, "WE-02", "4", "null", UUID.randomUUID());
        assertThat(laptop.qtyIn("ET-1002", "WE-02").value()).isEqualByComparingTo("4");
        assertThat(client().qtyIn("ET-1002", "WE-02").value()).isEqualByComparingTo("0"); // others unaffected
    }

    @Test
    void bookings_are_pushed_live_to_the_control_station() throws Exception {
        var c = client();
        c.getJson("/api/dashboard"); // creates the workspace cookie
        var stream = c.http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/events")).build(), HttpResponse.BodyHandlers.ofInputStream());
        var received = CompletableFuture.supplyAsync(() -> readUntil(stream.body(), "event:booking"));
        Thread.sleep(300);
        c.book("ISSUE", "ET-1001", "WE-01", null, "1", "null", UUID.randomUUID());
        assertThat(received.get(10, TimeUnit.SECONDS)).contains("ET-1001").contains("Entnahme");
    }

    @Test
    void photos_are_stored_per_workspace_and_only_images_are_accepted() throws Exception {
        var c = client();
        var boundary = "----t" + UUID.randomUUID();
        byte[] png = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==");
        var res = c.http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/photos"))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(multipart(boundary, png, "image/png", "ET-1003")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).as(res.body()).isEqualTo(201);
        var url = json.readTree(res.body()).get("url").asString();
        assertThat(c.get(url).statusCode()).isEqualTo(200);
        assertThat(client().get(url).statusCode()).isEqualTo(404);
        assertThat(c.getJson("/api/articles/ET-1003").get("article").get("photoUrl").asString()).isEqualTo(url);

        var text = c.http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/photos"))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(multipart(boundary, "hello".getBytes(StandardCharsets.UTF_8), "text/plain", "ET-1003")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(text.statusCode()).isEqualTo(422);
    }

    @Test
    void responses_carry_security_headers() throws Exception {
        var res = client().get("/api/health");
        assertThat(res.headers().firstValue("content-security-policy").orElse("")).contains("frame-ancestors 'none'");
        assertThat(res.headers().firstValue("permissions-policy").orElse("")).contains("camera=(self)");
    }

    private static HttpRequest.BodyPublisher multipart(String boundary, byte[] file, String type, String article) {
        var head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"articleNumber\"\r\n\r\n" + article + "\r\n"
            + "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"f\"\r\nContent-Type: " + type + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        var tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        return HttpRequest.BodyPublishers.ofByteArrays(java.util.List.of(head, file, tail));
    }

    private static String readUntil(InputStream in, String marker) {
        try (var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            var sb = new StringBuilder();
            String line;
            var deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (System.nanoTime() < deadline && (line = reader.readLine()) != null) {
                sb.append(line).append('\n');
                if (sb.indexOf(marker) >= 0 && line.startsWith("data:")) return sb.toString();
            }
            return sb.toString();
        } catch (Exception e) {
            return e.toString();
        }
    }
}
