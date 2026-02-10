package com.gateway.jobs;

import com.gateway.services.Database;
import com.gateway.services.JobService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class ProcessRefundJob {
    public static void execute(String refundId) {
        System.out.println("Processing Refund: " + refundId);
        try (Connection conn = Database.connect()) {
            String merchantId = null;
            try (PreparedStatement stmt = conn.prepareStatement("SELECT merchant_id FROM refunds WHERE id = ?")) {
                stmt.setString(1, refundId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) merchantId = rs.getString("merchant_id");
                else return;
            }

            // Simulate Processing
            Thread.sleep(3000 + (long)(Math.random() * 2000));

            // Update Status
            try (PreparedStatement stmt = conn.prepareStatement("UPDATE refunds SET status = 'processed', processed_at = CURRENT_TIMESTAMP WHERE id = ?")) {
                stmt.setString(1, refundId);
                stmt.executeUpdate();
            }

            // Enqueue Webhook
            String webhookData = "{\"merchant_id\":\"" + merchantId + "\", \"event\":\"refund.processed\", \"data\": {\"refund_id\":\"" + refundId + "\"}}";
            JobService.enqueueJob("DELIVER_WEBHOOK", webhookData);
        } catch (Exception e) { e.printStackTrace(); }
    }
}