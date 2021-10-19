package com.highestpeak.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.highestpeak.PeakBot;
import com.highestpeak.entity.Constants;
import com.highestpeak.entity.PeakBotException;
import com.highestpeak.util.LogUtil;
import lombok.Data;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * todo WARNING：
 * 增加 config 需要注意在 updateConfig saveConfig newConfigInstance readExistConfig createDefaultConfigAndWriteIt 中写下自己的逻辑
 */
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
     * 从文件中加载
     */
    @JsonIgnore
    private List<ApiConfig> apiImageConfigs = Lists.newArrayList(ApiConfig.DEFAULT);

    public static final String API_IMAGE_CONFIG_FILE_PREFIX = "apiImageConfig_";

    /**
     * 爬虫的图片配置
     */
    @JsonIgnore
    private List<CrawlConfig> crawlImageConfigs = Lists.newArrayList(CrawlConfig.DEFAULT);

    public static final String CRAWL_IMAGE_CONFIG_FILE_PREFIX = "crawlImageConfig_";

    /**
     * 文字api配置
     * <p>
     * 可以随时添加新的文字api配置
     */
    @JsonIgnore
    private List<ApiConfig> apiTextConfigs = Lists.newArrayList(ApiConfig.DEFAULT);

    public static final String API_TEXT_CONFIG_FILE_PREFIX = "apiTextConfig_";

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
            saveConfig(INSTANCE);
            INSTANCE = newConfigInstance();
            prepareUpdateConfig.set(false);
            PeakBot.loadCommandMap();
        }
    }

    private static void saveConfig(Config config) {
        try {
            clearConfigDir();
            // write config
            saveFieldConfig(
                    configFilePath(Constants.CONFIG_FILE_NAME),
                    config
            );
            // write apiImageConfigs to json file
            config.getApiImageConfigs().forEach(apiConfig ->
                    saveFieldConfig(
                            configFilePath(API_IMAGE_CONFIG_FILE_PREFIX, apiConfig.getName()),
                            apiConfig
                    )
            );
            // write crawlImageConfigs to json file
            config.getCrawlImageConfigs().forEach(crawlConfig ->
                    saveFieldConfig(
                            configFilePath(CRAWL_IMAGE_CONFIG_FILE_PREFIX, crawlConfig.getName()),
                            crawlConfig
                    )
            );
            // write apiTextConfigs to json file
            config.getApiTextConfigs().forEach(apiConfig ->
                    saveFieldConfig(
                            configFilePath(API_TEXT_CONFIG_FILE_PREFIX, apiConfig.getName()),
                            apiConfig
                    )
            );
        } catch (Exception e) {
            LogUtil.error("更新配置失败.", e);
        }
    }

    private static Config newConfigInstance() {
        try {
            File configFile = new File(configFilePath(Constants.CONFIG_FILE_NAME));
            if (!configFile.exists()) {
                return createDefaultConfigAndWriteIt();
            }

            return readExistConfig();
        } catch (Exception e) {
            LogUtil.error("init config error.", e);
            throw new PeakBotException();
        }
    }

    private static Config readExistConfig() throws IOException {
        ObjectMapper mapper = PeakBot.getMAPPER();
        String configJson = FileUtils.readFileToString(
                new File(configFilePath(Constants.CONFIG_FILE_NAME)),
                StandardCharsets.UTF_8
        );
        Config config = mapper.readValue(configJson, Config.class);

        // read all config
        File file = new File(configDirPath());
        File[] configFiles = file.listFiles();
        if (configFiles == null) {
            return createDefaultConfigAndWriteIt();
        }
        config.apiImageConfigs = Lists.newArrayList();
        config.crawlImageConfigs = Lists.newArrayList();
        config.apiTextConfigs = Lists.newArrayList();
        for (File configFile : configFiles) {
            String name = configFile.getName();
            String configFileJson = FileUtils.readFileToString(configFile, StandardCharsets.UTF_8);
            if (name.startsWith(API_IMAGE_CONFIG_FILE_PREFIX)) {
                config.apiImageConfigs.add(
                        mapper.readValue(configFileJson, ApiConfig.class)
                );
            } else if (name.startsWith(CRAWL_IMAGE_CONFIG_FILE_PREFIX)) {
                config.crawlImageConfigs.add(
                        mapper.readValue(configFileJson, CrawlConfig.class)
                );
            } else if (name.startsWith(API_TEXT_CONFIG_FILE_PREFIX)) {
                config.apiTextConfigs.add(
                        mapper.readValue(configFileJson, ApiConfig.class)
                );
            }

        }

        return config;
    }

    private static Config createDefaultConfigAndWriteIt() throws IOException {
        Config config = new Config();

        config.specialCommands = Maps.newHashMap();
        config.specialCommands.put(Constants.SUPER_COMMAND, Lists.newArrayList("super", "admin"));

        config.authorityConfig = new AuthorityConfig();

        config.proxyConfig = ProxyConfig.DEFAULT;

        config.apiImageConfigs = Lists.newArrayList(ApiConfig.DEFAULT);

        config.crawlImageConfigs = Lists.newArrayList(CrawlConfig.DEFAULT);

        config.apiTextConfigs = Lists.newArrayList(ApiConfig.DEFAULT);

        config.sendMsgMaxLength = 4500;

        saveConfig(config);

        return config;
    }

    public static void saveFieldConfig(String configFilePath, Object config) {
        try {
            ObjectMapper mapper = PeakBot.getMAPPER();
            FileUtils.writeStringToFile(
                    new File(configFilePath),
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(config),
                    StandardCharsets.UTF_8
            );
        } catch (Exception e) {
            LogUtil.error("save default config error.", e);
        }
    }

    public static String sqliteFilePath() {
        return getJavaRunPath() + File.separator
                + "config" + File.separator
                + Constants.APP_NAME + File.separator
                + Constants.SQLITE_FILE_NAME;
    }

    public static void clearConfigDir() {
        File file = new File(configDirPath());
        File[] configFiles = file.listFiles();
        if (configFiles == null) {
            return;
        }
        for (File configFile : configFiles) {
            String fileType = Files.getFileExtension(configFile.getName());
            if (StringUtils.equalsIgnoreCase(fileType, "json")) {
                configFile.delete();
            }
        }
    }

    public static String configFilePath(String configPrefix, String configName) {
        return configFilePath(configPrefix + configName + ".json");
    }

    /**
     * 配置文件的路径
     */
    public static String configFilePath(String fileName) {
        return configDirPath() + File.separator + fileName;
    }

    /**
     * 配置文件所在目录
     */
    public static String configDirPath() {
        return getJavaRunPath() + File.separator
                + "config" + File.separator
                + Constants.APP_NAME;
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
