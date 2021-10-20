package com.highestpeak.util;

import com.google.common.io.CharStreams;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

import javax.net.ssl.SSLException;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

public class FetchHtmlAndStoreLocal {

    private static CloseableHttpClient HTTP_CLIENT;

    private static CloseableHttpClient SHADOWSOCKS_HTTP_CLIENT;

    /**
     * 对于某些使用js加载的站点请手动替换为 selenium 参考如下进行配置
     * https://zhuanlan.zhihu.com/p/110274934
     * https://www.jianshu.com/p/30b60f5da23c
     */
    public static void main(String[] args) throws Exception {
        System.setProperty("https.protocols", "TLSv1,TLSv1.1,TLSv1.2");
        System.setProperty("webdriver.chrome.driver", "E:\\_environment\\chromedriver.exe");

        HTTP_CLIENT = HttpClients.createDefault();
        SHADOWSOCKS_HTTP_CLIENT = ShadowsocksClientHelper.getHttpClient();
        WebDriver driver = new ChromeDriver();

        String saveFileNamePattern = "E:\\_PLAY\\图片暂存\\asiansister_{#PeakBotPageUpdater}.html";
        String urlPattern = "https://asiansister.com/_page{#PeakBotPageUpdater}";
        String imgBaseUrl = "https://asiansister.com/";

        int num = 53;

        for (int i = 17; i <num; i++) {
            String url = urlPattern.replace("{#PeakBotPageUpdater}", Integer.toString(i));
            // todo 二选一
            // String crawlPageContent = fetchHtml(url, true);
            driver.get(url);
            // 向下滚动充分加载
            for (int j = 0; j < 20; j++) {
                ((JavascriptExecutor)driver).executeScript(String.format("scrollTo(0,%s)", j*300));
                Thread.sleep(1000);
            }

            Thread.sleep(2000);
            String crawlPageContent = driver.getPageSource();

            if (StringUtils.isBlank(crawlPageContent)) {
                continue;
            }
            Document document = Jsoup.parse(crawlPageContent, imgBaseUrl);
            replaceUrlToAbsolute(document);
            String fileName = saveFileNamePattern.replace("{#PeakBotPageUpdater}", Integer.toString(i));
            FileUtils.writeStringToFile(new File(fileName), document.outerHtml(), StandardCharsets.UTF_8);
            Thread.sleep(1000);
        }

    }

    private static void replaceUrlToAbsolute(Document document) {
        Elements select = document.select("a");
        for (Element e : select){
            // baseUri will be used by absUrl
            String absUrl = e.absUrl("href");
            e.attr("href", absUrl);
        }

        //now we process the imgs
        select = document.select("img");
        for (Element e : select){
            e.attr("src", e.absUrl("src"));
        }
    }

    private static String fetchHtml(String url, boolean useProxy) {
        // 创建http GET请求
        HttpGet httpGet = new HttpGet(url);
        httpGet.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, " +
                "like Gecko) Chrome/91.0.4472.124 Safari/537.36 Edg/91.0.864.67");
        RequestConfig.Builder requestConfigBuilder = RequestConfig.custom()
                .setConnectTimeout(10000)
                .setConnectionRequestTimeout(10000)
                .setSocketTimeout(10000);
        //设置代理IP、端口、协议（请分别替换）
        //ProxyConfig proxyConfig = Config.get().getProxyConfig();
        //if (useProxy && proxyConfig.isEnable() && !proxyConfig.isShadowsocks()) {
        //    HttpHost proxy = new HttpHost(proxyConfig.getHost(), proxyConfig.getPort(), proxyConfig.getScheme());
        //    requestConfigBuilder.setProxy(proxy);
        //}
        RequestConfig config = requestConfigBuilder.build();
        httpGet.setConfig(config);
        try (CloseableHttpResponse response = true && useProxy
                ? executeShadowsocks(httpGet)
                : HTTP_CLIENT.execute(httpGet)
        ) {
            // 判断返回状态是否为200
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                LogUtil.warn(String.format("获取响应非200. url: %s, code: %s", url, statusCode));
                return StringUtils.EMPTY;
            }
            return CharStreams.toString(
                    new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8)
            );
        } catch (ConnectTimeoutException e) {
            LogUtil.warn(String.format("requestAndDo链接超时 url: %s msg: %s", url, e.getMessage()));
        } catch (SocketTimeoutException e) {
            LogUtil.warn("requestAndDo超时 url:" + url);
        } catch (SSLException e) {
            LogUtil.warn("requestAndDo失败. ssl 异常" + e.getMessage());
        } catch (Exception e) {
            LogUtil.error("requestAndDo失败", e);
        }
        return StringUtils.EMPTY;
    }

    private static CloseableHttpResponse executeShadowsocks(final HttpUriRequest request) throws IOException {
        return SHADOWSOCKS_HTTP_CLIENT.execute(request, clientContext());
    }

    public static HttpClientContext clientContext() {
        InetSocketAddress socksaddr = new InetSocketAddress(
                "127.0.0.1", 7890
        );
        HttpClientContext context = HttpClientContext.create();
        context.setAttribute("socks.address", socksaddr);
        return context;
    }
}
