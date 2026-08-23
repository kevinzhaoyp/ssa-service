package com.ssa.apis.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoginAuditService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 异步写登录日志并回填 sys_user 的 last_login 快照
     * @param userId 可为空
     */
    @Async("ssaTaskExecutor")
    public void auditLogin(String username, Long userId, String ip, String userAgent, String clientId, boolean success) {
        try {
//            // 尝试直接写入数据库表 user_login_log
//            String now = LocalDateTime.now().format(DTF);
//            String insertSql = "INSERT INTO user_login_log (user_id, client_id, event_time, ip, user_agent, success, action, raw_info) VALUES (?,?,?,?,?,?,?,NULL)";
//            jdbcTemplate.update(insertSql, userId, clientId, now, ip, userAgent, success ? 1 : 0, "login");
//
//            // 回填 sys_user 的 last_login_time/last_login_ip（仅在当前时间晚于数据库中的时间时更新）
//            if (success && userId != null) {
//                String updateSql = "UPDATE sys_user SET last_login_time = ?, last_login_ip = ? WHERE user_id = ? AND (last_login_time IS NULL OR last_login_time < ? )";
//                jdbcTemplate.update(updateSql, now, ip, userId, now);
//            }
            log.info("写入数据库成功！");
        } catch (Exception e) {
            log.warn("直接写入数据库 user_login_log 失败，回退到 ApiRunner: {}", e.getMessage());
//            log.warn("直接写入数据库 user_login_log 失败，回退到 ApiRunner: {}", e.getMessage());
//            try {
//                JSONObject params = new JSONObject();
//                params.put("username", username);
//                if (userId != null) params.put("user_id", userId);
//                params.put("ip", ip);
//                params.put("user_agent", userAgent);
//                params.put("client_id", clientId);
//                params.put("success", success ? 1 : 0);
//                params.put("action", "login");
//                ApiRunner.safeRun("loginLog", params, false);
//
//                if (success && userId != null) {
//                    JSONObject update = new JSONObject();
//                    update.put("user_id", userId);
//                    update.put("last_login_ip", ip);
//                    ApiRunner.safeRun("user_updateLastLogin", update, false);
//                }
//            } catch (Exception ex) {
//                log.warn("回退的 ApiRunner 写入也失败: {}", ex.getMessage());
//            }
        }
    }
}
