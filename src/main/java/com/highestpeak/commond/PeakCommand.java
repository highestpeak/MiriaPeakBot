package com.highestpeak.commond;

import com.google.common.collect.Sets;
import com.highestpeak.PeakBot;
import com.highestpeak.entity.CommandUserType;
import com.highestpeak.entity.MsgEventParams;
import com.highestpeak.util.BotMessageHelper;
import com.highestpeak.util.LogUtil;
import lombok.Getter;
import lombok.NonNull;
import net.mamoe.mirai.contact.Friend;
import net.mamoe.mirai.contact.Group;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Getter
public abstract class PeakCommand {

    /**
     * 等于这个值的rule不能执行
     */
    public static final String ERROR_COMMAND_RULE_STR = "ERROR_COMMAND_RULE_STR";

    private final String rule;
    private final Pattern pattern;
    private Set<String> rules;
    private final Set<Integer> commandAllowChatTypes;
    private final Set<Integer> commandAllowSendUserTypes;

    public PeakCommand(List<String> rules) {
        this.rule = buildCommandMatchStr(rules);
        this.pattern = Pattern.compile(this.rule);
        this.rules = Sets.newHashSet(rules);
        this.commandAllowChatTypes = Sets.newHashSet();
        this.commandAllowSendUserTypes = Sets.newHashSet();
    }

    public PeakCommand() {
        this(Collections.singletonList(ERROR_COMMAND_RULE_STR));
    }

    protected void addAllowChatType(Integer chatType) {
        commandAllowChatTypes.add(chatType);
    }

    protected void addAllowSendUserType(Integer sendUserType) {
        commandAllowSendUserTypes.add(sendUserType);
    }

    public abstract boolean isEnable();

    /**
     * 该命令的名称
     */
    public abstract boolean matchName(String name);

    /**
     * config和command绑定<br/>
     * WARNING:config必须和Config实列中的配置实例相关联，在更新config对象时可以同时更新到
     */
    public abstract void setConfig(Object config);

    /**
     * 在group或者user上启用命令
     */
    public abstract void enableCommandOn(Long group, Long user);

    public abstract void disableCommandOn(Long group, Long user);

    /**
     * 判断该条消息是否匹配这个命令
     */
    public boolean matchCommand(String msg) {
        //Matcher m = pattern.matcher(msg);
        //return m.matches();
        return this.rules.contains(msg);
    }

    /**
     * 判断类型是否符合<br/>
     */
    public boolean matchType(MsgEventParams event) {
        if (!chatTypeMatch(event.getMsgType())) {
            return false;
        }

        if (!msgUserTypeMatch(event)) {
            BotMessageHelper.sendErrorPermissionMsg(event.getFromGroup(), event.getFromQQ(), "没有权限使用该指令");
            return false;
        }

        return true;
    }

    /**
     * 检查聊天类型：群 人 等
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean chatTypeMatch(int chatType) {
        return commandAllowChatTypes.contains(chatType);
    }

    /**
     * 检查命令发送者的类型是否满足： 超级管理员、群内管理员、群主、普通、好友等
     */
    public boolean msgUserTypeMatch(MsgEventParams event) {
        long fromGroup = event.getFromGroup();
        long fromQQ = event.getFromQQ();
        // 判断为群消息还是私聊消息
        int userType = CommandUserType.getType(fromGroup, fromQQ);
        return commandAllowSendUserTypes.contains(userType);
    }

    /**
     * 执行命令
     */
    public void execCommand(MsgEventParams event) {
        long fromGroup = event.getFromGroup();
        if (fromGroup != MsgEventParams.FRIEND_TYPE_DEFAULT_GROUP_VALUE) {
            if (!matchGroup(fromGroup)) {
                BotMessageHelper.sendToQQGroup(fromGroup, "本群未开启该类型的命令执行");
                return;
            }
            Group groupRef = PeakBot.getCURRENT_BOT().getGroup(event.getFromGroup());
            if (groupRef == null) {
                LogUtil.warn("QQ群未找到: " + event.getFromGroup());
                return;
            }
            groupExecCommand(groupRef, event);
        } else {
            if (!matchFriend(event.getFromQQ())) {
                BotMessageHelper.sendToQQFriend(event.getFromQQ(), "未对您开启该类型的命令执行");
                return;
            }
            Friend friendRef = PeakBot.getCURRENT_BOT().getFriend(event.getFromQQ());
            if (friendRef == null) {
                LogUtil.warn("QQ好友未找到: " + event.getFromGroup());
                return;
            }
            friendExecCommand(friendRef, event);
        }
    }

    public abstract boolean matchGroup(Long group);

    public abstract void groupExecCommand(@NonNull Group group, MsgEventParams event);

    public abstract boolean matchFriend(Long friend);

    public abstract void friendExecCommand(@NonNull Friend friend, MsgEventParams event);

    public String buildCommandMatchStr(List<String> commands) {
        if (commands == null || commands.isEmpty()) {
            // class of this
            return StringUtils.defaultIfBlank(this.getClass().getSimpleName(), ERROR_COMMAND_RULE_STR);
        }
        StringBuilder commandBuilder = new StringBuilder("^(?:");
        for (String command : commands) {
            commandBuilder.append("(?:")
                    .append(command)
                    .append(")|");
        }
        commandBuilder.deleteCharAt(commandBuilder.length() - 1);
        commandBuilder.append(")\\s?([\\s\\S]*)$");
        return commandBuilder.toString();
    }
}
