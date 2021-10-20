package com.highestpeak.commond;

import com.google.common.collect.Lists;
import com.highestpeak.config.ApiStrConfig;
import com.highestpeak.entity.CommandChatType;
import com.highestpeak.entity.CommandUserType;
import com.highestpeak.entity.ImageVo;
import com.highestpeak.entity.MsgEventParams;
import com.highestpeak.util.JsonUtil;
import com.highestpeak.util.LogUtil;
import lombok.NonNull;
import net.mamoe.mirai.contact.Friend;
import net.mamoe.mirai.contact.Group;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 通过某个图片api来获取图片
 * <p>
 * 1. 可以直接通过api来获取图片，api每次请求的响应不同<br/>
 * 2. 可以请求api，api返回json，真正的图片内容在json中
 */
public class ApiImageCommand extends ApiCommand {

    public ApiImageCommand(List<String> rule) {
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

    private List<ImageVo> fetchImages(int count) {
        List<ImageVo> imageVos = Lists.newArrayListWithCapacity(count);
        for (int i = 0; i < count; i++) {
            ApiStrConfig apiStrConfig = getConfig().selectNextApi();
            String api = apiStrConfig.getApi();
            String imageUrl = api;
            String name = getConfig().getName();
            try {
                if (apiStrConfig.isJsonRedirect()) {
                    imageUrl = jsonRedirectToPicApi(apiStrConfig);
                    // 降低抓取频率，防止被办，没必要，因为本来已经就很慢了
                    //Thread.sleep(500);
                }
                ImageVo imageVo = processParsedPic(apiStrConfig, api, imageUrl, name);
                imageVos.add(imageVo);
            } catch (Exception e) {
                LogUtil.error(String.format("图片获取错误. name: %s, api: %s", name, api), e);
            }
        }
        return imageVos;
    }

    /**
     * 从json中获取真正的api
     */
    private String jsonRedirectToPicApi(ApiStrConfig apiStrConfig) {
        Optional<String> picOptional = JsonUtil.jsonApiTargetJsonPath(
                String.class, apiStrConfig.getApi(), apiStrConfig.getJsonTargetPath()
        );
        return picOptional.orElse(StringUtils.EMPTY);
    }

}
