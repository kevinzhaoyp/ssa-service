package com.ssa.plugin.pushWay.config;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 小程序订阅消息凭证 —— push_config 表驱动（2026-11-23 起）。
 *
 * 取值只来自 DB(push_config, channel='wechat')，不走环境变量/Spring 配置。
 * 换密钥：UPDATE push_config 后等 60s 缓存过期，或调 pushWay.reload 即时生效。
 */
@Component
public class WechatPushConfig {

    @Resource
    private PushRegistry registry;

    public String getAppid() {
        return registry.account("wechat", "appid");
    }

    public String getSecret() {
        return registry.account("wechat", "secret");
    }

    /** formal 正式版 / trial 体验版 / developer 开发版（联调体验版改成 trial） */
    public String getMiniprogramState() {
        String v = registry.account("wechat", "miniprogram_state");
        return v == null || v.trim().isEmpty() ? "formal" : v.trim();
    }

    public Integer getTimeoutMs() {
        return PushRegistry.intOf(registry.account("wechat", "timeout_ms"), 5000);
    }
}
