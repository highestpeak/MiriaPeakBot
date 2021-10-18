package com.highestpeak.commond;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.MoreExecutors;
import com.highestpeak.PeakBot;
import com.highestpeak.config.ApiConfig;
import com.highestpeak.config.Config;
import com.highestpeak.entity.CommandChatType;
import com.highestpeak.entity.CommandUserType;
import com.highestpeak.entity.ImageVo;
import com.highestpeak.entity.MsgEventParams;
import com.highestpeak.util.BotMessageHelper;
import com.highestpeak.util.CommonUtil;
import com.highestpeak.util.LogUtil;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.message.data.Image;
import net.mamoe.mirai.utils.ExternalResource;
import org.apache.commons.lang3.StringUtils;

import java.io.FileInputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public abstract class ApiCommand extends PeakCommand {

    private ApiConfig apiConfig;

    private List<Object> cacheObjectList;

    private final Object cacheListLock = new Object();

    private static final ExecutorService CACHE_LOAD_EXECUTOR = MoreExecutors.newDirectExecutorService();

    private static final ScheduledExecutorService SCHEDULED_LOAD_CACHE_EXECUTOR = new ScheduledThreadPoolExecutor(1);

    private static AtomicBoolean alreadyInitScheduledLoadCacheExecutor = new AtomicBoolean(false);

    private AtomicBoolean updateTaskSubimt = new AtomicBoolean(false);

    public ApiCommand(List<String> rule) {
        super(rule);

        addAllowChatType(CommandChatType.FRIEND_CHAT.getType());
        addAllowChatType(CommandChatType.GROUP_TEMP_CHAT.getType());
        addAllowChatType(CommandChatType.GROUP_CHAT.getType());
        addAllowChatType(CommandChatType.STRANGER_CHAT.getType());

        addAllowSendUserType(CommandUserType.NORMAL_USER.getType());
        addAllowSendUserType(CommandUserType.GROUP_ADMINISTRATOR.getType());
        addAllowSendUserType(CommandUserType.GROUP_OWNER.getType());
        addAllowSendUserType(CommandUserType.BOT_ADMINISTRATOR.getType());
    }

    @Override
    public boolean isEnable() {
        if (apiConfig == null) {
            return false;
        }
        return apiConfig.isEnable();
    }

    @Override
    public boolean matchName(String name) {
        return StringUtils.equalsIgnoreCase(name, apiConfig.getName());
    }

    @Override
    public void setConfig(Object config) {
        if (config instanceof ApiConfig) {
            apiConfig = (ApiConfig) config;
            if (!apiConfig.isEnableCache()) {
                LogUtil.info("config not enable cache. name:" + apiConfig.getName());
                return;
            }
            cacheObjectList = initCache();

            if (!alreadyInitScheduledLoadCacheExecutor.getAndSet(true)) {
                // 每隔10s自动load一次cache
                Runnable cacheScheduleLoad = () -> {
                    if (cacheObjectList.size() <= apiConfig.getCordonCacheNum() && !updateTaskSubimt.get()) {
                        updateTaskSubimt.compareAndSet(false, true);
                        CACHE_LOAD_EXECUTOR.submit(this::loadCache);
                    }
                };
                SCHEDULED_LOAD_CACHE_EXECUTOR.scheduleWithFixedDelay(cacheScheduleLoad, 0, 10, TimeUnit.SECONDS);
            }
        }
    }

    public ApiConfig getConfig() {
        return apiConfig;
    }

    /**
     * 从数据库读取没有发送过的图片
     */
    private List<Object> initCache() {
        List<Object> cache = Lists.newArrayList();

        List<String> fileNames = CommonUtil.allFilesName("jpg", getConfig().getName());
        List<String> fileNotSends = fileNames.stream().filter(CommonUtil::isImageNotSend).collect(Collectors.toList());
        List<ImageVo> imageVoList = fileNotSends.stream()
                .map(fileName -> ImageVo.builder()
                        .url("local cache")
                        .localImageFilePath(fileName)
                        .build()
                ).collect(Collectors.toList());
        cache.addAll(imageVoList);

        return cache;
    }

    Object getCached() {
        if (cacheObjectList.size() <= apiConfig.getCordonCacheNum() && !updateTaskSubimt.get()) {
            updateTaskSubimt.compareAndSet(false, true);
            CACHE_LOAD_EXECUTOR.submit(this::loadCache);
        }
        return cacheObjectList.stream().findAny().orElseGet(() -> {
            List<Object> cacheObjects = getCacheObjects(1);
            if (cacheObjects.isEmpty()) {
                return null;
            }
            return cacheObjects.get(0);
        });
    }

    private void loadCache() {
        // 只有任务呗提交才会进行load
        if (updateTaskSubimt.get()) {
            List cacheObjects = getCacheObjects(apiConfig.getCacheNum() - cacheObjectList.size());
            synchronized (cacheListLock) {
                cacheObjectList.addAll(cacheObjects);
            }
            updateTaskSubimt.compareAndSet(true, false);
        }
    }

    public abstract List<Object> getCacheObjects(int count);

    @Override
    public void enableCommandOn(Long group, Long user) {
        if (group != null && group != MsgEventParams.FRIEND_TYPE_DEFAULT_GROUP_VALUE) {
            List<Long> enableGroup = apiConfig.getEnableGroup();
            if (!enableGroup.contains(group)) {
                enableGroup.add(group);
            }
            Config.prepareUpdateConfig();
        } else if (user != null) {
            List<Long> enableUsers = apiConfig.getEnableUsers();
            if (!enableUsers.contains(user)) {
                enableUsers.add(user);
            }
            Config.prepareUpdateConfig();
        }
    }

    @Override
    public void disableCommandOn(Long group, Long user) {
        if (group != null) {
            apiConfig.getEnableGroup().remove(group);
            Config.prepareUpdateConfig();
        } else if (user != null) {
            apiConfig.getEnableUsers().remove(user);
            Config.prepareUpdateConfig();
        }
    }

    @Override
    public boolean matchGroup(Long group) {
        return apiConfig.getEnableGroup().contains(group);
    }

    @Override
    public boolean matchFriend(Long friend) {
        return apiConfig.getEnableUsers().contains(friend);
    }

    protected void sendImage(Contact contact, String name) {
        Stopwatch watch = Stopwatch.createStarted();
        Object cached = getCached();
        LogUtil.debug(() -> String.format("获取缓存对象耗时: %sms", watch.elapsed(TimeUnit.MILLISECONDS)));
        if (cached == null) {
            BotMessageHelper.sendMsg(contact, "图片发送错误，未能生成图片");
            watch.stop();
            return;
        }
        ImageVo cachedImage = (ImageVo) cached;
        try {
            FileInputStream is = new FileInputStream(cachedImage.getLocalImageFilePath());
            watch.reset();
            Image uploadImage = ExternalResource.uploadAsImage(is,
                    PeakBot.getCURRENT_BOT().getGroups().stream().findAny().get()
            );
            LogUtil.debug(() -> String.format("uploadAsImage耗时: %sms", watch.elapsed(TimeUnit.MILLISECONDS)));
            BotMessageHelper.sendMsg(contact, "[mirai:image:" + uploadImage.getImageId() + "]");
            watch.reset();
            synchronized (cacheListLock) {
                cacheObjectList.remove(cached);
            }
            LogUtil.debug(() -> String.format("cacheObjectList remove耗时: %sms", watch.elapsed(TimeUnit.MILLISECONDS)));
            CommonUtil.markImageAlreadySend(cachedImage.getLocalImageFilePath());
        } catch (Exception e) {
            LogUtil.error(String.format("图片发送错误. name: %s, api: %s", name, cachedImage.getUrl()), e);
            BotMessageHelper.sendMsg(contact, "图片发送错误");
        }
        if (watch.isRunning()) {
            watch.stop();
        }
    }

}
