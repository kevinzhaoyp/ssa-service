package com.ssa.oauth.service;

import com.ssa.oauth.service.sqlapi.SqlApiPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 权限校验服务
 * 提供权限校验的核心接口，供业务微服务兜底使用
 * 
 * @author ssa
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PermissionService {

    private final SqlApiPermissionService permissionService;

    /**
     * 检查用户是否有指定权限
     */
    public boolean hasPermission(String userId, String permissionCode) {
        try {
            return permissionService.hasPermission(userId, permissionCode);
        } catch (Exception e) {
            log.error("权限校验失败", e);
            return false;
        }
    }

    /**
     * 检查用户是否有任意一个权限
     */
    public boolean hasAnyPermission(String userId, List<String> permissionCodes) {
        return permissionService.hasAnyPermission(userId, permissionCodes);
    }

    /**
     * 检查用户是否有所有权限
     */
    public boolean hasAllPermissions(String userId, List<String> permissionCodes) {
        return permissionService.hasAllPermissions(userId, permissionCodes);
    }

    /**
     * 获取用户的所有权限
     */
    public List<String> getUserPermissions(String userId) {
        return permissionService.getUserPermissions(userId);
    }

    /**
     * 检查用户是否有指定角色
     */
    public boolean hasRole(String userId, String roleCode) {
        try {
            return permissionService.hasRole(userId, roleCode);
        } catch (Exception e) {
            log.error("角色校验失败", e);
            return false;
        }
    }
}