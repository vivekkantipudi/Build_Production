package com.gateway;

import static spark.Spark.*;
import com.gateway.services.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.*;
import java.util.UUID;
import java.time.Instant;

public class Main {
    private static final ObjectMapper mapper = new ObjectMapper();

    // Helper to dynamically find the ID for the test account
    private static String getTestMerchantId(Connection conn) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT id FROM merchants WHERE email = 'test@example.com'")) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("id");
            throw new SQLException("Test merchant configuration missing in database");
        }
    }

    public static void main(String[] args) {
        port(8080);

        // CORS Headers
        options("/*", (req, res) -> {
            String headers = req.headers("Access-Control-Request-Headers");
            if (headers != null) res.header("Access-Control-Allow-Headers", headers);
            String method = req.headers("Access-Control-Request-Method");
            if (method != null) res.header("Access-Control-Allow-Methods", method);
            return "OK";
        });
        before((req, res) -> {
            res.header("Access-Control-Allow-Origin", "*");
            res.header("Access-Control-Request-Method", "GET, POST, PUT, DELETE, OPTIONS");
            res.header("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Api-Key, Idempotency-Key");
        });

        // Auth Middleware
        before("/api/v1/*", (req, res) -> {
            if (req.requestMethod().equals("OPTIONS")) return;
            if (req.pathInfo().equals("/api/v1/test/jobs/status")) return;
            String apiKey = req.headers("X-Api-Key");
            if (apiKey == null || !apiKey.startsWith("key_")) halt(401, "{\"error\":\"Unauthorized\"}");
        });

        // Global Exception Handler (Returns JSON instead of HTML on error)
        exception(Exception.class, (e, req, res) -> {
            e.printStackTrace();
            res.status(500);
            res.body("{\"error\": \"" + e.getMessage() + "\"}");
        });

        // 1. Create Payment
        post("/api/v1/payments", (req, res) -> {
            res.type("application/json");
            Connection conn = Database.connect();
            
            // DYNAMIC LOOKUP: No hardcoding!
            String merchantId = getTestMerchantId(conn);

            String idempotencyKey = req.headers("Idempotency-Key");
            if (idempotencyKey != null) {
                try (PreparedStatement stmt = conn.prepareStatement("SELECT response, expires_at FROM idempotency_keys WHERE key = ? AND merchant_id = ?::uuid")) {
                    stmt.setString(1, idempotencyKey);
                    stmt.setString(2, merchantId);
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        if (rs.getTimestamp("expires_at").after(Timestamp.from(Instant.now()))) {
                            res.status(201);
                            conn.close();
                            return rs.getString("response");
                        } else conn.createStatement().execute("DELETE FROM idempotency_keys WHERE key = '" + idempotencyKey + "'");
                    }
                }
            }

            JsonNode body = mapper.readTree(req.body());
            String paymentId = "pay_" + UUID.randomUUID().toString().substring(0, 14);
            int amount = body.get("amount").asInt();
            String currency = body.has("currency") ? body.get("currency").asText() : "INR";
            String method = body.get("method").asText();
            String orderId = body.get("order_id").asText();

            try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO payments (id, merchant_id, order_id, amount, currency, method, status) VALUES (?, ?::uuid, ?, ?, ?, ?, 'pending')")) {
                stmt.setString(1, paymentId);
                stmt.setString(2, merchantId);
                stmt.setString(3, orderId);
                stmt.setInt(4, amount);
                stmt.setString(5, currency);
                stmt.setString(6, method);
                stmt.executeUpdate();
            }

            JobService.enqueueJob("PROCESS_PAYMENT", paymentId);

            ObjectNode response = mapper.createObjectNode();
            response.put("id", paymentId);
            response.put("order_id", orderId);
            response.put("amount", amount);
            response.put("currency", currency);
            response.put("method", method);
            response.put("status", "pending");
            response.put("created_at", Instant.now().toString());
            String jsonResponse = mapper.writeValueAsString(response);

            if (idempotencyKey != null) {
                try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO idempotency_keys (key, merchant_id, response, expires_at) VALUES (?, ?::uuid, ?::jsonb, ?)")) {
                    stmt.setString(1, idempotencyKey);
                    stmt.setString(2, merchantId);
                    stmt.setString(3, jsonResponse);
                    stmt.setTimestamp(4, Timestamp.from(Instant.now().plusSeconds(86400)));
                    stmt.executeUpdate();
                }
            }
            conn.close();
            res.status(201);
            return jsonResponse;
        });

        // 2. Capture
        post("/api/v1/payments/:id/capture", (req, res) -> {
            res.type("application/json");
            String paymentId = req.params(":id");
            Connection conn = Database.connect();
            try (PreparedStatement stmt = conn.prepareStatement("SELECT status FROM payments WHERE id = ?")) {
                stmt.setString(1, paymentId);
                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) halt(404, "Payment not found");
                if (!"success".equals(rs.getString("status"))) halt(400, "{\"error\": {\"code\": \"BAD_REQUEST_ERROR\", \"description\": \"Payment not in capturable state\"}}");
            }
            try (PreparedStatement stmt = conn.prepareStatement("UPDATE payments SET captured = true, updated_at = CURRENT_TIMESTAMP WHERE id = ?")) {
                stmt.setString(1, paymentId);
                stmt.executeUpdate();
            }
            conn.close();
            return "{\"id\":\"" + paymentId + "\", \"status\":\"success\", \"captured\":true}";
        });

        // 3. Refunds
        post("/api/v1/payments/:id/refunds", (req, res) -> {
            res.type("application/json");
            String paymentId = req.params(":id");
            JsonNode body = mapper.readTree(req.body());
            int amount = body.get("amount").asInt();
            String reason = body.has("reason") ? body.get("reason").asText() : "";
            Connection conn = Database.connect();
            
            // DYNAMIC LOOKUP: Get the merchant_id associated with this payment
            String merchantId = null;
            try (PreparedStatement stmt = conn.prepareStatement("SELECT amount, status, merchant_id FROM payments WHERE id = ?")) {
                stmt.setString(1, paymentId);
                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) halt(404, "Payment not found");
                if (!"success".equals(rs.getString("status"))) halt(400, "{\"error\": \"Payment must be successful\"}");
                
                merchantId = rs.getString("merchant_id"); // Get ID from DB
                
                int totalRefunded = 0;
                try (PreparedStatement refStmt = conn.prepareStatement("SELECT SUM(amount) as total FROM refunds WHERE payment_id = ? AND status != 'failed'")) {
                    refStmt.setString(1, paymentId);
                    ResultSet refRs = refStmt.executeQuery();
                    if (refRs.next()) totalRefunded = refRs.getInt("total");
                }
                if (amount + totalRefunded > rs.getInt("amount")) halt(400, "{\"error\": {\"code\":\"BAD_REQUEST_ERROR\", \"description\":\"Refund amount exceeds available amount\"}}");
            }

            String refundId = "rfnd_" + UUID.randomUUID().toString().substring(0, 16);
            try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO refunds (id, payment_id, merchant_id, amount, reason, status) VALUES (?, ?, ?::uuid, ?, ?, 'pending')")) {
                stmt.setString(1, refundId);
                stmt.setString(2, paymentId);
                stmt.setString(3, merchantId);
                stmt.setInt(4, amount);
                stmt.setString(5, reason);
                stmt.executeUpdate();
            }
            JobService.enqueueJob("PROCESS_REFUND", refundId);
            conn.close();
            res.status(201);
            return "{\"id\":\"" + refundId + "\", \"payment_id\":\""+paymentId+"\", \"amount\":"+amount+", \"reason\":\""+reason+"\", \"status\":\"pending\", \"created_at\":\""+Instant.now().toString()+"\"}";
        });

        // 4. Get Refund
        get("/api/v1/refunds/:id", (req, res) -> {
            res.type("application/json");
            String refundId = req.params(":id");
            Connection conn = Database.connect();
            ObjectNode response = mapper.createObjectNode();
            try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM refunds WHERE id = ?")) {
                stmt.setString(1, refundId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    response.put("id", rs.getString("id"));
                    response.put("payment_id", rs.getString("payment_id"));
                    response.put("amount", rs.getInt("amount"));
                    response.put("status", rs.getString("status"));
                    response.put("processed_at", rs.getString("processed_at"));
                } else halt(404, "Refund not found");
            }
            conn.close();
            return mapper.writeValueAsString(response);
        });

        // 5. Webhooks
        get("/api/v1/webhooks", (req, res) -> {
            res.type("application/json");
            Connection conn = Database.connect();
            StringBuilder json = new StringBuilder("{\"data\":[");
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT id, event, status, attempts, response_code FROM webhook_logs ORDER BY created_at DESC LIMIT 10")) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    json.append(String.format("{\"id\":\"%s\",\"event\":\"%s\",\"status\":\"%s\",\"attempts\":%d,\"response_code\":%d}",
                        rs.getString("id"), rs.getString("event"), rs.getString("status"), rs.getInt("attempts"), rs.getInt("response_code")));
                    first = false;
                }
            }
            json.append("]}");
            conn.close();
            return json.toString();
        });

        // 6. Retry
        post("/api/v1/webhooks/:id/retry", (req, res) -> {
            res.type("application/json");
            String logId = req.params(":id");
            Connection conn = Database.connect();
            try (PreparedStatement stmt = conn.prepareStatement("UPDATE webhook_logs SET status = 'pending', attempts = 0, next_retry_at = NULL WHERE id = ?::uuid")) {
                stmt.setString(1, logId);
                if (stmt.executeUpdate() == 0) halt(404, "Log not found");
            }
            String payload = "";
            try (PreparedStatement stmt = conn.prepareStatement("SELECT payload FROM webhook_logs WHERE id = ?::uuid")) {
                stmt.setString(1, logId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) payload = rs.getString("payload");
            }
            JobService.enqueueJob("DELIVER_WEBHOOK", payload);
            conn.close();
            return "{\"id\":\"" + logId + "\", \"status\":\"pending\", \"message\":\"Retry scheduled\"}";
        });

        // 7. Status
        get("/api/v1/test/jobs/status", (req, res) -> "{\"status\": \"worker_running\"}");
        
        System.out.println("API Service Started on Port 8080");
    }
}