package com.ssa.oauth.entity;

import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 用户实体类
 * 
 * @author ssa
 */
@Data
public class SysUser implements UserDetails {

    private String userId;

    private String username;

    private String password;

    private String nickName;

    private String realName;

    private String phone;

    private String email;

    private String avatar;

    private String gender;

    private Boolean status = true;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private LocalDateTime lastLoginTime;

    private Integer loginCount = 0;

    private List<SysRole> roles;

    private List<String> permissions;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (permissions != null && !permissions.isEmpty()) {
            return permissions.stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();
        }
        return roles != null ? roles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(permission -> new SimpleGrantedAuthority(permission.getPermissionCode()))
                .toList() : List.of();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return Boolean.TRUE.equals(status);
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(status);
    }
}