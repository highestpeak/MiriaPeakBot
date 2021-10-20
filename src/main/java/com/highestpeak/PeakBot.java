package com.highestpeak;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.highestpeak.commond.*;
import com.highestpeak.config.ApiConfig;
import com.highestpeak.config.Config;
import com.highestpeak.config.CrawlConfig;
import com.highestpeak.entity.Constants;
import com.highestpeak.event.FriendMsgEventHandler;
import com.highestpeak.event.GroupMsgEventHandler;
import com.highestpeak.event.NewFriendEventHandler;
import com.highestpeak.util.DatabaseHelper;
import com.highestpeak.util.LogUtil;
import lombok.Getter;
import lombok.SneakyThrows;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.console.plugin.jvm.JavaPlugin;
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescriptionBuilder;
import net.mamoe.mirai.event.Event;
import net.mamoe.mirai.event.EventChannel;
import net.mamoe.mirai.event.GlobalEventChannel;
import net.mamoe.mirai.event.events.BotOnlineEvent;
import net.mamoe.mirai.event.events.FriendMessageEvent;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.event.events.NewFriendRequestEvent;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 插件入口 部分全局变量
 * <p>
 * 参考了 suijisetu 项目
 *
 * @author highestpeak
 */
@SuppressWarnings("SpellCheckingInspection")
public final class PeakBot extends JavaPlugin {
    /**
     * WARNING: INSTANCE 字段必须设置为 public, 否则 mirai-console 在反射时会失败.
     */
    @Getter
    public static final PeakBot INSTANCE = new PeakBot();

    @Getter
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Getter
    private static Bot CURRENT_BOT;

    @Getter
    private static boolean pluginLoaded;

    @Getter
    private static Map<Class<? extends PeakCommand>, List<? extends PeakCommand>> peakCommandMap;

    @Getter
    private static DatabaseHelper databaseHelper;

    private static final Object MAP_LOCK = new Object();

    private PeakBot() {
        super(new JvmPluginDescriptionBuilder("com.highestpeak.PeakBot", Constants.VERSION)
                .name(Constants.APP_NAME)
                .author(Constants.AUTHOR)
                .info(Constants.DESCRIPTION)
                .build());
    }

    @SneakyThrows
    @Override
    public void onEnable() {
        LogUtil.info("PeakBot startup. Start init PeakBot.");

        // https://stackoverflow.com/questions/44821561/how-to-set-proxy-host-on-httpclient-request-in-java
        System.setProperty("https.protocols", "TLSv1,TLSv1.1,TLSv1.2");

        pluginLoaded = true;

        LogUtil.info("PeakBot initConfig. file: ApplicationConfig.json");
        Config.init();
        LogUtil.info("PeakBot initConfig finish.");

        LogUtil.info("PeakBot init sqlite start.");
        initSqlite();
        LogUtil.info("PeakBot init sqlite finish.");

        LogUtil.info("PeakBot init command start.");
        loadCommandMap();
        LogUtil.info("PeakBot init command finish.");

        LogUtil.info("PeakBot subscribe events start.");
        EventChannel<Event> eventChannel = GlobalEventChannel.INSTANCE.parentScope(this);
        // 机器人登陆事件
        eventChannel.subscribeAlways(BotOnlineEvent.class, event -> {
            LogUtil.info("BotOnlineEvent.");
            CURRENT_BOT = CURRENT_BOT == null ? event.getBot() : CURRENT_BOT;
        });
        // 监听群消息
        GroupMsgEventHandler groupMsgEventHandler = new GroupMsgEventHandler();
        eventChannel.subscribeAlways(GroupMessageEvent.class, groupMsgEventHandler::handleEvent);
        // 监听好友消息
        FriendMsgEventHandler friendMsgEventHandler = new FriendMsgEventHandler();
        eventChannel.subscribeAlways(FriendMessageEvent.class, friendMsgEventHandler::handleEvent);
        // 接收添加好友请求事件
        NewFriendEventHandler newFriendEventHandler = new NewFriendEventHandler();
        eventChannel.subscribeAlways(NewFriendRequestEvent.class, newFriendEventHandler::handleEvent);
        LogUtil.info("PeakBot subscribe events finish.");

        LogUtil.info("PeakBot startup. Finish init PeakBot.");
    }

    private void initSqlite() throws SQLException, ClassNotFoundException {
        DatabaseHelper nextDbHelper = new DatabaseHelper(Config.sqliteFilePath());
        nextDbHelper.executeUpdate("CREATE TABLE IF NOT EXISTS id_table(" +
                "row_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "key varchar(32)," +
                "id varchar(512)," +
                "file_name TEXT," +
                "already_send BOOLEAN" +
                ");");
        databaseHelper = nextDbHelper;
    }

    public static void loadCommandMap() {
        synchronized (MAP_LOCK) {
            // todo WARNING: peakCommandMap init
            Map<Class<? extends PeakCommand>, List<? extends PeakCommand>> nextCommandMap = Maps.newHashMap();
            Config config = Config.get();

            // super command
            List<String> superCommands = config.getSpecialCommands().get(Constants.SUPER_COMMAND);
            nextCommandMap.put(SuperCommand.class, Collections.singletonList(new SuperCommand(superCommands)));

            // image api command
            List<ApiConfig> apiImageConfigs = config.getApiImageConfigs();
            List<ApiImageCommand> apiImageCommands = Lists.newArrayListWithCapacity(apiImageConfigs.size());
            for (ApiConfig apiImageConfig : apiImageConfigs) {
                if (!apiImageConfig.isEnable()) {
                    continue;
                }
                ApiImageCommand apiImageCommand = new ApiImageCommand(apiImageConfig.getCommands());
                apiImageCommand.setConfig(apiImageConfig);
                apiImageCommands.add(apiImageCommand);
            }
            nextCommandMap.put(ApiImageCommand.class, apiImageCommands);

            // crawl image command
            List<CrawlConfig> crawlConfigs = config.getCrawlImageConfigs();
            List<CrawlImageCommand> crawlImageCommands = Lists.newArrayListWithCapacity(crawlConfigs.size());
            for (CrawlConfig crawlConfig : crawlConfigs) {
                if (!crawlConfig.isEnable()) {
                    continue;
                }
                CrawlImageCommand crawlImageCommand = new CrawlImageCommand(crawlConfig.getCommands());
                crawlImageCommand.setConfig(crawlConfig);
                crawlImageCommands.add(crawlImageCommand);
            }
            nextCommandMap.put(CrawlImageCommand.class, crawlImageCommands);

            // apiTextConfigs
            List<ApiConfig> apiTextConfigs = config.getApiTextConfigs();
            List<ApiTextCommand> apiTextCommands = Lists.newArrayListWithCapacity(apiTextConfigs.size());
            for (ApiConfig apiTextConfig : config.getApiTextConfigs()) {
                if (!apiTextConfig.isEnable()) {
                    continue;
                }
                ApiTextCommand apiTextCommand = new ApiTextCommand(apiTextConfig.getCommands());
                apiTextCommand.setConfig(apiTextConfig);
                apiTextCommands.add(apiTextCommand);
            }
            nextCommandMap.put(ApiTextCommand.class, apiTextCommands);
            peakCommandMap = nextCommandMap;
        }
    }

    public static List<PeakCommand> allPeakCommands() {
        return peakCommandMap.values()
                .stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    @Override
    public void onDisable() {
        LogUtil.info("PeakBot will be Disable.");
        databaseHelper.destroyed();
        LogUtil.info("PeakBot sqlite is Disable.");
    }

}
