package com.highestpeak.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.highestpeak.PeakBot;
import com.highestpeak.entity.Constants;
import com.highestpeak.entity.PeakBotException;
import com.highestpeak.util.LogUtil;
import lombok.Data;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;

@Data
public class Config {

    private static volatile Config INSTANCE;

    private static AtomicBoolean prepareUpdateConfig = new AtomicBoolean(false);

    private static final BlockingQueue<Object> UPDATE_REQUEST_QUEUE = new LinkedBlockingDeque<>();

    private boolean debug = false;

    /**
     * 一些特殊的指令，不方便单独建一个新的类的 <br/>
     * key 来自常量类 constants
     */
    private Map<String, List<String>> specialCommands = Maps.newHashMap();

    /**
     * 权限配置
     */
    private AuthorityConfig authorityConfig = new AuthorityConfig();

    /**
     * 代理配置
     */
    private ProxyConfig proxyConfig = ProxyConfig.DEFAULT;

    /**
     * 图片配置
     * <p>
     * 可以随时添加新的图片配置
     */
    private List<ApiConfig> apiImageConfigs = Lists.newArrayList();

    /**
     * 爬虫的图片配置
     */
    private List<CrawlConfig> crawlImageConfigs = Lists.newArrayList();

    /**
     * 文字api配置
     * <p>
     * 可以随时添加新的文字api配置
     */
    private List<ApiConfig> apiTextConfigs = Lists.newArrayList();

    private long sendMsgMaxLength = 4500;

    /**
     * 2s 后更新
     */
    private static final Long CONFIG_UPDATE_DELAY = 2000L;

    private static final Thread UPDATE_THREAD = new Thread(() -> {
        while (!Thread.interrupted()) {
            try {
                UPDATE_REQUEST_QUEUE.take();
                Thread.sleep(CONFIG_UPDATE_DELAY);
                if (prepareUpdateConfig.get()) {
                    updateConfig();
                }
            } catch (Exception e) {
                LogUtil.error("exception in update config thread while loop.", e);
            }
        }
    });

    static {
        UPDATE_THREAD.setName("config-update-check-thread");
        UPDATE_THREAD.start();
    }

    private Config() {
    }

    public static Config get() {
        if (INSTANCE == null) {
            init();
        }
        return INSTANCE;
    }

    public static void init() {
        if (INSTANCE == null) {
            synchronized (Config.class) {
                if (INSTANCE == null) {
                    INSTANCE = newConfigInstance();
                }
            }
        }
    }

    /**
     * 准备去重新加载配置：延迟执行，短时间多个更新只执行一次
     */
    public static void prepareUpdateConfig() {
        if (!prepareUpdateConfig.get()) {
            // 短时间内第一次触发更新
            prepareUpdateConfig.compareAndSet(false, true);
            UPDATE_REQUEST_QUEUE.offer(new Object());
        }
        // 已经触发过更新
    }

    private static void updateConfig() {
        synchronized (Config.class) {
            // 重新刷新config后必须重新reload peakBot 的策略
            saveConfig();
            INSTANCE = newConfigInstance();
            prepareUpdateConfig.set(false);
            PeakBot.loadCommandMap();
        }
    }

    private static void saveConfig() {
        try {
            ObjectMapper mapper = PeakBot.getMAPPER();
            String configFilePath = configFilePath();
            File configFile = new File(configFilePath);
            String configJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(INSTANCE);
            FileUtils.writeStringToFile(configFile, configJson, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LogUtil.error("更新配置失败.", e);
        }
    }

    private static Config newConfigInstance() {
        try {
            ObjectMapper mapper = PeakBot.getMAPPER();
            String configFilePath = configFilePath();
            File configFile = new File(configFilePath);
            if (!configFile.exists()) {
                Config defaultConfig = createDefaultConfig();
                String configJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(defaultConfig);
                FileUtils.writeStringToFile(configFile, configJson, StandardCharsets.UTF_8);
                return defaultConfig;
            }

            String configJson = FileUtils.readFileToString(configFile, StandardCharsets.UTF_8);
            return mapper.readValue(configJson, Config.class);
        } catch (Exception e) {
            LogUtil.error("init config error.", e);
            throw new PeakBotException();
        }
    }

    /**
     * todo WARNING：增加 config 需要注意在 createDefaultConfig 中写下自己的逻辑
     */
    private static Config createDefaultConfig() {
        Config config = new Config();

        config.specialCommands = Maps.newHashMap();
        config.specialCommands.put(Constants.SUPER_COMMAND, Lists.newArrayList("super", "admin"));

        config.authorityConfig = new AuthorityConfig();

        config.proxyConfig = ProxyConfig.DEFAULT;

        config.apiImageConfigs = Lists.newArrayList(ApiConfig.DEFAULT);

        config.crawlImageConfigs = Lists.newArrayList(CrawlConfig.DEFAULT);

        config.apiTextConfigs = Lists.newArrayList(ApiConfig.DEFAULT);

        config.sendMsgMaxLength = 4500;

        return config;
    }

    public static String sqliteFilePath() {
        return getJavaRunPath() + File.separator
                + "config" + File.separator
                + Constants.APP_NAME + File.separator
                + Constants.SQLITE_FILE_NAME;
    }

    /**
     * 配置文件的路径
     */
    public static String configFilePath() {
        return getJavaRunPath() + File.separator
                + "config" + File.separator
                + Constants.APP_NAME + File.separator
                + Constants.CONFIG_FILE_NAME;
    }

    /**
     * 应用程序程序的运行路径
     * <p>
     * 该方法也有以下几种实现原理: <br/>
     * String result = Class.class.getClass().getResource("/").getPath(); <br/>
     * String result = System.getProperty("user.dir");
     */
    public static String getJavaRunPath() {
        // 利用 new File()相对路径原理
        return new File("").getAbsolutePath();
    }

}
