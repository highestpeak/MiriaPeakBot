package com.highestpeak.entity;

import lombok.Builder;
import lombok.Data;
import net.mamoe.mirai.message.data.MessageChain;

@Data
@Builder
public class MsgEventParams {
    /**
     * 当是好友的消息时候的 group 的值
     */
    public static final long FRIEND_TYPE_DEFAULT_GROUP_VALUE = -1;

    private int msgType;
    private int time;
    private long fromGroup;
    private long fromQQ;
    private MessageChain messageChain;
}
