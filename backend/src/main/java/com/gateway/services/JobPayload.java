package com.gateway.services;

public class JobPayload {
    private String type;
    private String data;

    public JobPayload() {}
    public JobPayload(String type, String data) {
        this.type = type;
        this.data = data;
    }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
}