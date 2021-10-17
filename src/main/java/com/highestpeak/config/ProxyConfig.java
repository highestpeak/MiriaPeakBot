package com.highestpeak.config;

import lombok.Data;

@Data
public class ProxyConfig {
    public static final ProxyConfig DEFAULT = new ProxyConfig();

    private boolean enable = false;
    private boolean shadowsocks = true;
    private String host = "你的代理的IP";
    private int port = 8080;
    private String scheme = "http";
}
