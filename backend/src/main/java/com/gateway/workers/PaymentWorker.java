package com.gateway.workers;

import com.gateway.services.JobService;
import com.gateway.services.JobPayload;
import com.gateway.jobs.ProcessPaymentJob;
import com.gateway.jobs.DeliverWebhookJob;
import com.gateway.jobs.ProcessRefundJob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class PaymentWorker {

    public static void main(String[] args) {
        System.out.println("Worker Service Started...");
        startRetryPoller();

        while (true) {
            JobPayload job = JobService.waitForJob();
            if (job != null) {
                try {
                    switch (job.getType()) {
                        case "PROCESS_PAYMENT":
                            ProcessPaymentJob.execute(job.getData().replace("\"", "")); 
                            break;
                        case "DELIVER_WEBHOOK":
                            DeliverWebhookJob.execute(job.getData());
                            break;
                        case "PROCESS_REFUND":
                            ProcessRefundJob.execute(job.getData().replace("\"", ""));
                            break;
                        default:
                            System.out.println("Unknown job type: " + job.getType());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void startRetryPoller() {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(10000); // Check every 10 seconds for testing responsiveness
                    System.out.println("Poller: Checking for retriable webhooks...");
                    try (Connection conn = com.gateway.services.Database.connect()) {
                        String sql = "SELECT id, payload, attempts FROM webhook_logs WHERE status = 'pending' AND next_retry_at < CURRENT_TIMESTAMP";
                        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                            ResultSet rs = stmt.executeQuery();
                            while (rs.next()) {
                                int attempts = rs.getInt("attempts");
                                if (attempts >= 5) {
                                    conn.createStatement().execute("UPDATE webhook_logs SET status = 'failed' WHERE id = '" + rs.getString("id") + "'::uuid");
                                } else {
                                    // Re-queue
                                    JobService.enqueueJob("DELIVER_WEBHOOK", rs.getString("payload"));
                                    // Mark processed temporarily to avoid double queueing before worker picks it up
                                    conn.createStatement().execute("UPDATE webhook_logs SET next_retry_at = CURRENT_TIMESTAMP + INTERVAL '1 hour' WHERE id = '" + rs.getString("id") + "'::uuid");
                                }
                            }
                        }
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }
        }).start();
    }
}