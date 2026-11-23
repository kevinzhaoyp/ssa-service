package com.ssa.plugin.pushWay.core;

import com.ssa.plugin.annotation.SsaApiClass;
import com.ssa.plugin.annotation.SsaApiMethod;
import com.ssa.plugin.pushWay.config.MpPushConfig;
import com.ssa.plugin.pushWay.config.PushRegistry;
import com.ssa.plugin.pushWay.config.SmsConfig;
import com.ssa.plugin.pushWay.config.WechatPushConfig;
import com.ssa.plugin.pushWay.model.PushResult;
import com.ssa.plugin.pushWay.sms.AliyunSmsSender;
import com.ssa.plugin.pushWay.wechat.WxMpSender;
import com.ssa.plugin.pushWay.wechat.WxSubscribeSender;
import com.ssa.plugin.pushWay.wechat.WxTokenProvider;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 推送门面（只负责对接，不含任何业务判断）。
 *
 * 其它插件（如 cjyk-task）通过 ApiRunner 调用：
 *   ApiRunner.safeRun("pushWay.send", map, false)
 * map 键：channel / target / templateKey / page / fields
 *
 * 返回 Map（跨插件类加载器安全）：
 *   ok        是否发送成功
 *   channel   渠道
 *   errCode   错误码（0 = 成功）
 *   errMsg    错误信息
 *   retryable 是否值得重试
 *   fallback  是否允许降级到下一个渠道
 *   unbindMp  服务号 43004 时为 true（业务侧据此清空 mp_open_id）
 *   resp      渠道原始响应
 */
@Slf4j
@SsaApiClass(value = "pushWay", describe = "推送对接：小程序订阅消息 / 服务号模板消息 / 短信")
@Service
public class PushService {
    @Resource
    private WxSubscribeSender wxSubscribeSender;
    @Resource
    private WxMpSender wxMpSender;
    @Resource
    private AliyunSmsSender aliyunSmsSender;
    @Resource
    private WechatPushConfig wechatPushConfig;
    @Resource
    private MpPushConfig mpPushConfig;
    @Resource
    private SmsConfig smsConfig;
    @Resource
    private PushRegistry pushRegistry;
    @Resource
    private WxTokenProvider wxTokenProvider;

    /**
     * 统一发送入口。
     *
     * @param channel     wechat | mp | sms
     * @param target      微信 openid / 服务号 openid / 手机号
     * @param templateKey 模板键（detail.key，与 push_template.tkey 对齐）
     * @param page        点击跳转页面（短信忽略）
     * @param fields      业务字段原始值（detail.fields）
     */
    @SsaApiMethod(name = "send", describe = "按渠道发送一条推送（wechat/mp/sms）")
    public Map<String, Object> send(String channel, String target, String templateKey,
                                    String page, Map<String, Object> fields) {
        if (channel == null || channel.trim().isEmpty()) {
            return PushResult.fail("", -99, "channel is required", false, false);
        }
        try {
            switch (channel.trim().toLowerCase()) {
                case "wechat":
                    return wxSubscribeSender.send(target, templateKey, page, fields);
                case "mp":
                    return wxMpSender.send(target, templateKey, page, fields);
                case "sms":
                    return aliyunSmsSender.sendTemplate(target, templateKey, fields);
                default:
                    return PushResult.fail(channel, -98, "unknown channel: " + channel, false, false);
            }
        } catch (Exception e) {
            log.error("推送发送异常 channel={} templateKey={}: {}", channel, templateKey, e.getMessage(), e);
            return PushResult.fail(channel, -97, "push error: " + e.getMessage(), true, true);
        }
    }

    /** 配置自检：看各渠道/模板是否就绪，联调时用（敏感项只报 ready/missing，不回显值） */
    @SsaApiMethod(name = "status", describe = "推送配置自检（各渠道凭证与模板是否就绪）")
    public Map<String, Object> status() {
        Map<String, Map<String, PushRegistry.Tpl>> tpls = pushRegistry.templates();

        Map<String, Object> wechat = new LinkedHashMap<>();
        wechat.put("appid", wechatPushConfig.getAppid());
        fillTemplates(wechat, tpls, "wechat");

        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put("appid", mpPushConfig.getAppid());
        fillTemplates(mp, tpls, "mp");

        Map<String, Object> sms = new LinkedHashMap<>();
        sms.put("sign_name", smsConfig.getSignName());
        sms.put("access_key_id", mask(smsConfig.getAccessKeyId()));
        fillTemplates(sms, tpls, "sms");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("wechat", wechat);
        result.put("mp", mp);
        result.put("sms", sms);
        return result;
    }

    /** 重载注册表并失效微信 access_token：改 push_config/push_template 后调用，即时生效 */
    @SsaApiMethod(name = "reload", describe = "重载 push_config/push_template（改凭证或模板后调用）")
    public Map<String, Object> reload() {
        pushRegistry.reload();
        wxTokenProvider.invalidate(WxTokenProvider.KIND_WECHAT);
        wxTokenProvider.invalidate(WxTokenProvider.KIND_MP);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        return r;
    }

    /** 把某渠道的模板就绪状态写进自检结果 */
    private void fillTemplates(Map<String, Object> into, Map<String, Map<String, PushRegistry.Tpl>> tpls,
                               String channel) {
        Map<String, PushRegistry.Tpl> m = tpls == null ? null : tpls.get(channel);
        if (m == null || m.isEmpty()) {
            into.put("templateCount", 0);
            into.put("note", "registry not loaded or channel empty");
            return;
        }
        into.put("templateCount", m.size());
        m.forEach((k, t) -> into.put(k, t.getId() == null || t.getId().isEmpty() ? "missing" : "ready"));
    }

    /** 敏感项打码：只露前 4 位 */
    private String mask(String v) {
        if (v == null || v.length() <= 4) {
            return "***";
        }
        return v.substring(0, 4) + "***";
    }
}
