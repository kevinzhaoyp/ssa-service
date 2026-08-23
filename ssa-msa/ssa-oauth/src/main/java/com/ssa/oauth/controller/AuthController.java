package com.ssa.oauth.controller;

import com.ssa.oauth.dto.LoginRequest;
import com.ssa.oauth.dto.LoginResult;
import com.ssa.oauth.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * 认证控制器
 * 
 * @author ssa
 */
@RestController
@RequestMapping("/auth/v1")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    /**
     * 登录
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResult> login(@Valid @RequestBody LoginRequest loginRequest) {
        log.info("用户尝试登录，登录类型: {}", loginRequest.getLoginType());
        LoginResult result = authService.login(loginRequest);
        return ResponseEntity.ok(result);
    }

    /**
     * 刷新令牌
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResult> refresh(@RequestParam String refreshToken) {
        log.info("用户尝试刷新令牌");
        LoginResult result = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(result);
    }

    /**
     * 注销
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authorization) {
        log.info("用户尝试注销");
        String token = authorization.replace("Bearer ", "");
        authService.logout(token);
        return ResponseEntity.ok().build();
    }

    /**
     * 验证令牌
     */
    @PostMapping("/validate")
    public ResponseEntity<Boolean> validateToken(@RequestParam String token) {
        log.info("验证令牌");
        boolean isValid = authService.validateToken(token);
        return ResponseEntity.ok(isValid);
    }
}