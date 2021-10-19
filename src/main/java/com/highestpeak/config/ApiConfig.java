package com.highestpeak.config;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.Lists;
import com.highestpeak.entity.Constants;
import com.highestpeak.util.AtomicIntegerJacksonHelper;
import lombok.Data;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Data
public class ApiConfig {
    public static final ApiConfig DEFAULT = new ApiConfig();

    /**
     * 该配置的名称
     */
    private String name = "API发送策略默认名称";

    /**
     * 这个配置是否生效
     */
    private boolean enable = false;

    private List<ApiStrConfig> apiList = Lists.newArrayList(ApiStrConfig.DEFAULT);

    /**
     * 是否在全局开启 允许向单个好友发送
     */
    private SendToConfig sendToFriends = SendToConfig.DEFAULT;

    /**
     * 是否在全局开启 允许向某个群发送
     */
    private SendToConfig sendToGroups = SendToConfig.DEFAULT;

    /**
     * 触发指令
     */
    private List<String> commands = Lists.newArrayList("command1", "command2");

    /**
     * 开启的群组<br/>
     * 在聊天框中通过 super enable name 来添加
     */
    private List<Long> enableGroup = Lists.newArrayList(12345678L, 23456789L);

    /**
     * 开启的好友<br/>
     * 在聊天框中通过 super enable user name 来添加
     */
    private List<Long> enableUsers = Lists.newArrayList(123456789L);

    private boolean enableCache = false;

    private int cacheNum = Constants.DEFAULT_CACHE_NUM;

    /**
     * 低于这个数值会重新 load 到 cacheNum
     */
    private int cordonCacheNum = Constants.DEFAULT_CACHE_NUM / 2;

    @JsonSerialize(using = AtomicIntegerJacksonHelper.AtomicIntegerSerializer.class, as = Integer.class)
    @JsonDeserialize(using = AtomicIntegerJacksonHelper.AtomicIntegerDeserializer.class, as = AtomicInteger.class)
    private AtomicInteger apiIndex = new AtomicInteger(0);

    /**
     * 1. 直接返回图片的api
     * 2. 返回json的api
     * 3. 返回html的url---带有下一页的生成策略
     */
    public ApiStrConfig selectNextApi() {
        int currIndex = apiIndex.get();
        List<ApiStrConfig> apiList = this.getApiList();
        if (currIndex >= apiList.size()) {
            apiIndex.compareAndSet(currIndex, 0);
        }
        int nextApiIndex = apiIndex.getAndIncrement();
        if (nextApiIndex < apiList.size()) {
            return apiList.get(nextApiIndex);
        } else {
            return apiList.get(0);
        }
    }
}
