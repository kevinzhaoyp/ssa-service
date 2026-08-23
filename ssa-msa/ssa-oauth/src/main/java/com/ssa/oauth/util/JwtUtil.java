package com.ssa.oauth.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTPayload;
import cn.hutool.jwt.JWTUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JWT工具类
 * 
 * @author ssa
 */
@Component
@Slf4j
public class JwtUtil {

    @Value("${jwt.secret:}")
    private String secret;

    @Value("${jwt.access-token-expiration:7200}") // 默认2小时
    private Long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration:604800}") // 默认7天
    private Long refreshTokenExpiration;

    @Value("${oauth.jwt.issuer:https://ssa-oauth.example.com}")
    private String issuer;

    /**
     * 生成访问令牌
     */
    public String generateAccessToken(String username, String userId) {
        Map<String, Object> payload = createBasePayload(username, userId, accessTokenExpiration);
        payload.put("type", "access");
        return JWTUtil.createToken(payload, secret.getBytes());
    }

    /**
     * 生成刷新令牌
     */
    public String generateRefreshToken(String username, String userId) {
        Map<String, Object> payload = createBasePayload(username, userId, refreshTokenExpiration);
        payload.put("type", "refresh");
        return JWTUtil.createToken(payload, secret.getBytes());
    }

    /**
     * 创建基础载荷
     */
    private Map<String, Object> createBasePayload(String username, String userId, Long expiration) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(JWTPayload.ISSUER, issuer); // 签发者
        payload.put(JWTPayload.SUBJECT, username); // 主题
        payload.put(JWTPayload.JWT_ID, UUID.randomUUID().toString()); // JWT ID
        payload.put("user_id", userId); // 用户ID
        payload.put(JWTPayload.EXPIRES_AT, System.currentTimeMillis() / 1000 + expiration); // 过期时间
        payload.put(JWTPayload.NOT_BEFORE, System.currentTimeMillis() / 1000); // 生效时间
        payload.put(JWTPayload.ISSUED_AT, System.currentTimeMillis() / 1000); // 签发时间
        return payload;
    }

    /**
     * 验证JWT令牌
     */
    public boolean validateToken(String token) {
        try {
            if (StrUtil.isBlank(token)) {
                return false;
            }
            JWT jwt = JWTUtil.parseToken(token);
            if (jwt == null) {
                return false;
            }
            // 验证签名
            boolean verify = jwt.setKey(secret.getBytes()).verify();
            if (!verify) {
                return false;
            }
            // 检查是否过期
            JSONObject payloads = jwt.getPayloads();
            Long expiration = payloads.getLong(JWTPayload.EXPIRES_AT);
            if (expiration != null) {
                long currentTimeSeconds = System.currentTimeMillis() / 1000;
                return expiration > currentTimeSeconds;
            }
            return true;
        } catch (Exception e) {
            log.error("JWT验证失败", e);
            return false;
        }
    }

    /**
     * 解析JWT令牌获取载荷
     */
    public JSONObject getClaimsFromToken(String token) {
        try {
            JWT jwt = JWTUtil.parseToken(token);
            if (jwt != null) {
                jwt.setKey(secret.getBytes());
                return jwt.getPayloads();
            }
        } catch (Exception e) {
            log.error("解析JWT令牌失败", e);
        }
        return null;
    }

    /**
     * 从令牌中获取用户名
     */
    public String getUsernameFromToken(String token) {
        JSONObject claims = getClaimsFromToken(token);
        return claims != null ? claims.getStr(JWTPayload.SUBJECT) : null;
    }

    /**
     * 从令牌中获取用户ID
     */
    public String getUserIdFromToken(String token) {
        JSONObject claims = getClaimsFromToken(token);
        return claims != null ? claims.getStr("user_id") : null;
    }

    /**
     * 从令牌中获取过期时间
     */
    public Date getExpirationDateFromToken(String token) {
        JSONObject claims = getClaimsFromToken(token);
        if (claims != null) {
            Long expiration = claims.getLong(JWTPayload.EXPIRES_AT);
            if (expiration != null) {
                return new Date(expiration * 1000);
            }
        }
        return null;
    }
}