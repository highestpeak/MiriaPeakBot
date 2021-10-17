package com.highestpeak.event;

import com.highestpeak.PeakBot;
import com.highestpeak.commond.PeakCommand;
import com.highestpeak.entity.MsgEventParams;
import com.highestpeak.util.LogUtil;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;

public abstract class MessageEventHandler {
    public void handleEvent(MsgEventParams msgEventParams) {
        String msgContent = msgEventParams.getMessageChain().contentToString();
        if (StringUtils.isBlank(msgContent)) {
            LogUtil.debug(() -> "接收到消息-不处理长度为0的异常文本.");
            return;
        }

        LogUtil.debug(() -> "接收到消息: " + msgContent);

        Collection<PeakCommand> peakCommands = PeakBot.allPeakCommands();
        for (PeakCommand peakCommand : peakCommands) {
            if (peakCommand.isEnable() && peakCommand.matchCommand(msgContent) && peakCommand.matchType(msgEventParams)) {
                peakCommand.execCommand(msgEventParams);
                return;
            }
        }
    }
}
