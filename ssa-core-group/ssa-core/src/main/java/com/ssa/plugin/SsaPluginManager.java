package com.ssa.plugin;

import com.alibaba.fastjson.JSONArray;
import com.ssa.core.utils.SpringUtil;
import com.ssa.plugin.annotation.SsaApiClass;
import com.ssa.plugin.annotation.SsaApiMethod;
import com.ssa.plugin.config.SsaPluginProperties;
import com.ssa.plugin.methodApi.MethodApiManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

@Service
@Slf4j
@SsaApiClass("plugin")
public class SsaPluginManager {
    private MethodApiManager getMethodApiManager() {
        return SpringUtil.getBean(MethodApiManager.class);
    }

    /**
     * 已加载插件列表 如果要按顺序使用 TreeMap,如果要快使用HashMap
     */
    private final static Map<String, SsaPlugin> SSA_PLUGINS = new TreeMap<>();
    /**
     * 类对应的插件Key 类名，插件Key
     */
    public final static Map<String, String> classPluginKey = new HashMap<>();

    private final SsaPluginProperties properties;

    /**
     * 插件根目录
     */
    private static String pluginPath;

    public SsaPluginManager(SsaPluginProperties properties) {
        this.properties = properties;
    }

    public static String getPluginPath() {
        if (pluginPath == null || pluginPath.isEmpty()) {
            pluginPath = SpringUtil.getEnvironment().getProperty("ssa.plugin.pluginPath");
            if (pluginPath == null || pluginPath.isEmpty()) {
                pluginPath = SpringUtil.getJarHome() + "/plugins";
            }
        }
        return pluginPath;
    }

    /**
     * 从 JSON文件中解析出需要加载jar
     */
    public void loadPlugins() {
        if (properties == null || properties.getPlugins() == null) {
            log.info("没有配置 ssa 插件。");
            return;
        }
        if (properties.getPluginPath() != null && !properties.getPluginPath().isEmpty()) {
            pluginPath = properties.getPluginPath();
        }
        log.info("正在加载插件列表...路径:{}", pluginPath);
        for (SsaPluginProperties.Plugin pluginConfig : properties.getPlugins()) {
            SsaPlugin plugin = new SsaPlugin();
            plugin.setKey(pluginConfig.getKey());
            plugin.setFileName(pluginPath + pluginConfig.getFileName());
            plugin.setState(pluginConfig.getState());
            plugin.setType(pluginConfig.getType() == null || pluginConfig.getType().isEmpty() ? "jar" : pluginConfig.getType());

            if (SSA_PLUGINS.containsKey(plugin.getKey())) {
                log.info("插件{}已存在，跳过加载", plugin.getKey());
                continue;
            }
            SSA_PLUGINS.put(plugin.getKey(), plugin);
            if (PluginState.ENABLE.equals(plugin.getState())) {
                try {
                    log.info("正在加载插件：{}({})", plugin.getKey(), plugin.getFileName());
                    if (!plugin.load()) {
                        log.info("加载插件失败：({})", plugin.getFileName());
                        plugin.setState(PluginState.ERROR);
                    } else {
                        log.info("插件加载成功({})", plugin.getFileName());
                        plugin.setState(PluginState.ENABLE);
                    }
                } catch (Exception e) {
                    // 打印完整堆栈，便于定位插件加载失败根因
                    log.error("插件{}加载失败", plugin.getFileName(), e);
                    plugin.setState(PluginState.ERROR);
                }
            }
        }
    }

    /**
     * 这里主要考虑要不要把总出错的插件给禁用了
     */
    public void savePlugins() {
        // no-op for config-based plugins
    }

    @SsaApiMethod(describe = "获取加载的插件列表", frequency = 0)
    public JSONArray getPluginList(String pluginKey) {
        JSONArray ja = new JSONArray();
        SSA_PLUGINS.forEach((key, value) -> {
            if (!(pluginKey == null || pluginKey.isEmpty())) {
                if (key.contains(pluginKey)) {
                    ja.add(value);
                }
            } else {
                ja.add(value);
            }
        });
        return ja;
    }

    /**
     * 主要目的动态 启用插件
     * @param pluginKey 插件 Key
     * @return 是否成功
     */
    @SsaApiMethod(describe = "启用插件", frequency = 0)
    public String enablePlugin(String pluginKey) {
        SsaPlugin plugin = SSA_PLUGINS.get(pluginKey);
        if (plugin == null) {
            return "plugin:" + pluginKey + " is not fond.";
        }
        if (PluginState.ENABLE.equals(plugin.getState())) {
            return "plugin:" + pluginKey + " is already enabled.";
        }
        plugin.load();
        savePlugins();
        // 重新加载一下
        getMethodApiManager().reload();
        return "plugin:" + pluginKey + " is enabled";
    }

    /**
     * 主要目的动态 禁用插件
     * @param pluginKey 插件 Key
     * @return 是否成功
     */
    @SsaApiMethod(describe = "禁用插件", frequency = 0)
    public String disablePlugin(String pluginKey) {
        SsaPlugin plugin = SSA_PLUGINS.get(pluginKey);
        if (plugin == null) {
            return "plugin:" + pluginKey + " is not fond.";
        }
        if (PluginState.DISABLE.equals(plugin.getState())) {
            return "plugin:" + pluginKey + " is already disabled.";
        }
        // 卸载
        plugin.unLoad();
        savePlugins();
        // 重新加载一下
        getMethodApiManager().reload();
        return "plugin:" + pluginKey + " is disabled";
    }

    @SsaApiMethod(describe = "获取插件说明", frequency = 0)
    public String getPluginDoc(String pluginKey) {
        if (pluginKey == null || pluginKey.isEmpty()) {
            return null;
        }
        SsaPlugin plugin = SSA_PLUGINS.get(pluginKey);
        if (plugin == null) {
            return "未找到插件:" + pluginKey;
        }
        return plugin.getPluginDoc("readme.md");
    }
}
