package com.ssa.oauth.service;

import cn.hutool.core.util.RandomUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 短信验证码服务
 * 
 * @author ssa
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmsCodeService {

    private final StringRedisTemplate redisTemplate;

    /**
     * 生成并发送短信验证码
     */
    public boolean sendSmsCode(String phone) {
        try {
            // 生成6位随机验证码
            String code = RandomUtil.randomNumbers(6);
            log.info("为手机号 {} 生成验证码: {}", phone, code);

            // 将验证码存储到Redis，有效期5分钟
            String key = "sms_code:" + phone;
            redisTemplate.opsForValue().set(key, code, 5, TimeUnit.MINUTES);

            // 这里应该调用短信服务商API发送验证码
            // 模拟发送过程
            log.info("短信验证码已发送到: {}", phone);

            return true;
        } catch (Exception e) {
            log.error("发送短信验证码失败", e);
            return false;
        }
    }

    /**
     * 验证短信验证码
     */
    public boolean validateSmsCode(String phone, String code) {
        try {
            String key = "sms_code:" + phone;
            String storedCode = redisTemplate.opsForValue().get(key);

            if (storedCode != null && storedCode.equals(code)) {
                // 验证成功后删除验证码
                redisTemplate.delete(key);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("验证短信验证码失败", e);
            return false;
        }
    }

    /**
     * 检查手机号是否已被使用
     */
    public boolean isPhoneExists(String phone) {
        // 这里应该查询数据库检查手机号是否已被注册
        // 暂时返回false表示未被使用
        return false;
    }
}