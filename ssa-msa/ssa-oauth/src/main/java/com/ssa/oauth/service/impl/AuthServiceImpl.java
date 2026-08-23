package com.ssa.oauth.service.impl;

import cn.hutool.json.JSONObject;
import com.ssa.oauth.dto.LoginRequest;
import com.ssa.oauth.dto.LoginResult;
import com.ssa.oauth.entity.SysUser;
import com.ssa.oauth.service.AuthService;
import com.ssa.oauth.service.CustomUserDetailsService;
import com.ssa.oauth.service.SmsCodeService;
import com.ssa.oauth.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现类
 * 
 * @author ssa
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final SmsCodeService smsCodeService;

    @Value("${jwt.refresh-token-expiration:604800}") // 默认7天
    private Long refreshTokenExpiration;

    @Override
    public LoginResult login(LoginRequest loginRequest) {
        try {
            SysUser user = switch (loginRequest.getLoginType()) {
                case "password" ->
                    // 账号密码登录
                        authenticateUser(loginRequest.getIdentifier(), loginRequest.getPassword());
                case "phone" ->
                    // 手机验证码登录
                        authenticateByPhone(loginRequest.getIdentifier(), loginRequest.getCode());
                case "wechat" ->
                    // 微信登录
                        authenticateByWechat(loginRequest.getThirdPartyCode());
                case "qq" ->
                    // QQ登录
                        authenticateByQQ(loginRequest.getThirdPartyCode());
                default -> throw new IllegalArgumentException("不支持的登录类型: " + loginRequest.getLoginType());
            };
            
            // 根据不同登录类型处理

            if (user == null) {
                throw new IllegalArgumentException("登录失败，请检查登录信息");
            }

            // 生成 JWT令牌
            String accessToken = jwtUtil.generateAccessToken(user.getUsername(), user.getUserId());
            String refreshToken = jwtUtil.generateRefreshToken(user.getUsername(), user.getUserId());

            // 将刷新令牌存储到Redis中，用于后续验证
            String refreshTokenKey = "refresh_token:" + refreshToken;
            redisTemplate.opsForValue().set(refreshTokenKey, user.getUserId(), refreshTokenExpiration, TimeUnit.SECONDS);

            // 计算过期时间
            Long expires = System.currentTimeMillis() / 1000 + 7200; // 2小时后过期

            // 构建用户信息
            LoginResult.UserInfo userInfo = LoginResult.UserInfo.builder()
                    .userId(user.getUserId())
                    .username(user.getUsername())
                    .nickName(user.getNickName())
                    .realName(user.getRealName())
                    .phone(user.getPhone())
                    .email(user.getEmail())
                    .avatar(user.getAvatar())
                    .gender(user.getGender())
                    .build();

            return LoginResult.builder()
                    .tokenType("Bearer")
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .expires(expires)
                    .userInfo(userInfo)
                    .firstLogin(isFirstLogin(user.getUserId()))
                    .build();

        } catch (AuthenticationException e) {
            log.error("认证失败", e);
            throw new IllegalArgumentException("认证失败: " + e.getMessage());
        }
    }

    @Override
    public LoginResult refreshToken(String refreshToken) {
        // 验证刷新令牌是否存在且有效
        String refreshTokenKey = "refresh_token:" + refreshToken;
        String userId = redisTemplate.opsForValue().get(refreshTokenKey);
        
        if (userId == null) {
            throw new IllegalArgumentException("无效的刷新令牌");
        }

        // 从 Redis中删除旧的刷新令牌
        redisTemplate.delete(refreshTokenKey);

        // 根据用户 ID获取用户信息
        SysUser user = userDetailsService.loadUserById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        // 生成新的访问令牌和刷新令牌
        String newAccessToken = jwtUtil.generateAccessToken(user.getUsername(), user.getUserId());
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getUsername(), user.getUserId());

        // 存储新的刷新令牌
        String newRefreshTokenKey = "refresh_token:" + newRefreshToken;
        redisTemplate.opsForValue().set(newRefreshTokenKey, user.getUserId(), refreshTokenExpiration, TimeUnit.SECONDS);

        // 计算过期时间
        Long expires = System.currentTimeMillis() / 1000 + 7200; // 2小时后过期

        return LoginResult.builder()
                .tokenType("Bearer")
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expires(expires)
                .build();
    }

    @Override
    public void logout(String token) {
        // 从令牌中提取用户信息并放入黑名单
        String userId = jwtUtil.getUserIdFromToken(token);
        if (userId != null) {
            String jti = jwtUtil.getClaimsFromToken(token).getStr("jti");
            if (jti != null) {
                // 将JWT ID加入黑名单，直到过期时间
                Date expiration = jwtUtil.getExpirationDateFromToken(token);
                if (expiration != null) {
                    long ttl = expiration.getTime() - System.currentTimeMillis();
                    if (ttl > 0) {
                        redisTemplate.opsForValue().set("blacklist:token:" + jti, "1", ttl, TimeUnit.MILLISECONDS);
                    }
                }
            }
        }
    }

    @Override
    public boolean validateToken(String token) {
        // 检查令牌是否在黑名单中
        JSONObject claims = jwtUtil.getClaimsFromToken(token);
        if (claims != null) {
            String jti = claims.getStr("jti");
            if (jti != null && redisTemplate.hasKey("blacklist:token:" + jti)) {
                return false;
            }
        }
        return jwtUtil.validateToken(token);
    }

    /**
     * 用户名密码认证
     */
    private SysUser authenticateUser(String username, String password) {
        try {
            // 使用Spring Security进行认证
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
            
            if (authentication.isAuthenticated()) {
                return (SysUser) userDetailsService.loadUserByUsername(username);
            }
        } catch (AuthenticationException e) {
            log.error("用户名密码认证失败", e);
        }
        return null;
    }

    /**
     * 手机验证码认证
     */
    private SysUser authenticateByPhone(String phone, String code) {
        // 验证手机验证码
        boolean isValid = smsCodeService.validateSmsCode(phone, code);
        if (!isValid) {
            throw new IllegalArgumentException("手机验证码错误或已过期");
        }
        
        // 根据手机号获取用户
        return (SysUser) userDetailsService.loadUserByPhone(phone);
    }

    /**
     * 微信登录认证
     */
    private SysUser authenticateByWechat(String code) {
        // 这里应该调用微信API验证code
        // 为了演示，暂时返回模拟用户
        log.info("微信登录认证，code: {}", code);
        // TODO: 实现微信登录逻辑
        throw new UnsupportedOperationException("微信登录功能尚未实现");
    }

    /**
     * QQ登录认证
     */
    private SysUser authenticateByQQ(String code) {
        // 这里应该调用QQ API验证code
        // 为了演示，暂时返回模拟用户
        log.info("QQ登录认证，code: {}", code);
        // TODO: 实现QQ登录逻辑
        throw new UnsupportedOperationException("QQ登录功能尚未实现");
    }

    /**
     * 检查是否首次登录
     */
    private boolean isFirstLogin(String userId) {
        String key = "user:first_login:" + userId;
        Boolean exists = redisTemplate.hasKey(key);
        if (!exists) {
            // 标记为已登录过
            redisTemplate.opsForValue().set(key, "1", 30, TimeUnit.DAYS);
            return true;
        }
        return false;
    }
}
