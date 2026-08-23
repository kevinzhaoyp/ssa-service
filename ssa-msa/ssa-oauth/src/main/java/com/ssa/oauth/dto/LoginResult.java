package com.ssa.oauth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录结果DTO
 * 
 * @author ssa
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResult {

    /**
     * 令牌类型
     */
    private String tokenType;

    /**
     * 访问令牌
     */
    private String accessToken;

    /**
     * 刷新令牌
     */
    private String refreshToken;

    /**
     * 过期时间（秒）
     */
    private Long expires;

    /**
     * 用户信息
     */
    private UserInfo userInfo;

    /**
     * 是否需要绑定手机
     */
    private Boolean needBindPhone;

    /**
     * 是否首次登录
     */
    private Boolean firstLogin;

    /**
     * 用户信息内嵌类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private String userId;
        private String username;
        private String nickName;
        private String realName;
        private String phone;
        private String email;
        private String avatar;
        private String gender;
    }
}