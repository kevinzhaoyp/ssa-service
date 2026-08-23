package com.ssa.apis.auth;

import com.ssa.apis.wechat.WechatQrService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthTestController {

    private final WechatQrService qrService;

    public AuthTestController(WechatQrService qrService) {
        this.qrService = qrService;
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if ("SSA_TEST_AUTH".equals(c.getName())) {
                    String ticket = c.getValue();
                    String username = qrService.getTestUserByTicket(ticket);
                    if (username != null) {
                        Map<String, Object> body = new HashMap<>();
                        body.put("username", username);
                        body.put("ticket", ticket);
                        return ResponseEntity.ok(body);
                    }
                }
            }
        }
        return ResponseEntity.status(401).body(Map.of("error", "unauthenticated"));
    }
}

