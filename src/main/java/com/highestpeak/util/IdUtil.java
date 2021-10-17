package com.highestpeak.util;

import com.google.common.hash.Hashing;
import com.highestpeak.PeakBot;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 对于同一个站点的数据来说 id 是很有必要的<br/>
 * 其他情况也可以酌情生成id
 */
@SuppressWarnings("UnstableApiUsage")
public class IdUtil {

    /**
     * 根据 argList hash 出唯一的 id
     */
    public static String hashId(String... argList) {
        String join = String.join("", argList);
        return Hashing.sha256().hashBytes(join.getBytes(StandardCharsets.UTF_8)).toString();
    }

    /**
     * 判断 id 是否存在
     */
    public static boolean isIdExist(String id) {
        try {
            DatabaseHelper dbHelper = PeakBot.getDatabaseHelper();
            List<String> idList = dbHelper.executeQuery(
                    String.format("select id from id_table where id = '%s'", id),
                    (rs, index) -> rs.getString("id")
            );
            return !idList.isEmpty();
        } catch (Exception e) {
            LogUtil.error("isIdExist error.", e);
            return false;
        }
    }

    /**
     * 将这个 id 标记为已经存在
     */
    public static void markIdAsExist(String key, String id, String fileName, boolean alreadySend) {
        try {
            DatabaseHelper dbHelper = PeakBot.getDatabaseHelper();
            dbHelper.executeUpdate(String.format(
                    "insert into id_table values (null, '%s', '%s', '%s', '%s');",
                    key, id, fileName, alreadySend
            ));
        } catch (Exception e) {
            LogUtil.error("markIdAsExist error.", e);
        }
    }

}
