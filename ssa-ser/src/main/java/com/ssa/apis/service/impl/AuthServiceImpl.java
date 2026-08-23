package com.ssa.apis.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTException;
import cn.hutool.jwt.JWTPayload;
import cn.hutool.jwt.JWTUtil;
import com.ssa.apis.security.constant.SecurityConstants;
import com.ssa.apis.security.model.SysUserDetails;
import com.ssa.apis.security.util.JwtUtils;
import com.ssa.apis.service.AuthService;
import com.ssa.apis.service.dto.LoginResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import com.ssa.apis.security.store.InMemoryTokenStore;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;


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
    private final InMemoryTokenStore tokenStore;
    private final LoginAuditService loginAuditService;
    private final UserDetailsService userDetailsService;

    // RefreshToken 过期时间（秒），默认 30 天
    @Value("${security.refresh.ttl:2592000}")
    private long refreshTtlSeconds;

    /**
     * 登录
     *
     * @param username 用户名
     * @param password 密码
     * @return 登录结果
     */
    @Override
    public LoginResult login(String username, String password) {
        try {
            // 认证用户信息
            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(username.toLowerCase().trim(), password);
            // 认证
            Authentication authentication = authenticationManager.authenticate(authenticationToken);
            // 认证成功，生成Token
            String accessToken = JwtUtils.createToken(authentication);
            // 生成 RefreshToken 并存入内存
            String refreshToken = IdUtil.simpleUUID();
            tokenStore.saveRefreshToken(refreshToken, authentication.getName(), refreshTtlSeconds);

            Long expires = extractJwtExpires(accessToken);

            // 从数据库读取 first_login 标记（首次登录强制改密）
            Boolean firstLogin = null;
            Object principal = authentication.getPrincipal();
            if (principal instanceof SysUserDetails sud) {
                firstLogin = sud.getFirstLogin();
            }

            return LoginResult.builder()
                    .tokenType("Bearer")
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .expires(expires)
                    .firstLogin(firstLogin)
                    .build();
        } catch (AuthenticationException ex) {
            // 认证失败，发送失败事件到 RabbitMQ（审计）
            try {
                ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                String ip = null;
                String ua = null;
                if (attrs != null) {
                    var req = attrs.getRequest();
                    ua = req.getHeader("User-Agent");
                    ip = req.getHeader("X-Forwarded-For");
                    if (ip == null || ip.isEmpty()) ip = req.getRemoteAddr();
                }
                // 直接调用本地 LoginAuditService 记录失败审计
                loginAuditService.auditLogin(username, null, ip, ua, null, false);
            } catch (Exception e) {
                log.warn("发送失败登录事件失败: {}", e.getMessage());
            }
            throw ex;
        }
    }

    /**
     * 使用 refreshToken 刷新并旋转：校验 -> 颁发新 accessToken + 新 refreshToken -> 作废旧的
     */
    @Override
    public LoginResult refresh(String refreshToken) {
        if (StrUtil.isBlank(refreshToken)) {
            throw new IllegalArgumentException("invalid refresh token");
        }
        String username = tokenStore.getRefreshToken(refreshToken);
        if (username == null) {
            throw new IllegalArgumentException("invalid refresh token");
        }
        tokenStore.deleteRefreshToken(refreshToken);

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );
        String accessToken = JwtUtils.createToken(authentication);

        String newRefreshToken = IdUtil.simpleUUID();
        tokenStore.saveRefreshToken(newRefreshToken, username, refreshTtlSeconds);

        Long expires = extractJwtExpires(accessToken);

        return LoginResult.builder()
                .tokenType("Bearer")
                .accessToken(accessToken)
                .refreshToken(newRefreshToken)
                .expires(expires)
                .build();
    }

    private Long extractJwtExpires(String token) {
        try {
            JWT jwt = JWTUtil.parseToken(token);
            JSONObject payloads = jwt.getPayloads();
            return payloads.getLong(JWTPayload.EXPIRES_AT);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 注销
     */
    @Override
    public void logout() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            SecurityContextHolder.clearContext();
            return;
        }
        var request = attrs.getRequest();
        String token = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StrUtil.isNotBlank(token) && token.startsWith(SecurityConstants.JWT_TOKEN_PREFIX)) {
            token = token.substring(SecurityConstants.JWT_TOKEN_PREFIX.length());
            try {
                // 解析Token以获取有效载荷（payload）
                JSONObject payloads = JWTUtil.parseToken(token).getPayloads();
                // 解析 Token 获取 jti(JWT ID) 和 exp(过期时间)
                String jti = payloads.getStr(JWTPayload.JWT_ID);
                Long expiration = payloads.getLong(JWTPayload.EXPIRES_AT); // 过期时间(秒)
                // 如果exp存在，则计算Token剩余有效时间
                if (expiration != null) {
                    long currentTimeSeconds = System.currentTimeMillis() / 1000;
                    if (expiration < currentTimeSeconds) {
                        // Token已过期，不再加入黑名单
                        return;
                    }
                    // 将Token的jti加入黑名单，并设置剩余有效时间，使其在过期后自动从黑名单移除
                    long ttl = expiration - currentTimeSeconds;
                    tokenStore.blacklist(jti, ttl);
                } else {
                    // 如果exp不存在，说明Token永不过期，则永久加入黑名单
                    tokenStore.blacklistPermanent(jti);
                }
            }  catch (JWTException e) {
                log.warn("无法解析 token，跳过加入黑名单: {}", e.getMessage());
            } catch (Exception e) {
                log.error("登出时写入内存黑名单失败", e);
            }
        }
        // 清空Spring Security上下文
        SecurityContextHolder.clearContext();
    }

    /*
      获取验证码
      @return 验证码
     */
//    @Override
//    public CaptchaResult getCaptcha() {
//
//        String captchaType = captchaProperties.getType();
//        int width = captchaProperties.getWidth();
//        int height = captchaProperties.getHeight();
//        int interfereCount = captchaProperties.getInterfereCount();
//        int codeLength = captchaProperties.getCode().getLength();
//
//        AbstractCaptcha captcha;
//        if (CaptchaTypeEnum.CIRCLE.name().equalsIgnoreCase(captchaType)) {
//            captcha = CaptchaUtil.createCircleCaptcha(width, height, codeLength, interfereCount);
//        } else if (CaptchaTypeEnum.GIF.name().equalsIgnoreCase(captchaType)) {
//            captcha = CaptchaUtil.createGifCaptcha(width, height, codeLength);
//        } else if (CaptchaTypeEnum.LINE.name().equalsIgnoreCase(captchaType)) {
//            captcha = CaptchaUtil.createLineCaptcha(width, height, codeLength, interfereCount);
//        } else if (CaptchaTypeEnum.SHEAR.name().equalsIgnoreCase(captchaType)) {
//            captcha = CaptchaUtil.createShearCaptcha(width, height, codeLength, interfereCount);
//        } else {
//            throw new IllegalArgumentException("Invalid captcha type: " + captchaType);
//        }
//        captcha.setGenerator(codeGenerator);
//        captcha.setTextAlpha(captchaProperties.getTextAlpha());
//        captcha.setFont(captchaFont);
//
//        String captchaCode = captcha.getCode();
//        String imageBase64Data = captcha.getImageBase64Data();
//
//        // 验证码文本缓存至Redis，用于登录校验
//        String captchaKey = IdUtil.fastSimpleUUID();
//        redisTemplate.opsForValue().set(SecurityConstants.CAPTCHA_CODE_PREFIX + captchaKey, captchaCode,
//                captchaProperties.getExpireSeconds(), TimeUnit.SECONDS);
//
//        return CaptchaResult.builder()
//                .captchaKey(captchaKey)
//                .captchaBase64(imageBase64Data)
//                .build();
//    }
}
