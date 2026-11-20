package com.ssa.plugin.pushWay.sms;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.ssa.core.utils.ApiResult;
import com.ssa.plugin.annotation.SsaApiClass;
import com.ssa.plugin.annotation.SsaApiMethod;
import com.ssa.plugin.pushWay.config.SmsConfig;
import com.ssa.plugin.pushWay.config.SmsPushConfig;
import com.ssa.plugin.pushWay.model.PushResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 阿里云短信发送。
 *
 * 两种用法：
 *  1) send(phone, safeCode)              —— 旧接口，验证码模板（沿用 aliyun.sms.templateCode），保留兼容
 *  2) sendTemplate(phone, key, fields)   —— 通用模板发送，模板键对应 push.sms.templates.<key>
 *
 * 短信模板必须先报备通过；报备模板里的变量顺序由 push.sms.templates.<key>.params 指定。
 */
@Slf4j
@SsaApiClass("smsSender")
@Service
public class AliyunSmsSender {

    @Resource
    private SmsConfig smsConfig;
    @Resource
    private SmsPushConfig smsPushConfig;

    /** 旧接口：验证码短信（保持原签名与返回，避免破坏已有调用方） */
    @SsaApiMethod
    public Object send(String phoneNumber, String safeCode) throws Exception {
        SendSmsResponse resp = doSend(phoneNumber, smsConfig.getTemplateCode(),
                "{\"code\":\"" + safeCode + "\"}");
        if (resp == null) {
            return ApiResult.failed("短信服务异常");
        }
        if (!"OK".equals(resp.getBody().getCode())) {
            log.error("短信发送失败: {} - {}", resp.getBody().getCode(), resp.getBody().getMessage());
            return ApiResult.failed(resp.getBody().getMessage());
        }
        log.info("{} 短信发送成功", phoneNumber);
        return ApiResult.success(null);
    }

    /**
     * 通用模板发送（供通知 worker 调用）。
     *
     * @param phone       手机号（app_user.phone）
     * @param templateKey 模板键（push.sms.templates 下的 key）
     * @param fields      业务字段值，按模板 params 顺序取
     */
    public Map<String, Object> sendTemplate(String phone, String templateKey, Map<String, Object> fields) {
        if (isBlank(phone)) {
            return PushResult.fail("sms", -1, "no phone", false, false);
        }
        SmsPushConfig.Template tpl = smsPushConfig.getTemplates() == null
                ? null : smsPushConfig.getTemplates().get(templateKey);
        if (tpl == null || isBlank(tpl.getCode())) {
            // 短信是最后一道兜底，模板没报备就直接终态，不再降级
            return PushResult.fail("sms", -2, "sms template not configured: " + templateKey, false, false);
        }

        String paramJson = buildParams(tpl.getParams(), fields);
        SendSmsResponse resp;
        try {
            resp = doSend(phone, tpl.getCode(), paramJson);
        } catch (Exception e) {
            log.error("短信发送异常 phone={} key={}: {}", mask(phone), templateKey, e.getMessage());
            return PushResult.fail("sms", -4, "sms client error: " + e.getMessage(), true, false);
        }
        if (resp == null) {
            return PushResult.fail("sms", -4, "sms client null response", true, false);
        }
        String code = resp.getBody().getCode();
        String msg = resp.getBody().getMessage();
        if ("OK".equals(code)) {
            log.info("短信发送成功 phone={} key={}", mask(phone), templateKey);
            return PushResult.ok("sms", resp.getBody().getBizId());
        }
        log.warn("短信发送失败 phone={} key={} code={} msg={}", mask(phone), templateKey, code, msg);
        return mapError(code, msg);
    }

    /** 阿里云错误码 → 统一语义 */
    private Map<String, Object> mapError(String code, String msg) {
        String c = code == null ? "" : code;
        if (c.contains("BUSINESS_LIMIT_CONTROL")) {
            // 触发频控：短时间重试无效，交给 worker 按退避处理
            return PushResult.fail("sms", -10, "短信频控: " + msg, true, false);
        }
        if (c.contains("MOBILE_NUMBER_ILLEGAL") || c.contains("MOBILE_NUMBER_NOT_EXIST")) {
            return PushResult.fail("sms", -11, "手机号无效: " + msg, false, false);
        }
        if (c.contains("AMOUNT_NOT_ENOUGH") || c.contains("OUT_OF_SERVICE")
                || c.contains("TEMPLATE_ILLEGAL") || c.contains("SIGNATURE_ILLEGAL")
                || c.contains("TEMPLATE_NOT_EXIST")) {
            return PushResult.fail("sms", -12, "短信配置问题: " + msg, false, false);
        }
        return PushResult.fail("sms", -13, c + " " + msg, true, false);
    }

    private SendSmsResponse doSend(String phone, String templateCode, String templateParam) throws Exception {
        Config config = new Config()
                .setAccessKeyId(smsConfig.getAccessKeyId())
                .setAccessKeySecret(smsConfig.getAccessKeySecret());
        config.endpoint = "dysmsapi.aliyuncs.com";
        Client client = new Client(config);
        SendSmsRequest req = new SendSmsRequest()
                .setPhoneNumbers(phone)
                .setSignName(smsConfig.getSignName())
                .setTemplateCode(templateCode)
                .setTemplateParam(templateParam);
        return client.sendSms(req);
    }

    /** 按报备模板的变量顺序拼 JSON，例如 {"name":"轮滑基础","time":"2026-11-20 09:00"} */
    private String buildParams(List<String> names, Map<String, Object> fields) {
        StringBuilder sb = new StringBuilder("{");
        if (names != null) {
            for (int i = 0; i < names.size(); i++) {
                String name = names.get(i);
                Object v = fields == null ? null : fields.get(name);
                if (i > 0) {
                    sb.append(',');
                }
                sb.append('"').append(escape(name)).append("\":\"")
                        .append(escape(v == null ? "" : String.valueOf(v))).append('"');
            }
        }
        return sb.append('}').toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String mask(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
