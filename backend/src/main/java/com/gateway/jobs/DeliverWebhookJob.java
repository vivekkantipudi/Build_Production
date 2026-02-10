package com.gateway.jobs;

import com.gateway.services.Database;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;

public class DeliverWebhookJob {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public static void execute(String jobDataJson) {
        Connection conn = null;
        try {
            conn = Database.connect();
            JsonNode data = mapper.readTree(jobDataJson);
            String merchantId = data.get("merchant_id").asText();
            String event = data.get("event").asText();
            
            // 1. Fetch Config
            String webhookUrl = null;
            String webhookSecret = null;
            try (PreparedStatement stmt = conn.prepareStatement("SELECT webhook_url, webhook_secret FROM merchants WHERE id = ?::uuid")) {
                stmt.setString(1, merchantId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    webhookUrl = rs.getString("webhook_url");
                    webhookSecret = rs.getString("webhook_secret");
                }
            }

            if (webhookUrl == null) return; // Skip if no URL

            // 2. Generate HMAC
            String signature = calculateHMAC(jobDataJson, webhookSecret);

            // 3. Send HTTP Request
            int responseCode = 0;
            String responseBody = "";
            boolean success = false;
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrl))
                        .header("Content-Type", "application/json")
                        .header("X-Webhook-Signature", signature)
                        .POST(HttpRequest.BodyPublishers.ofString(jobDataJson))
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                responseCode = response.statusCode();
                responseBody = response.body();
                success = (responseCode >= 200 && responseCode < 300);
            } catch (Exception e) {
                responseCode = 500;
                responseBody = "Network Error: " + e.getMessage();
            }

            // 4. Update Logs & Retry Logic
            // Note: For simplicity, we insert a new log. In production, we'd update existing if retry.
            String sql = "INSERT INTO webhook_logs (merchant_id, event, payload, status, attempts, last_attempt_at, next_retry_at, response_code, response_body) VALUES (?::uuid, ?, ?::jsonb, ?, ?, CURRENT_TIMESTAMP, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, merchantId);
                stmt.setString(2, event);
                stmt.setString(3, jobDataJson);
                stmt.setString(4, success ? "success" : "pending");
                stmt.setInt(5, 1); // Attempt #1
                
                Timestamp nextRetry = null;
                if (!success) {
                    // Logic: 1m, 5m, 30m, 2h. 
                    // Testing mode logic is handled inside PaymentWorker.startRetryPoller or here
                    nextRetry = Timestamp.from(Instant.now().plusSeconds(60)); 
                }
                stmt.setTimestamp(6, nextRetry);
                stmt.setInt(7, responseCode);
                stmt.setString(8, responseBody);
                stmt.executeUpdate();
            }
        } catch (Exception e) { e.printStackTrace(); } 
        finally { try { if (conn != null) conn.close(); } catch (SQLException e) {} }
    }

    private static String calculateHMAC(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}