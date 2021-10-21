package com.highestpeak.util;

import com.google.common.io.CharStreams;
import com.highestpeak.PeakBot;
import com.highestpeak.config.Config;
import com.highestpeak.config.ProxyConfig;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import javax.net.ssl.SSLException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CommonUtil {

    private static final CloseableHttpClient HTTP_CLIENT = HttpClients.createDefault();

    public static String readLocalHtmlContent(String fileName) throws IOException {
        return FileUtils.readFileToString(new File(fileName), StandardCharsets.UTF_8);
    }

    public static String getCrawlPageContent(String url, boolean useProxy) throws Exception {
        if (StringUtils.isBlank(url)) {
            return StringUtils.EMPTY;
        }

        StringBuilder content = new StringBuilder();

        requestAndDo(url, useProxy, (contentStream) -> {
            try {
                content.append(
                        CharStreams.toString(new InputStreamReader(contentStream, StandardCharsets.UTF_8))
                );
            } catch (IOException e) {
                LogUtil.error("获取内容失败", e);
            }
        });

        return content.toString();
    }

    public static String requestAndDownloadPic(String url, String uid, String ext, String name, boolean useProxy) throws Exception {
        if (StringUtils.isBlank(url)) {
            return StringUtils.EMPTY;
        }
        String path = "./data/Image/" + name + "/";
        String filePath = path + uid + "." + ext;

        AtomicBoolean isOk = new AtomicBoolean(false);

        // 默认使用 proxy 其他由内部判断
        requestAndDo(url, useProxy, (contentStream) -> {
            try {
                FileUtils.copyToFile(contentStream, new File(filePath));
                isOk.set(true);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        return isOk.get() ? filePath : StringUtils.EMPTY;
    }

    public static void requestAndDo(String url, boolean useProxy, Consumer<InputStream> contentConsumer) throws Exception {
        if (StringUtils.isBlank(url)) {
            return;
        }

        // 创建http GET请求
        HttpGet httpGet = new HttpGet(url);
        httpGet.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, " +
                "like Gecko) Chrome/91.0.4472.124 Safari/537.36 Edg/91.0.864.67");
        RequestConfig.Builder requestConfigBuilder = RequestConfig.custom()
                .setConnectTimeout(10000)
                .setConnectionRequestTimeout(10000)
                .setSocketTimeout(10000);
        //设置代理IP、端口、协议（请分别替换）
        ProxyConfig proxyConfig = Config.get().getProxyConfig();
        if (useProxy && proxyConfig.isEnable() && !proxyConfig.isShadowsocks()) {
            HttpHost proxy = new HttpHost(proxyConfig.getHost(), proxyConfig.getPort(), proxyConfig.getScheme());
            requestConfigBuilder.setProxy(proxy);
        }
        RequestConfig config = requestConfigBuilder.build();
        httpGet.setConfig(config);
        try (CloseableHttpResponse response = proxyConfig.isShadowsocks() && useProxy
                ? ShadowsocksClientHelper.execute(httpGet)
                : HTTP_CLIENT.execute(httpGet)
        ) {
            // 判断返回状态是否为200
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                LogUtil.warn(String.format("获取响应非200. url: %s, code: %s", url, statusCode));
                return;
            }
            try {
                contentConsumer.accept(response.getEntity().getContent());
            } catch (Exception e) {
                throw e.getCause();
            }
        } catch (ConnectTimeoutException e) {
            LogUtil.warn(String.format("requestAndDo链接超时 url: %s msg: %s", url, e.getMessage()));
        } catch (SocketTimeoutException e) {
            LogUtil.warn("requestAndDo超时 url:" + url);
        } catch (SSLException e) {
            LogUtil.warn("requestAndDo失败. ssl 异常" + e.getMessage());
        } catch (Throwable e) {
            LogUtil.error("requestAndDo失败", e);
        }
    }

    public static List<String> allFilesName(String ext, String name) {
        try {
            String path = "./data/Image/" + name + "/";
            Collection<File> files = FileUtils.listFiles(new File(path), new String[]{ext}, false);
            return files.stream().map(File::getName).map(fileName -> path + fileName).collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public static boolean isImageNotSend(String imageFileName) {
        try {
            DatabaseHelper dbHelper = PeakBot.getDatabaseHelper();
            List<Boolean> sendList = dbHelper.executeQuery(
                    String.format("select already_send from id_table where file_name = '%s'", imageFileName),
                    (rs, index) -> Boolean.parseBoolean(rs.getString(rs.getMetaData().getColumnName(1)))
            );
            if (sendList.isEmpty()) {
                return true;
            }
            return !sendList.get(0);
        } catch (Exception e) {
            LogUtil.error("isIdExist error.", e);
            return false;
        }
    }

    public static void markImageAlreadySend(String localImageFilePath) {
        try {
            startCleanImageFile();
            DatabaseHelper dbHelper = PeakBot.getDatabaseHelper();
            dbHelper.executeUpdate(String.format(
                    "update id_table SET already_send = '%s' where file_name = '%s';",
                    true, localImageFilePath
            ));
        } catch (Exception e) {
            LogUtil.error("markIdAsExist error.", e);
        }
    }

    private static final AtomicBoolean ALREADY_START_CLEAN = new AtomicBoolean(false);

    private static void startCleanImageFile() {
        if (!ALREADY_START_CLEAN.getAndSet(true)) {
            ScheduledExecutorService scheduledExecutorService = new ScheduledThreadPoolExecutor(1);
            // 每隔10s自动load一次cache
            Runnable imageScheduleDel = () -> {
                try {
                    DatabaseHelper dbHelper = PeakBot.getDatabaseHelper();
                    List<String> sendList = dbHelper.executeQuery(
                            "select file_name from id_table where already_send = 'true'",
                            (rs, index) -> rs.getString(rs.getMetaData().getColumnName(1))
                    );
                    for (String fileName : sendList) {
                        File file = new File(fileName);
                        if (file.exists()) {
                            try {
                                FileUtils.delete(file);
                            } catch (FileSystemException e) {
                                LogUtil.warn("cleanImageFile error. msg:" + e.getMessage());
                                // 出现错误可能是 miria 正在使用这个文件，下一个周期再去删除
                                return;
                            }
                        }
                    }
                } catch (Exception e) {
                    LogUtil.error("cleanImageFile error.", e);
                }
            };
            scheduledExecutorService.scheduleWithFixedDelay(imageScheduleDel, 0, 10, TimeUnit.MINUTES);
        }
    }
}
