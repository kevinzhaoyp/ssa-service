package com.ssa.plugin.pushWay.core;

import com.ssa.plugin.annotation.SsaApiClass;
import com.ssa.plugin.annotation.SsaApiMethod;
import com.ssa.plugin.pushWay.config.MpPushConfig;
import com.ssa.plugin.pushWay.config.SmsPushConfig;
import com.ssa.plugin.pushWay.config.WechatPushConfig;
import com.ssa.plugin.pushWay.model.PushResult;
import com.ssa.plugin.pushWay.sms.AliyunSmsSender;
import com.ssa.plugin.pushWay.wechat.WxMpSender;
import com.ssa.plugin.pushWay.wechat.WxSubscribeSender;
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

    public static final String CHANNEL_WECHAT = "wechat";
    public static final String CHANNEL_MP = "mp";
    public static final String CHANNEL_SMS = "sms";

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
    private SmsPushConfig smsPushConfig;

    /**
     * 统一发送入口。
     *
     * @param channel     wechat | mp | sms
     * @param target      微信 openid / 服务号 openid / 手机号
     * @param templateKey 模板键（detail.key；短信为 push.sms.templates 的键）
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
                case CHANNEL_WECHAT:
                    return wxSubscribeSender.send(target, templateKey, page, fields);
                case CHANNEL_MP:
                    return wxMpSender.send(target, templateKey, page, fields);
                case CHANNEL_SMS:
                    return aliyunSmsSender.sendTemplate(target, templateKey, fields);
                default:
                    return PushResult.fail(channel, -98, "unknown channel: " + channel, false, false);
            }
        } catch (Exception e) {
            log.error("推送发送异常 channel={} templateKey={}: {}", channel, templateKey, e.getMessage(), e);
            return PushResult.fail(channel, -97, "push error: " + e.getMessage(), true, true);
        }
    }

    /** 配置自检：看各渠道/模板是否就绪，联调时用 */
    @SsaApiMethod(name = "status", describe = "推送配置自检（各渠道模板是否就绪）")
    public Map<String, Object> status() {
        Map<String, Object> wechat = new LinkedHashMap<>();
        wechat.put("appid", wechatPushConfig.getAppid());
        wechat.put("templateCount", wechatPushConfig.getTemplates() == null ? 0 : wechatPushConfig.getTemplates().size());
        if (wechatPushConfig.getTemplates() != null) {
            wechatPushConfig.getTemplates().forEach((k, v) ->
                    wechat.put(k, v.getId() == null || v.getId().isEmpty() ? "missing" : "ready"));
        }

        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put("appid", mpPushConfig.getAppid());
        mp.put("templateCount", mpPushConfig.getTemplates() == null ? 0 : mpPushConfig.getTemplates().size());
        if (mpPushConfig.getTemplates() != null) {
            mpPushConfig.getTemplates().forEach((k, v) ->
                    mp.put(k, v.getId() == null || v.getId().isEmpty() ? "missing" : "ready"));
        }

        Map<String, Object> sms = new LinkedHashMap<>();
        sms.put("templateCount", smsPushConfig.getTemplates() == null ? 0 : smsPushConfig.getTemplates().size());
        if (smsPushConfig.getTemplates() != null) {
            smsPushConfig.getTemplates().forEach((k, v) ->
                    sms.put(k, v.getCode() == null || v.getCode().isEmpty() ? "missing" : "ready"));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("wechat", wechat);
        result.put("mp", mp);
        result.put("sms", sms);
        return result;
    }
}
