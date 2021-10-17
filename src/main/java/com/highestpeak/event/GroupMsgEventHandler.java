package com.highestpeak.event;

import com.highestpeak.entity.MsgEventParams;
import com.highestpeak.entity.CommandChatType;
import net.mamoe.mirai.event.events.GroupMessageEvent;

public class GroupMsgEventHandler extends MessageEventHandler{

    public void handleEvent(GroupMessageEvent event) {
        super.handleEvent(MsgEventParams.builder()
                .msgType(CommandChatType.GROUP_CHAT.getType())
                .time(event.getTime())
                .fromGroup(event.getGroup().getId())
                .fromQQ(event.getSender().getId())
                .messageChain(event.getMessage())
                .build());
    }

}
