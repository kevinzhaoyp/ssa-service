package com.ssa.oauth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求 DTO
 * 
 * @author ssa
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    /**
     * 登录类型
     * password: 账号密码登录
     * phone: 手机验证码登录
     * wechat: 微信登录
     * qq: QQ登录
     */
    @NotBlank(message = "登录类型不能为空")
    private String loginType;

    /**
     * 用户名/手机号/邮箱
     */
    private String identifier;

    /**
     * 密码（账号密码登录时使用）
     */
    private String password;

    /**
     * 验证码（短信登录时使用）
     */
    private String code;

    /**
     * 第三方登录的code（微信/QQ登录时使用）
     */
    private String thirdPartyCode;

    /**
     * 客户端 ID
     */
    private String clientId;

    /**
     * 客户端密钥
     */
    private String clientSecret;

    /**
     * 验证码
     */
    private String captcha;

    /**
     * 验证码 KEY
     */
    private String captchaKey;
}