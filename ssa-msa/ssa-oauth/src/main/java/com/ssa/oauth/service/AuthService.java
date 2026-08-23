package com.ssa.oauth.service;

import com.ssa.oauth.dto.LoginRequest;
import com.ssa.oauth.dto.LoginResult;

/**
 * 认证服务接口
 * 
 * @author ssa
 */
public interface AuthService {
    /**
     * 账号密码登录
     */
    LoginResult login(LoginRequest loginRequest);

    /**
     * 刷新令牌
     */
    LoginResult refreshToken(String refreshToken);

    /**
     * 注销
     */
    void logout(String token);

    /**
     * 验证令牌
     */
    boolean validateToken(String token);
}