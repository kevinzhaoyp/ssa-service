package com.ssa.apis.security.model.dto;

import lombok.Data;

import java.util.Set;

/**
 * 用户认证信息
 *
 * @author ssa
 *
 */
@Data
public class UserAuthInfo {

    private String userId;

    private String username;

    private String nickname;

    private String realName;

    private String password;

    private String avatar;

    private String mobile;

    private String orgName;

    private Integer status;

    private Set<String> roles;

    private Set<String> perms;

    private Integer dataScope;

    private Boolean firstLogin;

}
