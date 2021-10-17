package com.highestpeak.entity;

import com.highestpeak.PeakBot;
import com.highestpeak.config.Config;
import com.highestpeak.util.LogUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.contact.Member;
import net.mamoe.mirai.contact.MemberPermission;

import java.util.List;

/**
 * 指令发送者类型
 */
@Getter
@AllArgsConstructor
public enum CommandUserType {
    NORMAL_USER(1),
    GROUP_ADMINISTRATOR(2),
    GROUP_OWNER(3),
    BOT_ADMINISTRATOR(4),
    ;

    private final int type;

    /**
     * 判断用户的类型
     */
    public static int getType(long fromGroup, long fromQQ) {
        // 判断为群消息还是私聊消息
        int userType = fromGroup == MsgEventParams.FRIEND_TYPE_DEFAULT_GROUP_VALUE ?
                getUserTypeByQQ(fromQQ) :
                getUserTypeByQQ(fromGroup, fromQQ);

        LogUtil.debug(() -> "User fromGroup = " + fromGroup + ", fromQQ = " + fromQQ + ", userType：" + userType);

        return userType;
    }

    public static int getUserTypeByQQ(long QQ) {

        List<Long> botGlobalAdministrators = Config.get().getAuthorityConfig().getBotGlobalAdmins();
        for (Long botAdministrator : botGlobalAdministrators) {
            if (botAdministrator.equals(QQ)) {
                return BOT_ADMINISTRATOR.getType();
            }
        }

        return NORMAL_USER.getType();
    }

    public static int getUserTypeByQQ(long fromGroup, long fromQQ) {
        Group group = PeakBot.getCURRENT_BOT().getGroup(fromGroup);
        if (group == null) {
            return NORMAL_USER.getType();
        }

        Member m = group.get(fromQQ);
        if (m == null) {
            return NORMAL_USER.getType();
        }

        // 这个判断是有顺序的 例如：自己是超级管理员，但又是普通群员

        // 首先判断是不是bot管理员
        if (getUserTypeByQQ(fromQQ) == BOT_ADMINISTRATOR.getType()) {
            return BOT_ADMINISTRATOR.getType();
        }

        // 首先判断是不是普通群员
        if (m.getPermission() == MemberPermission.MEMBER) {
            return NORMAL_USER.getType();
        }

        if (m.getPermission() == MemberPermission.OWNER) {
            return GROUP_OWNER.getType();
        }

        if (m.getPermission() == MemberPermission.ADMINISTRATOR) {
            return GROUP_ADMINISTRATOR.getType();
        }

        return NORMAL_USER.getType();
    }
}
