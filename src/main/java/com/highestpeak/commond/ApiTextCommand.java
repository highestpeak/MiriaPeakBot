package com.highestpeak.commond;

import com.fasterxml.jackson.databind.JsonNode;
import com.highestpeak.config.ApiConfig;
import com.highestpeak.config.ApiStrConfig;
import com.highestpeak.entity.CommandChatType;
import com.highestpeak.entity.CommandUserType;
import com.highestpeak.entity.MsgEventParams;
import com.highestpeak.util.BotMessageHelper;
import com.highestpeak.util.JsonUtil;
import com.highestpeak.util.LogUtil;
import lombok.NonNull;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.contact.Friend;
import net.mamoe.mirai.contact.Group;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ApiTextCommand extends ApiCommand {

    public ApiTextCommand(List<String> rule) {
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
    public List getCacheObjects(int count) {
        return Collections.emptyList();
    }

    @Override
    public void groupExecCommand(@NonNull Group group, MsgEventParams event) {
        sendText(group, getConfig().getName(), getConfig().selectNextApi());
    }

    @Override
    public void friendExecCommand(@NonNull Friend friend, MsgEventParams event) {
        sendText(friend, getConfig().getName(), getConfig().selectNextApi());
    }

    private void sendText(Contact contact, String name, ApiStrConfig apiStrConfig) {
        try {
            String api = apiStrConfig.getApi();
            String content = api + "默认内容";
            if (apiStrConfig.isJsonRedirect()) {
                Optional<String> infoOptional = JsonUtil.jsonApiTargetJsonPath(
                        String.class, apiStrConfig.getApi(), apiStrConfig.getJsonTargetPath()
                );
                if (infoOptional.isPresent()) {
                    content = infoOptional.get();
                }
            }
            BotMessageHelper.sendMsg(contact, content, true);
        } catch (Exception e) {
            LogUtil.error(String.format("文字发送错误. name: %s, apiStrConfig: %s", name, apiStrConfig), e);
            BotMessageHelper.sendMsg(contact, name + "文字发送错误");
        }
    }
}
