package com.ssa.plugin.pushWay.sms;
import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.ssa.plugin.annotation.SsaApiClass;
import com.ssa.plugin.annotation.SsaApiMethod;
import com.ssa.core.utils.ApiResult;
import com.ssa.plugin.pushWay.config.SmsConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

@Slf4j
@SsaApiClass("smsSender")
@Service
public class AliyunSmsSender {
    @Resource
    private SmsConfig smsConfig;
    @SsaApiMethod
    public Object send(String phoneNumber, String safeCode) throws Exception {
        Config config = new Config()
                .setAccessKeyId(smsConfig.getAccessKeyId())
                .setAccessKeySecret(smsConfig.getAccessKeySecret());
        // 访问的域名
        config.endpoint = "dysmsapi.aliyuncs.com";
        Client client = new Client(config);

        SendSmsRequest sendSmsRequest = new SendSmsRequest()
                .setPhoneNumbers(phoneNumber)
                .setSignName(smsConfig.getSignName())
                .setTemplateCode(smsConfig.getTemplateCode())
                .setTemplateParam("{\"code\":\"" + safeCode + "\"}");

        SendSmsResponse sendSmsResponse = client.sendSms(sendSmsRequest);
        if (!"OK".equals(sendSmsResponse.getBody().getCode())) {
            log.error("短信发送失败: {}", sendSmsResponse.getBody().getMessage());
            return ApiResult.failed(sendSmsResponse.getBody().getMessage());
        } else {
            log.info("{}短信发送成功", phoneNumber);
        }
        return ApiResult.success(null);
    }
}
