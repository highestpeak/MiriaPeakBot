package com.highestpeak.commond;

import com.google.common.collect.Lists;
import com.highestpeak.config.ApiStrConfig;
import com.highestpeak.config.Config;
import com.highestpeak.config.CrawlConfig;
import com.highestpeak.entity.*;
import com.highestpeak.util.CommonUtil;
import com.highestpeak.util.LogUtil;
import lombok.NonNull;
import net.mamoe.mirai.contact.Friend;
import net.mamoe.mirai.contact.Group;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 是一种简单的三步策略图片发送
 * 第一步 请求url
 * 第二步 解析image地址(提供可选image区域的定位器)
 * 第三步 发送图片地址和图片到群中
 */
public class CrawlImageCommand extends ApiCommand {

    public CrawlImageCommand(List<String> rule) {
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
    public CrawlConfig getConfig() {
        return (CrawlConfig) super.getConfig();
    }

    @Override
    public void groupExecCommand(@NonNull Group group, MsgEventParams event) {
        sendImage(group, getConfig().getName());
    }

    @Override
    public void friendExecCommand(@NonNull Friend friend, MsgEventParams event) {
        sendImage(friend, getConfig().getName());
    }

    @Override
    public List getCacheObjects(int count) {
        return fetchImages(count);
    }

    /**
     * 爬虫爬取的必定是一种策略的可以获取下一页的
     */
    private List<ImageVo> fetchImages(int count) {
        try {
            CrawlConfig config = getConfig();
            if (count <= 0) {
                return Collections.emptyList();
            }

            boolean found = false;
            ApiStrConfig apiStrConfig = null;
            for (int i = 0; i < config.getApiList().size(); i++) {
                apiStrConfig = config.selectNextApi();
                if (apiStrConfig.isPageUpdater()) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return Collections.emptyList();
            }

            if (apiStrConfig.isLocalHtml()) {
                String htmlFileName = apiStrConfig.selectCurrentPage();
                List<ImageVo> imageVos = parseLocalPage(htmlFileName, config, apiStrConfig, count);
                if (imageVos.isEmpty() || imageVos.size() < count) {
                    htmlFileName = apiStrConfig.selectNextPage();
                    List<ImageVo> remainVos = parseLocalPage(htmlFileName, config, apiStrConfig,
                            count - imageVos.size());
                    imageVos.addAll(remainVos);
                }
                return imageVos;
            } else {
                String pageUrl = apiStrConfig.selectCurrentPage();
                List<ImageVo> imageVos = crawlPage(pageUrl, config, apiStrConfig, count);
                if (imageVos.isEmpty() || imageVos.size() < count) {
                    pageUrl = apiStrConfig.selectNextPage();
                    List<ImageVo> remainVos = crawlPage(pageUrl, config, apiStrConfig, count - imageVos.size());
                    imageVos.addAll(remainVos);
                }
                return imageVos;
            }
        } catch (Exception e) {
            LogUtil.error("getOneImage error.", e);
            return Collections.emptyList();
        }
    }

    private List<ImageVo> crawlPage(String nextPage, CrawlConfig config, ApiStrConfig apiStrConfig, int count) throws Exception {
        String crawlPageContent = CommonUtil.getCrawlPageContent(nextPage, apiStrConfig.isUseProxy());
        if (StringUtils.isBlank(crawlPageContent)) {
            throw new PeakBotException();
        }
        Document document = Jsoup.parse(crawlPageContent);
        document.setBaseUri(nextPage);
        return parseHtml(document, config, apiStrConfig, count);
    }

    private List<ImageVo> parseLocalPage(String nextPageFileName, CrawlConfig config, ApiStrConfig apiStrConfig,
                                         int count) throws Exception {
        String pageFilePath = "./data/page/" + getConfig().getName() + "/" + nextPageFileName;
        String localHtmlContent = CommonUtil.readLocalHtmlContent(pageFilePath);
        if (StringUtils.isBlank(localHtmlContent)) {
            throw new PeakBotException();
        }
        Document document = Jsoup.parse(localHtmlContent);
        return parseHtml(document, config, apiStrConfig, count);
    }

    private List<ImageVo> parseHtml(Document document, CrawlConfig config, ApiStrConfig apiStrConfig, int count) throws Exception {
        CrawlConfig.CrawlLocator locator = config.getLocator(apiStrConfig.getName());
        Elements blockElements = document.select(locator.getBlockCssQuery());
        String name = config.getName();
        Set<String> blackUrlList = apiStrConfig.getBlackUrlList();

        int iter = 0;
        List<ImageVo> imageLinkList = Lists.newArrayList();
        for (Element blockElement : blockElements) {
            if (iter == count) {
                break;
            }
            Element imageElement = blockElement.select(locator.getImgCssQuery()).first();
            Element idElement = blockElement.select(locator.getIdCssQuery()).first();
            if (imageElement != null && idElement != null) {
                String imageUrl = imageElement.absUrl(locator.getUrlAttr());
                if (StringUtils.isBlank(imageUrl)) {
                    imageUrl = document.baseUri() + imageElement.attr(locator.getUrlAttr());
                    if (StringUtils.isBlank(imageUrl)) {
                        continue;
                    }
                }
                if (blackUrlList != null && blackUrlList.contains(imageUrl)) {
                    // 发现占位图
                    continue;
                }
                String idValue = idElement.attr(locator.getIdAttr());
                ImageVo imageVo = processParsedPic(apiStrConfig, idValue, imageUrl, name);

                if (imageVo == null) {
                    continue;
                }
                String pageCssQuery = locator.getPageCssQuery();
                if (StringUtils.isNotBlank(pageCssQuery)) {
                    Element pageElement = blockElement.select(pageCssQuery).first();
                    if (pageElement != null) {
                        String pageValue = pageElement.absUrl(locator.getPageAttr());
                        imageVo.setPageUrl(pageValue);
                    }
                }
                iter++;
                imageLinkList.add(imageVo);
                if (imageLinkList.size() >= count) {
                    break;
                }
            }
        }
        return imageLinkList;
    }
}
