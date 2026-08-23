package com.ssa.oauth.controller;

import com.ssa.oauth.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 权限校验控制器
 * 提供权限校验的核心接口，供业务微服务兜底使用
 * 
 * @author ssa
 */
@RestController
@RequestMapping("/auth/v1/permission")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    /**
     * 检查用户是否有指定权限
     */
    @GetMapping("/check")
    public ResponseEntity<Boolean> checkPermission(
            @RequestParam String userId,
            @RequestParam String permissionCode) {
        boolean hasPermission = permissionService.hasPermission(userId, permissionCode);
        return ResponseEntity.ok(hasPermission);
    }

    /**
     * 检查用户是否有任意一个权限
     */
    @PostMapping("/check-any")
    public ResponseEntity<Boolean> checkAnyPermission(
            @RequestParam String userId,
            @RequestBody List<String> permissionCodes) {
        boolean hasAnyPermission = permissionService.hasAnyPermission(userId, permissionCodes);
        return ResponseEntity.ok(hasAnyPermission);
    }

    /**
     * 检查用户是否有所有权限
     */
    @PostMapping("/check-all")
    public ResponseEntity<Boolean> checkAllPermissions(
            @RequestParam String userId,
            @RequestBody List<String> permissionCodes) {
        boolean hasAllPermissions = permissionService.hasAllPermissions(userId, permissionCodes);
        return ResponseEntity.ok(hasAllPermissions);
    }

    /**
     * 获取用户的所有权限
     */
    @GetMapping("/user/{userId}/permissions")
    public ResponseEntity<List<String>> getUserPermissions(@PathVariable String userId) {
        List<String> permissions = permissionService.getUserPermissions(userId);
        return ResponseEntity.ok(permissions);
    }

    /**
     * 检查用户是否有指定角色
     */
    @GetMapping("/role/check")
    public ResponseEntity<Boolean> checkRole(
            @RequestParam String userId,
            @RequestParam String roleCode) {
        boolean hasRole = permissionService.hasRole(userId, roleCode);
        return ResponseEntity.ok(hasRole);
    }
}