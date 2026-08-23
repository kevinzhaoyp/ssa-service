package com.ssa.oauth.config;

import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Configuration;

/**
 * Nacos配置类
 * 启用服务发现功能
 * 
 * @author ssa
 */
@Configuration
@EnableDiscoveryClient
public class NacosConfig {
}