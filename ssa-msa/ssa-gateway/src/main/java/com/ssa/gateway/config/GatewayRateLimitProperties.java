package com.ssa.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关配置属性
 *
 * @author ssa
 */
@Data
@ConfigurationProperties(prefix = "ssa.gateway")
public class GatewayRateLimitProperties {

    /**
     * 流量控制配置
     */
    private RateLimit rateLimit = new RateLimit();

    @Data
    public static class RateLimit {
        /**
         * 每分钟最大请求数
         */
        private int maxRequestsPerMinute = 100;

        /**
         * 限流键前缀
         */
        private String keyPrefix = "rate_limit:";

        /**
         * 是否启用限流
         */
        private boolean enabled = true;
    }
}
