package com.highestpeak.event;

import com.highestpeak.util.LogUtil;
import net.mamoe.mirai.event.events.NewFriendRequestEvent;

public class NewFriendEventHandler {
    public void handleEvent(NewFriendRequestEvent event) {
        LogUtil.info(event.getMessage());
    }
}
