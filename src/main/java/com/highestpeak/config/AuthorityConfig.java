package com.highestpeak.config;

import com.google.common.collect.Lists;
import lombok.Data;

import java.util.List;

@Data
public class AuthorityConfig {
    /**
     * 全局的机器人管理员
     */
    private List<Long> botGlobalAdmins = Lists.newArrayList();

    public void addAdmin(Long admin) {
        botGlobalAdmins.add(admin);
    }

    public void removeAdmin(Long admin) {
        botGlobalAdmins.remove(admin);
    }
}
