package com.example.wea_trav_assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "amap")
public class AmapProperties {
    private String baseUrl = "https://restapi.amap.com";
    private String key = "";
    private int timeout = 5000;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public int getTimeout() { return timeout; }
    public void setTimeout(int timeout) { this.timeout = Math.max(1000, timeout); }
}
