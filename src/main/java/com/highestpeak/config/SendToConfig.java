package com.highestpeak.config;

import lombok.Data;

@Data
public class SendToConfig {
    public static final SendToConfig DEFAULT = new SendToConfig();

    public boolean enable = false;
    public long delayTimeMS = 0;
}
