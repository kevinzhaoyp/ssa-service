package com.ssa.plugin.config;

import com.ssa.plugin.PluginState;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@ConfigurationProperties(prefix = "ssa.plugin")
public class SsaPluginProperties {
    /**
     * Root directory for plugin jars.
     */
    private String pluginPath;

    private List<Plugin> plugins = new ArrayList<>();

    @Setter
    @Getter
    public static class Plugin {
        /**
         * 插件 Key 唯一标识
         */
        private String key;
        /**
         * 插件文件名, 如：plugin.jar,可以是全路径也可以是相对路径
         */
        private String fileName;
        /**
         * 插件状态
         */
        private PluginState state;
        /**
         * 插件类型 jar,class,js,python 默认jar
         */
        private String type = "jar";

    }
}
