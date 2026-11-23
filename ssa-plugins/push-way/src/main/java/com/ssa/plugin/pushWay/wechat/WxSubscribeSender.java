package com.ssa.plugin.pushWay.wechat;

import com.ssa.plugin.pushWay.config.PushRegistry;
import com.ssa.plugin.pushWay.config.WechatPushConfig;
import com.ssa.plugin.pushWay.model.PushResult;
import com.ssa.plugin.pushWay.util.PushHttp;
import com.ssa.plugin.pushWay.util.WxFields;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 小程序订阅消息发送（POST /cgi-bin/message/subscribe/send）。
 *
 * 只做「对接」：不查业务表、不判断发什么，入参就是一条待发消息。
 * 模板 ID 与字段映射来自 push_template 表（PushRegistry 60s 缓存），加类型不改代码。
 * 一次性订阅语义：用户授权一次只能发一条，额度用完返回 43101，属正常业务结果，不是故障。
 */
@Slf4j
@Service
public class WxSubscribeSender {

    private static final String SEND_URL =
            "https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=";

    private static final Set<Integer> TOKEN_INVALID = Set.of(40001, 42001, 40014);

    @Resource
    private WechatPushConfig config;
    @Resource
    private PushRegistry registry;
    @Resource
    private WxTokenProvider tokenProvider;

    /**
     * @param openId      小程序 openid（app_user.open_id）
     * @param templateKey 模板键（notify_snd.detail.key）
     * @param page        点击跳转页面（可空）
     * @param fields      业务字段原始值（notify_snd.detail.fields）
     */
    public Map<String, Object> send(String openId, String templateKey, String page, Map<String, Object> fields) {
        if (isBlank(openId)) {
            return PushResult.fail("wechat", -1, "no openid", false, true);
        }
        PushRegistry.Tpl tpl = registry.template("wechat", templateKey);
        if (tpl == null || isBlank(tpl.getId())) {
            // 模板没申请/没配置（push_template 无此行或 id 空）：不算故障，降级到别的渠道
            return PushResult.fail("wechat", -2, "template not configured: " + templateKey, false, true);
        }
        if (tpl.getFields().isEmpty()) {
            // fields_map 缺失会是 47003，直接按未配置降级并给出明确原因
            return PushResult.fail("wechat", -2, "template fields_map missing: " + templateKey, false, true);
        }

        Map<String, Object> data = WxFields.buildData(tpl.getFields(), fields == null ? new HashMap<>() : fields);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("touser", openId);
        body.put("template_id", tpl.getId());
        if (!isBlank(page)) {
            body.put("page", page);
        }
        body.put("miniprogram_state", isBlank(config.getMiniprogramState()) ? "formal" : config.getMiniprogramState());
        body.put("lang", "zh_CN");
        body.put("data", data);

        String token = tokenProvider.token(WxTokenProvider.KIND_WECHAT);
        if (token == null) {
            return PushResult.fail("wechat", -3, "access_token unavailable", true, true);
        }

        JsonNode resp = PushHttp.postJson(SEND_URL + token, body, timeout());
        // token 失效：刷新后重试一次
        if (resp != null && TOKEN_INVALID.contains(resp.path("errcode").asInt())) {
            tokenProvider.invalidate(WxTokenProvider.KIND_WECHAT);
            String fresh = tokenProvider.token(WxTokenProvider.KIND_WECHAT);
            if (fresh != null) {
                resp = PushHttp.postJson(SEND_URL + fresh, body, timeout());
            }
        }
        return mapResult(resp);
    }

    private Map<String, Object> mapResult(JsonNode resp) {
        if (resp == null) {
            return PushResult.fail("wechat", -4, "http error", true, true);
        }
        int code = resp.path("errcode").asInt();
        String msg = resp.path("errmsg").asText(null);
        String respJson = resp.toString();
        switch (code) {
            case 0:
                return PushResult.ok("wechat", respJson);
            case 43101:
                // 用户未授权 / 一次性额度已用完：一次性订阅的正常结果，别当故障
                return PushResult.fail("wechat", code, "用户未订阅或额度已用完", false, true, respJson);
            case 40003:
                return PushResult.fail("wechat", code, "openid 无效", false, true, respJson);
            case 47003:
                // 参数非法：代码问题，降级会掩盖 bug
                return PushResult.fail("wechat", code, "模板参数不合法: " + msg, false, false, respJson);
            case 45009:
                // 接口日调用量超限：重试无意义
                return PushResult.fail("wechat", code, "接口调用超限: " + msg, false, false, respJson);
            case 40164:
                return PushResult.fail("wechat", code, "IP 未加白名单: " + msg, false, false, respJson);
            default:
                log.warn("小程序订阅消息发送失败 errcode={} errmsg={}", code, msg);
                return PushResult.fail("wechat", code, msg, true, true, respJson);
        }
    }

    private int timeout() {
        Integer t = config.getTimeoutMs();
        return t == null || t <= 0 ? 5000 : t;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
