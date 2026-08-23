package com.ssa.apis.controller;

import com.alibaba.fastjson.JSONObject;
import com.ssa.apis.security.util.SecurityUtils;
import com.ssa.apis.service.AuthService;
import com.ssa.apis.service.dto.LoginResult;
import com.ssa.apis.service.impl.LoginAuditService;
import com.ssa.core.service.ApiRunner;
import com.ssa.core.utils.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth/v1")
@RequiredArgsConstructor
public class AuthV1 {
    private final AuthService authService;
    private final LoginAuditService loginAuditService;

    @PostMapping("/login")
    public ApiResult<LoginResult> login(HttpServletRequest request, @RequestBody JSONObject jo) {
        String username = jo.getString("username");
        String password = jo.getString("password");
        try {
            LoginResult loginResult = authService.login(username, password);
            // 异步写登录日志，避免阻塞主流程
            String userAgent = request.getHeader("User-Agent");
            String ipAddress = extractClientIp(request);
//        params.put("username", username);
//        ApiRunner.safeRun("loginLog", params, false);
            // 直接调用 LoginAuditService 异步写入登录日志并回填 sys_user
            loginAuditService.auditLogin(username, null, ipAddress, userAgent, null, true);

            return ApiResult.success(loginResult);
        } catch (Exception ex) {
            return ApiResult.failed(ex.getMessage());
        }
    }

    @PostMapping("/logout")
    public ApiResult<Object> logout() {
        authService.logout();
        return ApiResult.success("ok");
    }

    @PostMapping("/getUserInfo")
    public ApiResult<Object> getUserInfo() {
        String userId = SecurityUtils.getUserId(); // 登录用户名
        JSONObject params = new JSONObject();
        params.put("user_id", userId);
        return ApiRunner.safeRun("user_getUserInfo", params, false);
    }

    @PostMapping("/refresh")
    public ApiResult<LoginResult> refresh(@RequestBody JSONObject jo) {
        String refreshToken = jo.getString("refreshToken");
        LoginResult result = authService.refresh(refreshToken);
        return ApiResult.success(result);
    }

    private String extractClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty()) {
            // X-Forwarded-For 可能有多个 IP，取第一个为真实客户端
            return xfHeader.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) return realIp;
        return request.getRemoteAddr();
    }

}
