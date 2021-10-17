package com.highestpeak.commond;

import com.google.common.collect.Lists;
import com.highestpeak.PeakBot;
import com.highestpeak.config.AuthorityConfig;
import com.highestpeak.config.Config;
import com.highestpeak.entity.MsgEventParams;
import com.highestpeak.entity.CommandChatType;
import com.highestpeak.entity.CommandUserType;
import com.highestpeak.util.BotMessageHelper;
import lombok.NonNull;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.contact.Friend;
import net.mamoe.mirai.contact.Group;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;
import java.util.function.Function;

public class SuperCommand extends PeakCommand {

    public static final String HELP_MSG = "\n使用\"super list\"查看拥有super权限的成员\n\n"
            + "使用以下格式添加或删除成员权限：\n"
            + "\"super add\\\\del QQ号码\"\n"
            + "示例：\n"
            + "\"super add 2799282971\"";
    public static final String SUPER_LIST_COMMAND = "super list";
    public static final String SUPER_RELOAD_CONFIG_COMMAND = "super reload config";
    public static final String SUPER_ADD_COMMAND = "super add";
    public static final String SUPER_DEL_COMMAND = "super del";
    public static final String SUPER_ENABLE_COMMAND = "super enable";
    public static final String SUPER_DISABLE_COMMAND = "super disable";

    public SuperCommand(List<String> rule) {
        super(rule);
        addAllowChatType(CommandChatType.FRIEND_CHAT.getType());
        addAllowChatType(CommandChatType.GROUP_TEMP_CHAT.getType());
        addAllowChatType(CommandChatType.GROUP_CHAT.getType());
        addAllowChatType(CommandChatType.STRANGER_CHAT.getType());

        addAllowSendUserType(CommandUserType.BOT_ADMINISTRATOR.getType());
    }

    @Override
    public boolean isEnable() {
        return true;
    }

    @Override
    public boolean matchName(String name) {
        return StringUtils.equalsIgnoreCase(name, "super");
    }

    @Override
    public void setConfig(Object config) {
    }

    @Override
    public void enableCommandOn(Long group, Long user) {
        // todo WARNING: 不去实现
    }

    @Override
    public void disableCommandOn(Long group, Long user) {
        // todo WARNING: 不去实现
    }

    @Override
    public boolean matchGroup(Long group) {
        return true;
    }

    @Override
    public void groupExecCommand(@NonNull Group group, MsgEventParams event) {
        execSupperCommand(group, event);
    }

    @Override
    public boolean matchFriend(Long friend) {
        return true;
    }

    @Override
    public void friendExecCommand(@NonNull Friend friend, MsgEventParams event) {
        execSupperCommand(friend, event);
    }

    private void execSupperCommand(Contact contact, MsgEventParams event) {
        String msg = event.getMessageChain().contentToString();

        if (msg.equals(SUPER_LIST_COMMAND)) {
            List<Long> admins = Config.get().getAuthorityConfig().getBotGlobalAdmins();
            StringBuilder builder = new StringBuilder("所有拥有super权限的用户:\n");
            admins.forEach(qq -> builder.append(qq).append("\n"));
            BotMessageHelper.sendMsg(contact, builder.toString(), false);
        } else if (msg.equals(SUPER_RELOAD_CONFIG_COMMAND)) {
            Config.prepareUpdateConfig();
            BotMessageHelper.sendMsg(contact, "稍后即将重新加载配置", false);
        } else if (msg.startsWith(SUPER_ADD_COMMAND)) {
            iterTokenMsg(contact, msg.substring(SUPER_ADD_COMMAND.length()), this::addSuperUser);
        } else if (msg.startsWith(SUPER_DEL_COMMAND)) {
            iterTokenMsg(contact, msg.substring(SUPER_DEL_COMMAND.length()), this::delSuperUser);
        } else if (msg.startsWith(SUPER_ENABLE_COMMAND)) {
            iterTokenMsg(contact, msg.substring(SUPER_ENABLE_COMMAND.length()), (token) -> enableCommand(token, event));
        } else if (msg.startsWith(SUPER_DISABLE_COMMAND)) {
            iterTokenMsg(contact, msg.substring(SUPER_DISABLE_COMMAND.length()), (token) -> disableCommand(token,
                    event));
        } else {
            BotMessageHelper.sendMsg(contact, HELP_MSG);
        }
    }

    private void iterTokenMsg(Contact contact, String remainMsg, Function<String, String> consumeTokenMsg) {
        List<String> returnStrList = Lists.newArrayList();
        StringTokenizer tokenizer = new StringTokenizer(remainMsg, " ");
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            String returnStr = consumeTokenMsg.apply(token);
            if (StringUtils.isNotBlank(returnStr)) {
                returnStrList.add(returnStr);
            }
        }
        String collectMsg = String.join("\n", returnStrList);
        BotMessageHelper.sendMsg(contact, collectMsg);
    }

    private String addSuperUser(String user) {
        AuthorityConfig authorityConfig = Config.get().getAuthorityConfig();
        if (user.length() > 11 || user.length() < 5) {
            return "无效的qq号:" + user;
        } else if (authorityConfig.getBotGlobalAdmins().contains(Long.parseLong(user))) {
            return user + "已拥有super权限";
        } else {
            authorityConfig.addAdmin(Long.parseLong(user));
            Config.prepareUpdateConfig();
            return user + "添加到增加事件队列";
        }
    }

    private String delSuperUser(String user) {
        AuthorityConfig authorityConfig = Config.get().getAuthorityConfig();
        if (user.length() > 11 || user.length() < 5) {
            return "无效的qq号:" + user;
        } else if (authorityConfig.getBotGlobalAdmins().size() <= 1) {
            return "需至少保留一位拥有super权限的用户";
        } else if (!authorityConfig.getBotGlobalAdmins().contains(Long.parseLong(user))) {
            return user + "不在super权限列表中";
        } else {
            authorityConfig.removeAdmin(Long.parseLong(user));
            Config.prepareUpdateConfig();
            return user + "添加到删除事件队列";
        }
    }

    private String enableCommand(String command, MsgEventParams event) {
        Collection<PeakCommand> peakCommands = PeakBot.allPeakCommands();
        for (PeakCommand peakCommand : peakCommands) {
            //noinspection StatementWithEmptyBody
            if (peakCommand instanceof SuperCommand) {
                // do nothing
            } else if (peakCommand.matchName(command) || peakCommand.matchCommand(command)) {
                peakCommand.enableCommandOn(event.getFromGroup(), event.getFromQQ());
                return command + "-命令启用成功";
            }
        }
        return "未找到合适的命令";
    }

    private String disableCommand(String command, MsgEventParams event) {
        Collection<PeakCommand> peakCommands = PeakBot.allPeakCommands();
        for (PeakCommand peakCommand : peakCommands) {
            //noinspection StatementWithEmptyBody
            if (peakCommand instanceof SuperCommand) {
                // do nothing
            } else if (peakCommand.matchName(command) || peakCommand.matchCommand(command)) {
                peakCommand.disableCommandOn(event.getFromGroup(), event.getFromQQ());
                return command + "-命令禁用成功";
            }
        }
        return "未找到合适的命令";
    }
}
