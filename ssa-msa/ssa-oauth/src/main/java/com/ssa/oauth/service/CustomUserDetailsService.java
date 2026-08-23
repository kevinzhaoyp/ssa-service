package com.ssa.oauth.service;

import com.ssa.oauth.entity.SysUser;
import com.ssa.oauth.service.sqlapi.SqlApiPermissionService;
import com.ssa.oauth.service.sqlapi.SqlApiUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 用户详情服务
 * 
 * @author ssa
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final SqlApiUserService sqlApiUserService;
    private final SqlApiPermissionService sqlApiPermissionService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.info("Loading user by username: {}", username);
        SysUser user = sqlApiUserService.loadUserByIdentifier(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        return enrichPermissions(user);
    }

    /**
     * 根据手机号查找用户
     */
    public UserDetails loadUserByPhone(String phone) throws UsernameNotFoundException {
        log.info("Loading user by phone: {}", phone);
        SysUser user = sqlApiUserService.loadUserByIdentifier(phone)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with phone: " + phone));
        return enrichPermissions(user);
    }

    /**
     * 根据邮箱查找用户
     */
    public UserDetails loadUserByEmail(String email) throws UsernameNotFoundException {
        log.info("Loading user by email: {}", email);
        SysUser user = sqlApiUserService.loadUserByIdentifier(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
        return enrichPermissions(user);
    }

    /**
     * 根据用户ID查找用户
     */
    public SysUser loadUserById(String userId) throws UsernameNotFoundException {
        log.info("Loading user by id: {}", userId);
        SysUser user = sqlApiUserService.loadUserById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + userId));
        return enrichPermissions(user);
    }

    private SysUser enrichPermissions(SysUser user) {
        if (user != null && user.getUserId() != null) {
            user.setPermissions(sqlApiPermissionService.getUserPermissions(user.getUserId()));
        }
        return user;
    }
}