package com.gateway.jobs;

import com.gateway.services.Database;
import com.gateway.services.JobService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class ProcessPaymentJob {
    public static void execute(String paymentId) {
        System.out.println("Processing Payment: " + paymentId);
        try (Connection conn = Database.connect()) {
            String method = "card";
            String merchantId = null;
            
            try (PreparedStatement stmt = conn.prepareStatement("SELECT method, merchant_id FROM payments WHERE id = ?")) {
                stmt.setString(1, paymentId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    method = rs.getString("method");
                    merchantId = rs.getString("merchant_id");
                } else return;
            }

            // Simulate Delay
            Thread.sleep(5000 + (long)(Math.random() * 5000));

            // Determine Success
            boolean success = "upi".equalsIgnoreCase(method) ? Math.random() < 0.90 : Math.random() < 0.95;
            String status = success ? "success" : "failed";

            // Update DB
            try (PreparedStatement stmt = conn.prepareStatement("UPDATE payments SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?")) {
                stmt.setString(1, status);
                stmt.setString(2, paymentId);
                stmt.executeUpdate();
            }

            // Enqueue Webhook
            String webhookData = "{\"merchant_id\":\"" + merchantId + "\", \"event\":\"payment." + status + "\", \"data\": {\"payment_id\":\"" + paymentId + "\"}}";
            JobService.enqueueJob("DELIVER_WEBHOOK", webhookData);
        } catch (Exception e) { e.printStackTrace(); }
    }
}