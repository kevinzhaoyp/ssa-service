package com.ssa.plugin.pushWay.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信模板字段格式化。
 *
 * 微信模板关键字有类型和长度约束，超限返回 47003，所以发送前必须按类型处理：
 *   thing  ≤ 20 字（中文按字符算）
 *   phrase ≤ 5 个汉字
 *   time   只接受单点时间 YYYY-MM-DD HH:mm
 *   date   YYYY-MM-DD
 *   number 数字
 *   letter 字母
 *
 * 业务侧 fields 是原始值（如 dateTime = "2026-11-20 09:00-10:00" 区间格式），
 * 这里负责转成微信能接受的样子，业务侧不需要关心模板约束。
 */
public final class WxFields {

    private WxFields() {
    }

    /**
     * 按模板字段映射生成微信 data 节点。
     *
     * @param keyMapping 业务字段名 → 微信关键字（如 title → thing1）
     * @param fields     业务字段原始值
     * @return {"thing1":{"value":"..."}, ...}
     */
    public static Map<String, Object> buildData(Map<String, String> keyMapping, Map<String, Object> fields) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (keyMapping == null || keyMapping.isEmpty() || fields == null) {
            return data;
        }
        for (Map.Entry<String, String> e : keyMapping.entrySet()) {
            String bizKey = e.getKey();
            String wxKey = e.getValue();
            if (wxKey == null || wxKey.isEmpty()) {
                continue;
            }
            Object raw = fields.get(bizKey);
            String value = format(wxKey, raw);
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("value", value);
            data.put(wxKey, node);
        }
        return data;
    }

    /** 按关键字类型做格式化与截断 */
    public static String format(String wxKey, Object raw) {
        String v = raw == null ? "" : String.valueOf(raw);
        String type = wxKey.replaceAll("[0-9]+$", "").toLowerCase();
        switch (type) {
            case "thing":
            case "short_thing":
                return cut(v, 20);
            case "phrase":
                return cut(v, 5);
            case "time":
                return normalizeTime(v);
            case "date":
                return v.length() >= 10 && v.charAt(4) == '-' ? v.substring(0, 10) : cut(v, 10);
            case "number":
                return v.replaceAll("[^0-9.\\-]", "");
            case "letter":
                return v.replaceAll("[^A-Za-z]", "");
            default:
                // 未知类型按 thing 处理，宁可截断也不要 47003
                return cut(v, 20);
        }
    }

    /**
     * time 类型只接受单点时间。
     * 业务侧给的是区间（2026-11-20 09:00-10:00），取前 16 位（2026-11-20 09:00）。
     */
    public static String normalizeTime(String v) {
        String s = v == null ? "" : v.trim();
        if (s.length() >= 16 && s.charAt(4) == '-' && s.charAt(7) == '-') {
            return s.substring(0, 16);
        }
        return s;
    }

    /** 按字符数（code point）截断，避免把 emoji 截成半个 */
    public static String cut(String v, int max) {
        if (v == null) {
            return "";
        }
        if (v.codePointCount(0, v.length()) <= max) {
            return v;
        }
        int end = v.offsetByCodePoints(0, max);
        return v.substring(0, end);
    }
}
