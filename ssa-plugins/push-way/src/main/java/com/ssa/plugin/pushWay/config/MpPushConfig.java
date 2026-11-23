package com.ssa.plugin.pushWay.config;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 服务号模板消息凭证 —— push_config 表驱动（2026-11-23 起）。
 *
 * 取值只来自 DB(push_config, channel='mp')，不走环境变量/Spring 配置。
 * 模板在 push_template 表（channel='mp'）。
 * ⚠️ IP 白名单要加服务器出口 IP（不加报 40164）。
 */
@Component
public class MpPushConfig {

    @Resource
    private PushRegistry registry;

    public String getAppid() {
        return registry.account("mp", "appid");
    }

    public String getSecret() {
        return registry.account("mp", "secret");
    }

    /** 跳转的小程序 appid（与服务号同主体才可跳） */
    public String getMiniprogramAppid() {
        return registry.account("mp", "miniprogram_appid");
    }

    /** 点击消息时无小程序跳转能力的 H5 兜底地址 */
    public String getFallbackUrl() {
        return registry.account("mp", "fallback_url");
    }

    public Integer getTimeoutMs() {
        return PushRegistry.intOf(registry.account("mp", "timeout_ms"), 5000);
    }
}
