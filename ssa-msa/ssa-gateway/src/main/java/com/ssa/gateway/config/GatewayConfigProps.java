package com.ssa.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 网关配置类
 * 启用配置属性
 * 
 * @author ssa
 */
@Configuration
@EnableConfigurationProperties(GatewayRateLimitProperties.class)
public class GatewayConfigProps {
}