package com.ssa.oauth.controller;

import com.ssa.oauth.service.SmsCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 短信验证码控制器
 * 
 * @author ssa
 */
@RestController
@RequestMapping("/auth/v1/sms")
@RequiredArgsConstructor
public class SmsCodeController {

    private final SmsCodeService smsCodeService;

    /**
     * 发送短信验证码
     */
    @PostMapping("/send")
    public ResponseEntity<Boolean> sendSmsCode(@RequestParam String phone) {
        boolean result = smsCodeService.sendSmsCode(phone);
        return ResponseEntity.ok(result);
    }

    /**
     * 验证短信验证码
     */
    @PostMapping("/verify")
    public ResponseEntity<Boolean> verifySmsCode(@RequestParam String phone, @RequestParam String code) {
        boolean isValid = smsCodeService.validateSmsCode(phone, code);
        return ResponseEntity.ok(isValid);
    }
}