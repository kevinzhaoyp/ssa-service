package com.ssa.apis.wechat;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * A lightweight controller exposing endpoints used by frontend QR login demo.
 * - GET /wechat/qr -> { ticket, qrUrl }
 * - GET /wechat/qr/status?ticket=... -> { logged: boolean }
 * - GET /wechat/qr/simulate?ticket=... -> simulate scanning (sets logged=true)
 * - GET /wechat/qr/simulateLogin?ticket=... -> simulate scanning and set test auth cookie
 *
 * Note: qrUrl here is a placeholder. In a production integration you will
 * generate a real WeChat QR connect URL that encodes a callback including the ticket
 * (or map the ticket to session state server-side).
 */
@RestController
@RequestMapping("/wechat")
public class WechatController {

    private final WechatQrService qrService;

    public WechatController(WechatQrService qrService) {
        this.qrService = qrService;
    }

    @GetMapping("/qr")
    public ResponseEntity<?> createQr() {
        String ticket = qrService.createTicket();
        // For demo, the qrUrl is a simple deep link that would be scanned by the mobile
        // In reality you should produce an actual WeChat QR connect URL or a page that triggers WeChat OAuth
        String qrUrl = "weixin://scan?ticket=" + ticket;
        Map<String, Object> body = new HashMap<>();
        body.put("ticket", ticket);
        body.put("qrUrl", qrUrl);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/qr/status")
    public ResponseEntity<?> status(@RequestParam String ticket) {
        boolean logged = qrService.isLogged(ticket);
        Map<String, Object> body = new HashMap<>();
        body.put("logged", logged);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/qr/simulate")
    public ResponseEntity<?> simulateScan(@RequestParam String ticket) {
        boolean ok = qrService.markLogged(ticket);
        Map<String, Object> body = new HashMap<>();
        body.put("ticket", ticket);
        body.put("marked", ok);
        body.put("logged", qrService.isLogged(ticket));
        return ResponseEntity.ok(body);
    }

    @GetMapping("/qr/simulateLogin")
    public ResponseEntity<?> simulateLogin(@RequestParam String ticket, HttpServletResponse response) {
        // Create a test user session and set an httpOnly cookie for demo
        String testUsername = "testuser";
        qrService.createTestSession(ticket, testUsername);

        Cookie cookie = new Cookie("SSA_TEST_AUTH", ticket);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60); // 1 hour
        response.addCookie(cookie);

        Map<String, Object> body = new HashMap<>();
        body.put("ticket", ticket);
        body.put("marked", true);
        body.put("logged", qrService.isLogged(ticket));
        body.put("user", testUsername);
        return ResponseEntity.ok(body);
    }
}
