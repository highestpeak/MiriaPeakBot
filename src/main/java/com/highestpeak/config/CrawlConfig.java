package com.highestpeak.config;

import com.google.common.collect.Maps;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class CrawlConfig extends ApiConfig {

    public static final CrawlConfig DEFAULT = new CrawlConfig();

    private Map<String, CrawlLocator> apiLocatorMap = Maps.newHashMap();

    public CrawlLocator getLocator(String name) {
        return apiLocatorMap.get(name);
    }

    @Data
    public static class CrawlLocator {

        /**
         * 某个元素可能是一个块，到这个块的选择器
         */
        private String blockCssQuery;

        /**
         * 块内的选择器，块内某个标签有图片
         */
        private String imgCssQuery;

        /**
         * 定位到元素后取出那个字段的值
         */
        private String urlAttr;

        /**
         * 块内的选择器，块内某个标签有id
         */
        private String idCssQuery;

        /**
         * 定位到元素后取出那个字段的值
         */
        private String idAttr;

        /**
         * todo
         *
         *
         * 块内的选择器，块内某个标签有图片详情页面
         */
        private String pageCssQuery;

        /**
         * 定位到元素后取出那个字段的值
         */
        private String pageAttr;
    }
}
