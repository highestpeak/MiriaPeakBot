package com.highestpeak.util;

import com.highestpeak.PeakBot;
import com.highestpeak.config.Config;

import java.util.function.Supplier;

public class LogUtil {
    public static void info(String message) {
        if (PeakBot.isPluginLoaded()) {
            PeakBot.INSTANCE.getLogger().info(message);
        }
    }

    public static void error(String message, Throwable e) {
        if (PeakBot.isPluginLoaded()) {
            PeakBot.INSTANCE.getLogger().error(message, e);
        }
    }

    public static void debug(Supplier<String> messageSupplier) {
        if (PeakBot.isPluginLoaded() && Config.get().isDebug()) {
            PeakBot.INSTANCE.getLogger().debug(messageSupplier.get());
        }
    }

    public static void warn(String message) {
        if (PeakBot.isPluginLoaded()) {
            PeakBot.INSTANCE.getLogger().warning(message);
        }
    }
}
