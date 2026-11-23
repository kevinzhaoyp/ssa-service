package com.ssa.plugin.pushWay.wechat;

import com.ssa.plugin.pushWay.config.MpPushConfig;
import com.ssa.plugin.pushWay.config.PushRegistry;
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
 * 服务号模板消息发送（POST /cgi-bin/message/template/send）。
 *
 * 模板 ID 与字段映射来自 push_template 表（channel='mp'，PushRegistry 60s 缓存）。
 * 前提：用户已关注服务号且 app_user.mp_open_id 有值（靠 /wx/mp/event + UnionID 绑定回填）。
 * 合规：只允许发「用户触发后的即时服务结果」，上课提醒等到期提醒类不得走此通道。
 */
@Slf4j
@Service
public class WxMpSender {

    private static final String SEND_URL =
            "https://api.weixin.qq.com/cgi-bin/message/template/send?access_token=";

    private static final Set<Integer> TOKEN_INVALID = Set.of(40001, 42001, 40014);

    @Resource
    private MpPushConfig config;
    @Resource
    private PushRegistry registry;
    @Resource
    private WxTokenProvider tokenProvider;

    public Map<String, Object> send(String mpOpenId, String templateKey, String page, Map<String, Object> fields) {
        if (isBlank(mpOpenId)) {
            return PushResult.fail("mp", -1, "no mp_openid", false, true);
        }
        PushRegistry.Tpl tpl = registry.template("mp", templateKey);
        if (tpl == null || isBlank(tpl.getId())) {
            return PushResult.fail("mp", -2, "template not configured: " + templateKey, false, true);
        }
        if (tpl.getFields().isEmpty()) {
            return PushResult.fail("mp", -2, "template fields_map missing: " + templateKey, false, true);
        }

        Map<String, Object> data = WxFields.buildData(tpl.getFields(), fields == null ? new HashMap<>() : fields);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("touser", mpOpenId);
        body.put("template_id", tpl.getId());
        if (!isBlank(config.getFallbackUrl())) {
            body.put("url", config.getFallbackUrl());
        }
        if (!isBlank(page) && !isBlank(config.getMiniprogramAppid())) {
            Map<String, Object> mini = new LinkedHashMap<>();
            mini.put("appid", config.getMiniprogramAppid());
            mini.put("pagepath", page);
            body.put("miniprogram", mini);
        }
        body.put("data", data);

        String token = tokenProvider.token(WxTokenProvider.KIND_MP);
        if (token == null) {
            return PushResult.fail("mp", -3, "access_token unavailable", true, true);
        }

        JsonNode resp = PushHttp.postJson(SEND_URL + token, body, timeout());
        if (resp != null && TOKEN_INVALID.contains(resp.path("errcode").asInt())) {
            tokenProvider.invalidate(WxTokenProvider.KIND_MP);
            String fresh = tokenProvider.token(WxTokenProvider.KIND_MP);
            if (fresh != null) {
                resp = PushHttp.postJson(SEND_URL + fresh, body, timeout());
            }
        }
        return mapResult(resp);
    }

    private Map<String, Object> mapResult(JsonNode resp) {
        if (resp == null) {
            return PushResult.fail("mp", -4, "http error", true, true);
        }
        int code = resp.path("errcode").asInt();
        String msg = resp.path("errmsg").asText(null);
        String respJson = resp.toString();
        switch (code) {
            case 0:
                return PushResult.ok("mp", respJson);
            case 43004: {
                // 用户未关注服务号：清空绑定由业务侧决定，这里只给信号
                Map<String, Object> r = PushResult.fail("mp", code, "用户未关注服务号", false, true, respJson);
                r.put("unbindMp", true);
                return r;
            }
            case 43101:
                return PushResult.fail("mp", code, "用户拒收消息", false, true, respJson);
            case 47003:
                return PushResult.fail("mp", code, "模板参数不合法: " + msg, false, false, respJson);
            case 45009:
                return PushResult.fail("mp", code, "接口调用超限: " + msg, false, false, respJson);
            case 40164:
                return PushResult.fail("mp", code, "IP 未加白名单: " + msg, false, false, respJson);
            default:
                log.warn("服务号模板消息发送失败 errcode={} errmsg={}", code, msg);
                return PushResult.fail("mp", code, msg, true, true, respJson);
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
