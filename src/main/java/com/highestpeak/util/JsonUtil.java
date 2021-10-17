package com.highestpeak.util;

import com.jayway.jsonpath.JsonPath;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class JsonUtil {

    private static final CloseableHttpClient HTTP_CLIENT = HttpClients.createDefault();
    private static final RequestConfig config = RequestConfig.custom()
            .setConnectTimeout(1000)
            .setConnectionRequestTimeout(1000)
            .setSocketTimeout(1000).build();

    /**
     * 拉取 Api 获取 json ，并找到 json 目标路径的内容
     *
     * @param jsonApi 获取json的api
     * @param jsonTargetPath json中的目标内容的路径 eg: "/response/history"
     * @return 目标json节点
     */
    public static <T> Optional<T> jsonApiTargetJsonPath(Class<T> returnType, String jsonApi, String jsonTargetPath) {
        // 定义请求的参数
        URI uri;
        try {
            uri = new URIBuilder(jsonApi).build();
        } catch (Exception e) {
            LogUtil.error(String.format("生成uri对象错误. jsonApi: %s", jsonApi), e);
            return Optional.empty();
        }
        // 创建http GET请求
        HttpGet httpGet = new HttpGet(uri);
        httpGet.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, " +
                "like Gecko) Chrome/91.0.4472.124 Safari/537.36 Edg/91.0.864.67");
        httpGet.setConfig(config);
        try (CloseableHttpResponse response = HTTP_CLIENT.execute(httpGet)) {
            // 判断返回状态是否为200
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                LogUtil.warn(String.format("获取json响应非200. jsonApi: %s, code: %s", jsonApi,
                        statusCode));
                return Optional.empty();
            }
            String content = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            T returnValue = JsonPath.parse(content).read(jsonTargetPath, returnType);
            return Optional.of(returnValue);
        } catch (SocketTimeoutException e) {
            LogUtil.warn(String.format("获取json信息链接超时 uri: %s msg: %s", uri, e.getMessage()));
            return Optional.empty();
        } catch (Exception e) {
            LogUtil.error(String.format("获取json内容错误. jsonApi: %s jsonTargetPath: %s", jsonApi, jsonTargetPath), e);
            return Optional.empty();
        }
    }
}
