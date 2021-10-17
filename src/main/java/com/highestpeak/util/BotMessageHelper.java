package com.highestpeak.util;

import com.highestpeak.PeakBot;
import com.highestpeak.config.Config;
import com.highestpeak.entity.MsgEventParams;
import com.highestpeak.util.LogUtil;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.contact.Friend;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.message.code.MiraiCode;

public class BotMessageHelper {
    public static final String LEN_OVERFLOW_MSG = "很抱歉，本次发送的字数超过上限，已取消发送！\n字数：%d";

    public static void sendErrorPermissionMsg(long fromGroup, long fromQQ, String msg) {
        if (fromGroup == MsgEventParams.FRIEND_TYPE_DEFAULT_GROUP_VALUE) {
            sendToQQFriend(fromQQ, msg);
        } else {
            sendToQQGroup(fromQQ, msg);
        }
    }

    public static void sendToQQGroup(long group, String msg) {
        sendToQQGroup(group, msg, true);
    }

    public static void sendToQQGroup(long group, String msg, boolean checkLen) {
        try {
            LogUtil.debug(() -> "给某个QQ群发送信息-QQ群的号码为：" + group);
            Group groupRef = PeakBot.getCURRENT_BOT().getGroup(group);
            if (groupRef == null) {
                LogUtil.warn("QQ群未找到: " + group);
                return;
            }
            sendMsg(groupRef, msg, checkLen);
        } catch (Exception e) {
            LogUtil.error("给某个QQ群发送信息发生异常-QQ群的号码为：" + group, e);
        }
    }

    public static void sendToQQFriend(long toQQ, String msg) {
        sendToQQFriend(toQQ, msg, true);
    }

    public static void sendToQQFriend(long toQQ, String msg, boolean checkLen) {
        try {
            LogUtil.debug(() -> "sendMessageToQQFriend: " + toQQ);
            Friend friend = PeakBot.getCURRENT_BOT().getFriend(toQQ);
            if (friend == null) {
                LogUtil.warn("QQ未找到: " + toQQ);
                return;
            }
            sendMsg(friend, msg, checkLen);
        } catch (Exception e) {
            LogUtil.error("给某个QQ发送信息发生异常-QQ的号码为：" + toQQ, e);
        }
    }

    public static void sendMsg(Contact contact, String msg) {
        sendMsg(contact, msg, false);
    }

    public static void sendMsg(Contact contact, String msg, boolean checkLen) {
        msg = checkLen ? checkLengthAndModifySendMsg(msg) : msg;
        //LogUtil.debug(() -> "给某个QQ群发送信息-QQ群的号码为：" + contact.getId());
        contact.sendMessage(MiraiCode.deserializeMiraiCode(msg));
    }

    public static String checkLengthAndModifySendMsg(String sendMsg) {
        if (sendMsg == null) {
            return null;
        }

        LogUtil.debug(() -> "send msg checkSendMsgLength() -> length = " + sendMsg.length());

        long msgMaxLength = Config.get().getSendMsgMaxLength();
        if (sendMsg.length() >= msgMaxLength && msgMaxLength != 0) {
            return String.format(LEN_OVERFLOW_MSG, sendMsg.length());
        } else {
            return sendMsg;
        }
    }
}
