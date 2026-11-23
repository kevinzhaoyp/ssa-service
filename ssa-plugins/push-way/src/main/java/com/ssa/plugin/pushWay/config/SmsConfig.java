package com.ssa.plugin.pushWay.config;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 阿里云短信凭证 —— push_config 表驱动（2026-11-23 起）。
 *
 * 取值只来自 DB(push_config, channel='sms')，不走环境变量/Spring 配置。
 * 模板在 push_template 表（channel='sms'，fields_map 为参数顺序数组）。
 */
@Component
public class SmsConfig {

    @Resource
    private PushRegistry registry;

    public String getAccessKeyId() {
        return registry.account("sms", "access_key_id");
    }

    public String getAccessKeySecret() {
        return registry.account("sms", "access_key_secret");
    }

    public String getRegionId() {
        String v = registry.account("sms", "region_id");
        return v == null || v.trim().isEmpty() ? "cn-hangzhou" : v.trim();
    }

    public String getSignName() {
        return registry.account("sms", "sign_name");
    }

    /** 验证码短信模板（旧接口 send 用） */
    public String getTemplateCode() {
        return registry.account("sms", "code_template");
    }
}
