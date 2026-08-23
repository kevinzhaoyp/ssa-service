package com.ssa.apis.filter;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTPayload;
import cn.hutool.jwt.JWTUtil;
import com.ssa.apis.security.constant.SecurityConstants;
import com.ssa.apis.security.model.SysUserDetails;
import com.ssa.apis.security.util.JwtUtils;
import com.ssa.apis.security.util.SecurityUtils;
import com.ssa.core.utils.ApiResultCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.ssa.apis.security.store.InMemoryTokenStore;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import com.ssa.core.service.CurrentUser;
import com.ssa.core.service.UserContext;
import java.io.IOException;

/**
 * JWT token 校验过滤器
 *
 * @author ssa
 */
public class JwtValidationFilter extends OncePerRequestFilter {

    private final InMemoryTokenStore tokenStore;

    private final byte[] secretKey;

    public JwtValidationFilter(InMemoryTokenStore tokenStore, String secretKey) {
        this.tokenStore = tokenStore;
        this.secretKey = secretKey.getBytes();
    }


    /**
     * 从请求中获取 JWT Token，校验 JWT Token 是否合法
     * <p>
     * 如果合法则将 Authentication 设置到 Spring Security Context 上下文中
     * 如果不合法则清空 Spring Security Context 上下文，并直接返回响应
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String token = request.getHeader(HttpHeaders.AUTHORIZATION);

        try {
            if (StrUtil.isNotBlank(token) && token.startsWith(SecurityConstants.JWT_TOKEN_PREFIX)) {
                token = token.substring(SecurityConstants.JWT_TOKEN_PREFIX.length()); // 去除 Bearer 前缀

                // 解析 Token
                JWT jwt = JWTUtil.parseToken(token);

                // 检查 Token 是否有效(验签 + 是否过期)
                boolean isValidate = jwt.setKey(secretKey).validate(0);
                if (!isValidate) {
                    ResponseUtils.writeErrMsg(response, ApiResultCode.TOKEN_INVALID);
                    return;
                }

                // 检查 Token 是否已被加入黑名单(注销)
                JSONObject payloads = jwt.getPayloads();
                String jti = payloads.getStr(JWTPayload.JWT_ID);
                boolean isTokenBlacklisted = tokenStore.isBlacklisted(jti);
                if (isTokenBlacklisted) {
                    ResponseUtils.writeErrMsg(response, ApiResultCode.TOKEN_INVALID);
                    return;
                }

                // Token 有效将其解析为 Authentication 对象，并设置到 Spring Security 上下文中
                Authentication authentication = JwtUtils.getAuthentication(payloads);
                SecurityContextHolder.getContext().setAuthentication(authentication);

                // 将当前登录用户写入线程上下文，供 sql-api 注入 params.cur_user 使用
                SysUserDetails user = SecurityUtils.getUser();
                if (user != null) {
                    CurrentUser curUser = new CurrentUser();
                    curUser.setUserId(user.getUserId());
                    curUser.setUserName(user.getUsername());
                    curUser.setRealName(user.getRealName());
                    UserContext.set(curUser);
                }

            }
        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            ResponseUtils.writeErrMsg(response, ApiResultCode.TOKEN_INVALID);
            return;
        }
        // Token 有效或无 Token 时继续执行过滤链（SqlApiManager 在链内读取 UserContext）
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 请求处理完成后清理线程上下文，防止线程池复用导致用户信息串号 / 内存泄漏
            UserContext.clear();
        }
    }
}
