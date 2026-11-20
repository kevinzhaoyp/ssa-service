package com.ssa.plugin.pushWay.wechat;

import com.ssa.plugin.pushWay.config.MpPushConfig;
import com.ssa.plugin.pushWay.config.WechatPushConfig;
import com.ssa.plugin.pushWay.util.PushHttp;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 微信 access_token 缓存（小程序 / 服务号两套，绝对不能混用，混用报 40001）。
 *
 * 说明：
 *  - 单实例部署用进程内缓存即可；多实例部署必须换成共享存储（DB/Redis），
 *    否则实例之间会互相顶掉 token（微信侧刷新会使旧 token 失效）。
 *  - 提前 200 秒续期，并发刷新用对象锁做单飞（single-flight）。
 */
@Slf4j
@Service
public class WxTokenProvider {

    public static final String KIND_WECHAT = "wechat";
    public static final String KIND_MP = "mp";

    private static final String TOKEN_URL =
            "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=%s&secret=%s";
    /** 提前续期窗口 */
    private static final long AHEAD_MS = 200_000L;

    private final Map<String, Cache> caches = new ConcurrentHashMap<>();

    @Resource
    private WechatPushConfig wechatConfig;
    @Resource
    private MpPushConfig mpConfig;

    /** 取 token；拿不到返回 null（调用方按可重试处理） */
    public String token(String kind) {
        Cache cache = caches.computeIfAbsent(kind, k -> new Cache());
        synchronized (cache) {
            long now = System.currentTimeMillis();
            if (cache.token != null && cache.expireAt - AHEAD_MS > now) {
                return cache.token;
            }
            boolean isMp = KIND_MP.equals(kind);
            String appid = isMp ? mpConfig.getAppid() : wechatConfig.getAppid();
            String secret = isMp ? mpConfig.getSecret() : wechatConfig.getSecret();
            if (isBlank(appid) || isBlank(secret)) {
                log.warn("微信 token 未配置（kind={}），跳过发送。请配置 push.{}.appid/secret",
                        kind, isMp ? "mp" : "wechat");
                return null;
            }
            JsonNode node = PushHttp.get(String.format(TOKEN_URL, appid, secret), timeout(kind));
            if (node == null) {
                return null;
            }
            String token = node.path("access_token").asText(null);
            long expiresIn = node.path("expires_in").asLong();
            if (isBlank(token)) {
                log.warn("微信 token 获取失败（kind={}）：{}", kind, node);
                return null;
            }
            cache.token = token;
            cache.expireAt = now + (expiresIn > 0 ? expiresIn : 7200) * 1000L;
            log.info("微信 token 已刷新（kind={}，有效期 {}s）", kind, expiresIn);
            return token;
        }
    }

    /** 遇到 40001/42001 时主动失效，下一轮重新取 */
    public void invalidate(String kind) {
        Cache cache = caches.get(kind);
        if (cache != null) {
            synchronized (cache) {
                cache.token = null;
                cache.expireAt = 0;
            }
        }
    }

    private int timeout(String kind) {
        Integer t = KIND_MP.equals(kind) ? mpConfig.getTimeoutMs() : wechatConfig.getTimeoutMs();
        return t == null || t <= 0 ? 5000 : t;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    @Data
    private static class Cache {
        private String token;
        private long expireAt;
    }
}
