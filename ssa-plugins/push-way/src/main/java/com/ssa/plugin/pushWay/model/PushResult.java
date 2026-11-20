package com.ssa.plugin.pushWay.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 推送结果（对外统一用 Map 暴露，避免跨插件类加载器类型不兼容）。
 *
 * 关键语义：
 *  - retryable  ：失败是否值得重试（网络/限流/系统错误 = true；参数错误/配置缺失 = false）
 *  - fallback   ：失败后是否允许降级到下一个渠道（额度用尽/未订阅/模板缺失 = true；参数错误 = false）
 *  - unbindMp   ：服务号 43004（用户未关注）时置 true，由业务侧决定是否清空 mp_open_id
 */
public final class PushResult {

    private PushResult() {
    }

    public static Map<String, Object> ok(String channel, Object resp) {
        Map<String, Object> m = base(true, channel, 0, "ok");
        m.put("resp", resp);
        return m;
    }

    public static Map<String, Object> fail(String channel, int errCode, String errMsg,
                                           boolean retryable, boolean fallback) {
        return fail(channel, errCode, errMsg, retryable, fallback, null);
    }

    public static Map<String, Object> fail(String channel, int errCode, String errMsg,
                                           boolean retryable, boolean fallback, Object resp) {
        Map<String, Object> m = base(false, channel, errCode, errMsg);
        m.put("retryable", retryable);
        m.put("fallback", fallback);
        m.put("resp", resp);
        return m;
    }

    private static Map<String, Object> base(boolean ok, String channel, int errCode, String errMsg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", ok);
        m.put("channel", channel);
        m.put("errCode", errCode);
        m.put("errMsg", errMsg);
        m.put("retryable", false);
        m.put("fallback", false);
        m.put("unbindMp", false);
        return m;
    }
}
