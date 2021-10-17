package com.highestpeak.event;

import com.highestpeak.entity.MsgEventParams;
import com.highestpeak.entity.CommandChatType;
import net.mamoe.mirai.event.events.FriendMessageEvent;

public class FriendMsgEventHandler extends MessageEventHandler{
    public void handleEvent(FriendMessageEvent event) {
        super.handleEvent(MsgEventParams.builder()
                .msgType(CommandChatType.GROUP_CHAT.getType())
                .time(event.getTime())
                .fromGroup(MsgEventParams.FRIEND_TYPE_DEFAULT_GROUP_VALUE)
                .fromQQ(event.getSender().getId())
                .messageChain(event.getMessage())
                .build());
    }
}
