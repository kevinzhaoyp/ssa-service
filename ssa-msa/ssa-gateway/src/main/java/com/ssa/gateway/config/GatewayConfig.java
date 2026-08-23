package com.ssa.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * 网关配置
 * 包含熔断器、限流等配置
 * 
 * @author ssa
 */
@Configuration
public class GatewayConfig {
    /**
     * IP限流键解析器
     * 用于基于IP地址的请求限流
     */
    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(
            exchange.getRequest()
                .getHeaders()
                .getFirst("X-Forwarded-For") != null ?
                Objects.requireNonNull(exchange.getRequest().getHeaders().getFirst("X-Forwarded-For")).split(",")[0] :
                Objects.requireNonNull(exchange.getRequest().getRemoteAddress()).getAddress().getHostAddress()
        );
    }

    /**
     * 用户ID限流键解析器
     * 从请求头中提取用户ID进行限流
     */
    @Bean("userKeyResolver")
    public KeyResolver userKeyResolver() {
        return exchange -> Mono.just(
            exchange.getRequest().getHeaders().getFirst("Authorization") != null ?
                    Objects.requireNonNull(exchange.getRequest().getHeaders().getFirst("Authorization")) : "anonymous"
        );
    }
}