package com.ssa.plugin.pushWay.config;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 推送注册表 —— push_config（凭证/参数）+ push_template（模板）两表驱动（2026-11-23 起）。
 *
 * 三个渠道（wechat 订阅消息 / mp 服务号模板消息 / sms 短信）的凭证、模板 ID 与字段映射
 * 全部从库里读，加新类型/换密钥只改表，不再改代码打 jar。
 *
 * 缓存：全量加载 + 60 秒 TTL，改库最迟 1 分钟生效；换 appid/secret 需调 pushWay.reload
 * （同时失效微信 access_token）。查库失败保留旧缓存、绝不抛出（发送链路只降级、不中断）。
 *
 * push_template.fields_map 约定（JSONB）：
 *   wechat/mp：{"cardName":"thing10","amount":"short_thing11"}  业务字段 → 关键字编号
 *   sms      ：["cord","num"]                                   参数顺序，对应报备模板 ${1}${2}
 */
@Slf4j
@Component
public class PushRegistry {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** 缓存 TTL（毫秒） */
    private static final long CACHE_MS = 60_000L;

    /** push_template 单行 */
    public static class Tpl {
        private final String key;
        /** 微信模板 ID / 阿里云模板 CODE；空 = 未申请，调用方按「未配置」降级 */
        private final String id;
        /** wechat/mp：业务字段 → 关键字编号；sms 渠道为空 Map */
        private final Map<String, String> fields;
        /** sms：参数顺序（对应模板 ${1}${2}...）；其它渠道为空 List */
        private final List<String> params;

        Tpl(String key, String id, Map<String, String> fields, List<String> params) {
            this.key = key;
            this.id = id;
            this.fields = fields;
            this.params = params;
        }

        public String getKey() { return key; }

        public String getId() { return id; }

        public Map<String, String> getFields() { return fields; }

        public List<String> getParams() { return params; }
    }

    /** channel -> (code -> value)；volatile 整体替换，读不加锁 */
    private volatile Map<String, Map<String, String>> accounts;
    /** channel -> (key -> tpl) */
    private volatile Map<String, Map<String, Tpl>> templates;
    private volatile long loadedAt = 0;

    /** 取渠道凭证/参数；表里没有返回 null（调用方按未配置降级）。只读库，无环境变量兜底 */
    public String account(String channel, String code) {
        ensure();
        Map<String, String> m = accounts == null ? null : accounts.get(channel);
        return m == null ? null : m.get(code);
    }

    /** 取模板；表里没有/未加载成功返回 null（调用方按「未配置」降级） */
    public Tpl template(String channel, String key) {
        ensure();
        Map<String, Tpl> m = templates == null ? null : templates.get(channel);
        return m == null ? null : m.get(key);
    }

    /** 全部凭证（pushWay.status 自检用）：channel -> (code -> value) */
    public Map<String, Map<String, String>> accounts() {
        ensure();
        return accounts;
    }

    /** 全部模板（pushWay.status 自检用）：channel -> (key -> tpl) */
    public Map<String, Map<String, Tpl>> templates() {
        ensure();
        return templates;
    }

    /** 手动重载（pushWay.reload 用）：置空时间戳，下次读取重新查库 */
    public synchronized void reload() {
        loadedAt = 0;
    }

    private void ensure() {
        if (accounts == null || System.currentTimeMillis() - loadedAt >= CACHE_MS) {
            refresh();
        }
    }

    private synchronized void refresh() {
        // 双检：多个线程同时进 ensure 后只有第一个真正查库
        if (accounts != null && System.currentTimeMillis() - loadedAt < CACHE_MS) {
            return;
        }
        try {
            Map<String, Map<String, String>> acc = new HashMap<>();
            for (Record r : Db.use().find("SELECT channel, code, value FROM push_config")) {
                acc.computeIfAbsent(r.getStr("channel"), k -> new HashMap<>())
                        .put(r.getStr("code"), r.getStr("value"));
            }
            Map<String, Map<String, Tpl>> tpls = new HashMap<>();
            for (Record r : Db.use().find(
                    "SELECT channel, tkey, template_id, fields_map::text AS fields_map " +
                    "  FROM push_template WHERE enabled = 1")) {
                Tpl t = parseTpl(r.getStr("tkey"), r.getStr("template_id"), r.getStr("fields_map"));
                tpls.computeIfAbsent(r.getStr("channel"), k -> new LinkedHashMap<>()).put(t.getKey(), t);
            }
            accounts = acc;
            templates = tpls;
            loadedAt = System.currentTimeMillis();
            log.info("PushRegistry 加载完成：push_config {} 项 / push_template {} 项",
                    acc.values().stream().mapToInt(Map::size).sum(),
                    tpls.values().stream().mapToInt(Map::size).sum());
        } catch (Exception e) {
            // 查库失败：保留旧缓存（可能为 null → 调用方按未配置降级），不抛出影响发送链路
            log.warn("PushRegistry 加载 push_config/push_template 失败（保留旧缓存）: {}", e.getMessage());
        }
    }

    /** fields_map 兼容两种形态：对象（wechat/mp 映射）/ 数组（sms 参数顺序） */
    private Tpl parseTpl(String key, String id, String fieldsMapJson) {
        Map<String, String> fields = new LinkedHashMap<>();
        List<String> params = new ArrayList<>();
        if (fieldsMapJson != null && !fieldsMapJson.isBlank()) {
            try {
                Map<String, String> m = MAPPER.readValue(fieldsMapJson,
                        new TypeReference<LinkedHashMap<String, String>>() {});
                fields.putAll(m);
            } catch (Exception tryMap) {
                try {
                    List<String> l = MAPPER.readValue(fieldsMapJson,
                            new TypeReference<ArrayList<String>>() {});
                    params.addAll(l);
                } catch (Exception e) {
                    log.warn("push_template.fields_map 解析失败 tkey={}: {}", key, e.getMessage());
                }
            }
        }
        return new Tpl(key, id == null ? "" : id, fields, params);
    }

    /** 字符串转 int，空/非法回退默认值 */
    public static Integer intOf(String v, int def) {
        try {
            return v == null || v.trim().isEmpty() ? def : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
