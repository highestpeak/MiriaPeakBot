package com.highestpeak.config;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.highestpeak.entity.Constants;
import com.highestpeak.util.AtomicIntegerJacksonHelper;
import lombok.Data;

import java.util.concurrent.atomic.AtomicInteger;

@Data
public class ApiStrConfig {

    public static final String LOCAL_CACHE_URL_PLACEHOLDER = "local cache";

    public static final ApiStrConfig DEFAULT = new ApiStrConfig();

    /**
     * 这个 api str config 的名称标识
     */
    private String name = "reping";

    /**
     * api
     * <p>
     * eg: 网易云随机热评
     */
    private String api = "https://api.vvhan.com/api/reping";

    /**
     * 是否使用代理
     */
    private boolean isUseProxy = false;

    /**
     * 是否是返回json信息的api
     */
    private boolean isJsonRedirect = false;

    /**
     * 是否是本地的 html <br/>
     * 如果是本地的 html 则 api 是文件名
     */
    private boolean isLocalHtml = false;

    /**
     * 内容在json中的路径
     * <p>
     * json的path类似 "/response/history"
     * <p>
     * 这个格式是 https://github.com/json-path/JsonPath
     */
    private String jsonTargetPath = "/response/history";

    /**
     * 是否是多页的，可以翻页的api
     */
    private boolean isPageUpdater = false;

    /**
     * 使用占位符来代表需要插入数字的地方
     */
    private String pageUrl =
            "https://asiantolick.com/ajax/buscar_posts" +
                    ".php?post=&cat=&tag=1045&search=&index={#PeakBotPageUpdater}&ver=83";

    @JsonSerialize(using = AtomicIntegerJacksonHelper.AtomicIntegerSerializer.class, as = Integer.class)
    @JsonDeserialize(using = AtomicIntegerJacksonHelper.AtomicIntegerDeserializer.class, as = AtomicInteger.class)
    private AtomicInteger pageIndex = new AtomicInteger(1);

    public String selectCurrentPage() {
        if (!isPageUpdater) {
            return api;
        }

        return pageUrl.replace(Constants.PEAK_BOT_PAGE_UPDATER, Integer.toString(pageIndex.get()));
    }

    public String selectNextPage() {
        if (!isPageUpdater) {
            return api;
        }

        int nextIndex = pageIndex.incrementAndGet();
        return pageUrl.replace(Constants.PEAK_BOT_PAGE_UPDATER, Integer.toString(nextIndex));
    }
}
