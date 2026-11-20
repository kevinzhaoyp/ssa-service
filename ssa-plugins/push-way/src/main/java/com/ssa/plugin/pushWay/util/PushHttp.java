package com.ssa.plugin.pushWay.util;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * 极简 HTTP 工具：推送渠道只需要 GET / POST JSON 两种能力。
 * 用 JDK 自带 HttpClient + Jackson 3（Spring Boot 4 自带），不额外引依赖。
 */
@Slf4j
public final class PushHttp {

    /** Jackson 3 的 ObjectMapper 不可变且线程安全，可做单例 */
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private PushHttp() {
    }

    /** GET，返回解析后的 JSON；网络异常/非 200/非 JSON 返回 null（调用方按可重试处理） */
    public static JsonNode get(String url, int timeoutMs) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .GET()
                    .build();
            HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                log.warn("push http GET HTTP {} -> {}", resp.statusCode(), brief(url));
                return null;
            }
            return MAPPER.readTree(resp.body());
        } catch (Exception e) {
            log.warn("push http GET {} 失败: {}", brief(url), e.getMessage());
            return null;
        }
    }

    /** POST JSON，返回解析后的 JSON；网络异常/非 200/非 JSON 返回 null */
    public static JsonNode postJson(String url, Map<String, Object> body, int timeoutMs) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                log.warn("push http POST HTTP {} -> {}, body={}", resp.statusCode(), brief(url), brief(resp.body()));
                return null;
            }
            return MAPPER.readTree(resp.body());
        } catch (Exception e) {
            log.warn("push http POST {} 失败: {}", brief(url), e.getMessage());
            return null;
        }
    }

    /** 日志脱敏：access_token 出现在 URL 里，不要打全 */
    private static String brief(String s) {
        if (s == null) {
            return "";
        }
        String out = s.length() > 120 ? s.substring(0, 120) + "..." : s;
        return out.replaceAll("access_token=[^&]+", "access_token=***");
    }
}
