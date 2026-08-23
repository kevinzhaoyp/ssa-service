package com.ssa.plugin.pushWay.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;
//自动刷新配置内容
@RefreshScope
@Configuration
@Data
public class SmsConfig {
    /**
     * 应用标识
     */
    @Value("${aliyun.sms.accessKeyId:}")
    private String AccessKeyId;
    /**
     * 应用安全密钥
     */
    @Value("${aliyun.sms.accessKeySecret:}")
    private String AccessKeySecret;

    @Value("${aliyun.sms.regionId:cn-hangzhou}")
    private String RegionId;

    @Value("${aliyun.sms.signName:SSA}")
    private String SignName; // 签名

    @Value("${aliyun.sms.templateCode:SMS_184220802}")
    private String TemplateCode;
}
