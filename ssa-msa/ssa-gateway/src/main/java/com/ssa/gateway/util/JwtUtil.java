package com.ssa.gateway.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTPayload;
import cn.hutool.jwt.JWTUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JWT工具类（使用Hutool实现）
 * 用于验证和解析JWT令牌
 * 
 * @author ssa
 */
@Component
@Slf4j
public class JwtUtil {

    @Value("${jwt.secret:mySecretKeyForSSAGateway}")
    private String secret = "mySecretKeyForSSAGateway";

    /**
     * 验证JWT令牌是否有效
     */
    public boolean validateToken(String token) {
        try {
            if (StrUtil.isBlank(token)) {
                return false;
            }
            JWT jwt = JWTUtil.parseToken(token);
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
     * 从 JWT令牌中获取用户名
     */
    public String getUsernameFromToken(String token) {
        try {
            JWT jwt = JWTUtil.parseToken(token);
            jwt.setKey(secret.getBytes());
            JSONObject payloads = jwt.getPayloads();
            return payloads.getStr(JWTPayload.SUBJECT); // subject 通常存放用户名
        } catch (Exception e) {
            log.error("解析JWT令牌中的用户名失败", e);
        }
        return null;
    }
}