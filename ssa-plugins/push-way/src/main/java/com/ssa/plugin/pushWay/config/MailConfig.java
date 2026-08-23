package com.ssa.plugin.pushWay.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

//自动刷新配置内容
@RefreshScope
@Configuration
@Data
public class MailConfig {
    @Value("${mail.smtp.auth:true}")
    private String smtpAuth;

    @Value("${mail.smtp.starttls.enable:true}")
    private Boolean smtpStarttlsEnable;

    @Value("${mail.smtp.host:smtp.163.com}")
    private String smtpHost;

    @Value("${mail.smtp.port:25}")
    private String smtpPort;

    @Value("${mail.smtp.username:pushway2025@163.com}")
    private String smtpUsername;

    @Value("${mail.smtp.password:}")
    private String smtpPassword;
}
