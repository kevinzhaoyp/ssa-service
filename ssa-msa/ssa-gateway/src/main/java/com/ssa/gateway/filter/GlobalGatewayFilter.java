package com.ssa.gateway.filter;

import cn.hutool.core.util.StrUtil;
import com.ssa.gateway.config.GatewayRateLimitProperties;
import com.ssa.gateway.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * 全局过滤器
 * 实现请求转发、流量控制、全局过滤等功能
 * 
 * @author ssa
 */
@Component
@Slf4j
public class GlobalGatewayFilter implements GlobalFilter, Ordered {

    private static final String X_REAL_IP = "X-Real-IP";
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REQUEST_ID = "X-Request-ID";
    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Autowired
    private ReactiveStringRedisTemplate reactiveRedisTemplate;
    
    @Autowired
    private GatewayRateLimitProperties gatewayProperties;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        // 记录请求开始时间
        long startTime = System.currentTimeMillis();
        
        // 1. 添加自定义请求头
        String requestId = generateRequestId();
        ServerHttpRequest.Builder requestBuilder = request.mutate();
        requestBuilder.header(X_REQUEST_ID, requestId);
        
        // 设置真实 IP头
        String realIp = getClientIpAddress(request);
        requestBuilder.header(X_REAL_IP, realIp);
        
        // 2. 检查JWT Token有效性（只验证，不处理认证逻辑）
        String authHeader = request.getHeaders().getFirst(AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length());
            if (!jwtUtil.validateToken(token)) {
                // 无效的令牌，返回401错误
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                String message = "{\"code\":401,\"message\":\"Unauthorized: Invalid or expired token\",\"timestamp\":\"" + 
                                LocalDateTime.now() + "\"}";
                DataBuffer buffer = response.bufferFactory().wrap(message.getBytes(StandardCharsets.UTF_8));
                response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
                return response.writeWith(Mono.just(buffer));
            }
            
            // 有效的令牌，提取用户名并添加到请求头
            String username = jwtUtil.getUsernameFromToken(token);
            if (StrUtil.isNotBlank(username)) {
                requestBuilder.header("X-User-Name", username);
            }
        }
        
        // 3. 流量控制
        if (gatewayProperties.getRateLimit().isEnabled()) {
            return checkRateLimit(exchange, realIp)
                .flatMap(canProceed -> {
                    if (!canProceed) {
                        // 请求超出限制
                        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        String message = "{\"code\":429,\"message\":\"Too Many Requests\",\"timestamp\":\"" + 
                                        LocalDateTime.now() + "\"}";
                        DataBuffer buffer = response.bufferFactory().wrap(message.getBytes(StandardCharsets.UTF_8));
                        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
                        return response.writeWith(Mono.just(buffer));
                    }

                    // 4. 继续处理请求
                    ServerWebExchange mutatedExchange = exchange.mutate().request(requestBuilder.build()).build();
                    
                    return chain.filter(mutatedExchange).doFinally(signalType -> {
                        // 记录请求完成日志
                        long duration = System.currentTimeMillis() - startTime;
                        log.info("Request: {} {} | IP: {} | RequestId: {} | Duration: {}ms | Status: {}", 
                                 request.getMethod(), request.getPath(), realIp, requestId, duration, 
                                 response.getStatusCode());
                    });
                });
        } else {
            // 如果未启用限流，则直接处理请求
            ServerWebExchange mutatedExchange = exchange.mutate().request(requestBuilder.build()).build();
            
            return chain.filter(mutatedExchange).doFinally(signalType -> {
                // 记录请求完成日志
                long duration = System.currentTimeMillis() - startTime;
                log.info("Request: {} {} | IP: {} | RequestId: {} | Duration: {}ms | Status: {}", 
                         request.getMethod(), request.getPath(), realIp, requestId, duration, 
                         response.getStatusCode());
            });
        }
    }

    /**
     * 检查流量限制
     */
    private Mono<Boolean> checkRateLimit(ServerWebExchange exchange, String clientIp) {
        String rateLimitKey = gatewayProperties.getRateLimit().getKeyPrefix() + clientIp;
        int maxRequests = gatewayProperties.getRateLimit().getMaxRequestsPerMinute();
        
        // 使用Redis实现简单的滑动窗口限流
        return reactiveRedisTemplate.opsForValue()
            .increment(rateLimitKey)
            .flatMap(count -> {
                if (count.longValue() == 1) {
                    // 第一次访问，设置过期时间为1分钟
                    return reactiveRedisTemplate.expire(rateLimitKey, java.time.Duration.ofMinutes(1))
                        .then(Mono.just(true));
                } else if (count > maxRequests) {
                    // 超出限制
                    return Mono.just(false);
                } else {
                    return Mono.just(true);
                }
            })
            .switchIfEmpty(Mono.just(true));
    }

    /**
     * 获取客户端真实 IP地址
     */
    private String getClientIpAddress(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst(X_FORWARDED_FOR);
        if (StrUtil.isNotEmpty(xForwardedFor) && !xForwardedFor.contains("unknown")) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeaders().getFirst(X_REAL_IP);
        if (StrUtil.isNotEmpty(xRealIp)) {
            return xRealIp;
        }
        
        return request.getRemoteAddress().getAddress().getHostAddress();
    }

    /**
     * 生成请求 ID
     */
    private String generateRequestId() {
        return System.currentTimeMillis() + "-" + Thread.currentThread().getId();
    }

    @Override
    public int getOrder() {
        return -1; // 设置较高的优先级
    }
}