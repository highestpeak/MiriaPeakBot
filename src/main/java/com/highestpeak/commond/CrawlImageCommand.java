package com.highestpeak.commond;

import com.google.common.collect.Lists;
import com.highestpeak.config.ApiStrConfig;
import com.highestpeak.config.CrawlConfig;
import com.highestpeak.entity.CommandChatType;
import com.highestpeak.entity.CommandUserType;
import com.highestpeak.entity.ImageVo;
import com.highestpeak.entity.MsgEventParams;
import com.highestpeak.util.CommonUtil;
import com.highestpeak.util.IdUtil;
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
import java.util.UUID;

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
            String name = getConfig().getName();
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

            String nextPage = apiStrConfig.selectNextPage();

            Document document = Jsoup.connect(apiStrConfig.getApi()).get();
            CrawlConfig.CrawlLocator locator = config.getLocator(apiStrConfig.getName());
            Elements blockElements = document.select(locator.getBlockCssQuery());

            int iter = 0;
            List<ImageVo> imageLinkList = Lists.newArrayList();
            for (Element blockElement : blockElements) {
                if (iter == count) {
                    break;
                }
                Element imageElement = blockElement.select(locator.getImgCssQuery()).first();
                Element idElement = blockElement.select(locator.getIdCssQuery()).first();
                if (imageElement != null && idElement != null) {
                    String imageUrl = imageElement.attr(locator.getUrlAttr());
                    String idValue = idElement.attr(locator.getIdAttr());
                    String localImageFilePath = CommonUtil.requestAndDownloadPic(
                            imageUrl, UUID.randomUUID().toString(), "jpg", name
                    );
                    if (StringUtils.isBlank(localImageFilePath)) {
                        continue;
                    }
                    String id = IdUtil.hashId(nextPage, idValue, imageUrl);
                    if (IdUtil.isIdExist(id)) {
                        continue;
                    }
                    imageLinkList.add(ImageVo.builder()
                            .id(id)
                            .url(imageUrl)
                            .localImageFilePath(localImageFilePath)
                            .build());
                    IdUtil.markIdAsExist("image", id, localImageFilePath, false);
                    iter++;
                }
            }
            return imageLinkList;
        } catch (Exception e) {
            LogUtil.error("getOneImage error.", e);
            return Collections.emptyList();
        }
    }
}
