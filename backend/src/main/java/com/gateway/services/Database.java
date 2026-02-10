package com.gateway.services;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Database {
    public static Connection connect() throws SQLException {
        try {
            String envUrl = System.getenv("DATABASE_URL");
            
            // 1. Local Fallback (if running outside Docker)
            if (envUrl == null || envUrl.isEmpty()) {
                return DriverManager.getConnection("jdbc:postgresql://localhost:5432/payment_gateway", "gateway_user", "gateway_pass");
            }

            // 2. Docker URL Parsing (postgresql://user:pass@host:port/db)
            // Java JDBC needs: jdbc:postgresql://host:port/db?user=u&password=p
            URI uri = new URI(envUrl);
            String[] userInfo = uri.getUserInfo().split(":"); // Splits "gateway_user:gateway_pass"
            String username = userInfo[0];
            String password = userInfo[1];
            
            String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + uri.getPort() + uri.getPath();
            
            return DriverManager.getConnection(jdbcUrl, username, password);
        } catch (Exception e) {
            e.printStackTrace();
            throw new SQLException("DB Connection Failed: " + e.getMessage());
        }
    }
}