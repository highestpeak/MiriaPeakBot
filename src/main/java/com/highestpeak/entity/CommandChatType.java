package com.highestpeak.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用于描述机器人的指令支持的使用类型
 * <p>
 * 一旦Robot和其他人成为Friend，则一定为FRIEND_CHAT。
 * 即使其他人从群里打开和Robot的聊天界面，聊天类型也是FRIEND_CHAT。
 * <p>
 * 群里聊天的类型为GROUP_CHAT
 * <p>
 * 与Robot不是好友关系的人，从群里打开与Robot的聊天界面，则聊天类型为GROUP_TEMP_CHAT
 */
@Getter
@AllArgsConstructor
public enum CommandChatType {
    FRIEND_CHAT(1),
	GROUP_CHAT(2),
	GROUP_TEMP_CHAT(3),
	DISCUSS_MSG(4),
	STRANGER_CHAT(5),
	;

    private final int type;
}
