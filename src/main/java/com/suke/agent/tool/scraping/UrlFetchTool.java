/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29
 * @description URL抓取工具，通过Jsoup获取网页HTML内容
 */

package com.suke.agent.tool.scraping;

import com.alibaba.fastjson2.JSON;
import com.suke.agent.tool.cleaning.CsvUtils;
import com.alibaba.fastjson2.JSONObject;
import org.jsoup.Jsoup;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.util.Map;
import java.util.Set;

@Component
public class UrlFetchTool {

    private static final int MAX_REDIRECTS = 3;

    @Tool(description = "抓取指定URL的网页内容，返回HTML文本")
    public String fetchUrl(
            @ToolParam(description = "目标URL，必须是合法的HTTP/HTTPS地址") String url,
            @ToolParam(description = "超时时间（秒），默认30") int timeoutSeconds) {

        if (url == null || url.isBlank()) {
            return CsvUtils.errorJson("URL不能为空");
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return CsvUtils.errorJson("只支持HTTP和HTTPS协议");
        }

        URI uri;
        try {
            uri = new URI(url);
            String scheme = uri.getScheme();
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return CsvUtils.errorJson("只支持HTTP和HTTPS协议");
            }
        } catch (Exception e) {
            return CsvUtils.errorJson("URL格式不合法");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return CsvUtils.errorJson("URL缺少主机地址");
        }

        String ssrfError = checkSSRF(host);
        if (ssrfError != null) {
            return CsvUtils.errorJson(ssrfError);
        }

        int timeout = (timeoutSeconds > 0 ? timeoutSeconds : 30) * 1000;

        try {
            String currentUrl = url;
            for (int i = 0; i <= MAX_REDIRECTS; i++) {
                org.jsoup.Connection.Response response = Jsoup.connect(currentUrl)
                        .userAgent("Mozilla/5.0 (compatible; DataAnalysisBot/1.0)")
                        .timeout(timeout)
                        .followRedirects(false)
                        .execute();

                int status = response.statusCode();
                if (status >= 300 && status < 400) {
                    String location = response.header("Location");
                    if (location == null || location.isBlank()) {
                        return CsvUtils.errorJson("重定向缺少Location头");
                    }
                    URI redirectUri = URI.create(location);
                    if (!redirectUri.isAbsolute()) {
                        redirectUri = new URI(currentUrl).resolve(redirectUri);
                    }
                    String redirectHost = redirectUri.getHost();
                    if (redirectHost == null) {
                        return CsvUtils.errorJson("重定向URL缺少主机地址");
                    }
                    String redirectError = checkSSRF(redirectHost);
                    if (redirectError != null) {
                        return CsvUtils.errorJson("重定向目标" + redirectError);
                    }
                    currentUrl = redirectUri.toString();
                    continue;
                }

                String html = response.body();
                JSONObject result = new JSONObject();
                result.put("success", true);
                result.put("url", url);
                result.put("html", html);
                result.put("statusCode", status);
                result.put("contentLength", html.length());
                return result.toJSONString();
            }
            return CsvUtils.errorJson("超过最大重定向次数");
        } catch (Exception e) {
            return CsvUtils.errorJson("抓取失败");
        }
    }

    private String checkSSRF(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);
            if (isPrivateOrReserved(address)) {
                return "不允许访问内网或保留地址: " + host;
            }
            return null;
        } catch (Exception e) {
            return "域名解析失败: " + host;
        }
    }


    private boolean isPrivateOrReserved(InetAddress addr) {
        return addr.isLoopbackAddress()
                || addr.isSiteLocalAddress()
                || addr.isLinkLocalAddress()
                || addr.isAnyLocalAddress()
                || addr.isMulticastAddress();
    }
}
