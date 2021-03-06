package com.highestpeak.commond;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.highestpeak.PeakBot;
import com.highestpeak.config.ApiConfig;
import com.highestpeak.config.ApiStrConfig;
import com.highestpeak.config.Config;
import com.highestpeak.config.CrawlConfig;
import com.highestpeak.entity.CommandChatType;
import com.highestpeak.entity.CommandUserType;
import com.highestpeak.entity.ImageVo;
import com.highestpeak.entity.MsgEventParams;
import com.highestpeak.util.BotMessageHelper;
import com.highestpeak.util.CommonUtil;
import com.highestpeak.util.IdUtil;
import com.highestpeak.util.LogUtil;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.message.data.Image;
import net.mamoe.mirai.utils.ExternalResource;
import org.apache.commons.lang3.StringUtils;

import java.io.FileInputStream;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public abstract class ApiCommand extends PeakCommand {

    public static final Set<Class<?>> CACHE_ENABLE_CLASS_SET = ImmutableSet.of(
            ApiConfig.class, CrawlConfig.class
    );

    private ApiConfig apiConfig;

    private List<Object> cacheObjectList;

    private final Object cacheListLock = new Object();

    private static final ExecutorService CACHE_LOAD_EXECUTOR = Executors.newFixedThreadPool(
            4,
            new ThreadFactoryBuilder().setNameFormat("cache-load-executor").build()
    );

    private static final ScheduledExecutorService SCHEDULED_LOAD_CACHE_EXECUTOR =
            new ScheduledThreadPoolExecutor(
                    4,
                    new ThreadFactoryBuilder().setNameFormat("scheduled-load-cache-executor").build()
            );

    private static int taskCount = 0;

    private final AtomicBoolean alreadyInitScheduledLoadCacheExecutor = new AtomicBoolean(false);

    private final AtomicBoolean updateTaskSubimt = new AtomicBoolean(false);

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
        if (CACHE_ENABLE_CLASS_SET.contains(config.getClass())) {
            apiConfig = (ApiConfig) config;
            if (!apiConfig.isEnableCache()) {
                LogUtil.info("config not enable cache. name:" + apiConfig.getName());
                return;
            }
            cacheObjectList = initCache();

            if (!alreadyInitScheduledLoadCacheExecutor.getAndSet(true)) {
                // ??????10s??????load??????cache
                Runnable cacheScheduleLoad = () -> {
                    if (cacheObjectList.size() <= apiConfig.getCordonCacheNum() && !updateTaskSubimt.get()) {
                        updateTaskSubimt.compareAndSet(false, true);
                        CACHE_LOAD_EXECUTOR.submit(this::loadCache);
                    }
                };

                taskCount++;
                SCHEDULED_LOAD_CACHE_EXECUTOR.scheduleWithFixedDelay(
                        cacheScheduleLoad,
                        10L * taskCount,
                        10,
                        TimeUnit.SECONDS
                );
            }
        }
    }

    public ApiConfig getConfig() {
        return apiConfig;
    }

    /**
     * ??????????????????????????????????????????
     */
    private List<Object> initCache() {
        List<Object> cache = Lists.newArrayList();

        List<String> fileNames = CommonUtil.allFilesName("jpg", getConfig().getName());
        List<String> fileNotSends = fileNames.stream().filter(CommonUtil::isImageNotSend).collect(Collectors.toList());
        List<ImageVo> imageVoList = fileNotSends.stream()
                .map(fileName -> ImageVo.builder()
                        .url(ApiStrConfig.LOCAL_CACHE_URL_PLACEHOLDER)
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
        return cacheObjectList.stream().filter(Objects::nonNull).findAny().orElseGet(() -> {
            List<Object> cacheObjects = getCacheObjects(1);
            if (cacheObjects.isEmpty()) {
                return null;
            }
            return cacheObjects.get(0);
        });
    }

    private void loadCache() {
        // ?????????????????????????????????load
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
        LogUtil.debug(() -> String.format("????????????????????????: %sms", watch.elapsed(TimeUnit.MILLISECONDS)));
        if (cached == null) {
            BotMessageHelper.sendMsg(contact, "??????.??????????????????????????????");
            watch.stop();
            return;
        }
        ImageVo cachedImage = (ImageVo) cached;
        try {
            FileInputStream is = new FileInputStream(cachedImage.getLocalImageFilePath());
            watch.reset();
            Image uploadImage = ExternalResource.uploadAsImage(is, contact);
            LogUtil.debug(() -> String.format("uploadAsImage??????: %sms", watch.elapsed(TimeUnit.MILLISECONDS)));
            BotMessageHelper.sendMsg(contact, "[mirai:image:" + uploadImage.getImageId() + "]");
            if (StringUtils.isNotBlank(cachedImage.getPageUrl())) {
                BotMessageHelper.sendMsg(contact, "????????????: " + cachedImage.getPageUrl());
            } else if (!StringUtils.equalsIgnoreCase(cachedImage.getUrl(), ApiStrConfig.LOCAL_CACHE_URL_PLACEHOLDER)) {
                BotMessageHelper.sendMsg(contact, "??????????????????: " + cachedImage.getUrl());
            }
            watch.reset();
            synchronized (cacheListLock) {
                cacheObjectList.remove(cached);
            }
            LogUtil.debug(() -> String.format("cacheObjectList remove??????: %sms", watch.elapsed(TimeUnit.MILLISECONDS)));
            CommonUtil.markImageAlreadySend(cachedImage.getLocalImageFilePath());
        } catch (IllegalStateException e) {
            LogUtil.warn(String.format("?????????????????????. name: %s, api: %s", name, cachedImage.getUrl()));
            BotMessageHelper.sendMsg(contact, "????????????????????? " +
                    (StringUtils.isNotBlank(cachedImage.getUrl()) ? cachedImage.getUrl() : "")
            );
        } catch (Exception e) {
            LogUtil.error(String.format("??????????????????. name: %s, api: %s", name, cachedImage.getUrl()), e);
            BotMessageHelper.sendMsg(contact, "??????????????????");
        }
        if (watch.isRunning()) {
            watch.stop();
        }
    }

    protected ImageVo processParsedPic(ApiStrConfig apiStrConfig, String api, String imageUrl,
                                       String name) throws Exception {
        String localImageFilePath = CommonUtil.requestAndDownloadPic(
                imageUrl, UUID.randomUUID().toString(), "jpg", name, apiStrConfig.isUseProxy()
        );
        if (StringUtils.isBlank(localImageFilePath)) {
            return null;
        }
        String id = IdUtil.hashId(api, imageUrl);
        if (IdUtil.isIdExist(id)) {
            return null;
        }
        IdUtil.markIdAsExist("image", id, localImageFilePath, false);
        return ImageVo.builder()
                .id(id)
                .url(imageUrl)
                .localImageFilePath(localImageFilePath)
                .build();
    }

}
