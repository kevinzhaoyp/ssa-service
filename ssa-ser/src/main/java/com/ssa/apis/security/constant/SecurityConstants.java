package com.ssa.apis.security.constant;

/**
 * 缓存常量
 *
 * @author ssa
 */
public interface SecurityConstants {

    /**
     * 角色和权限缓存前缀
     */
    String ROLE_PERMS_PREFIX = "role_perms:";

    /**
     * 黑名单 token 缓存前缀
     */
    String BLACKLIST_TOKEN_PREFIX = "token:blacklist:";


    /**
     * 登录路径
     */
    String LOGIN_PATH = "/auth/v1/login";


    /**
     * 刷新路径
     */
    String REFRESH_PATH = "/auth/v1/refresh";


    /**
     * JWT Token 前缀
     */
    String JWT_TOKEN_PREFIX = "Bearer ";


    /**
     * Refresh Token 缓存前缀
     */
    String REFRESH_TOKEN_PREFIX = "token:refresh_token:";


}
