package com.ssa.plugin;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@Slf4j
@Data
public class SsaPlugin {
    /**
     * 插件Key，为了防止Api名重复，调用时可用 key.classname.methodName 后续是不是可以在其他地方想办法
     */
    private String key;
    /**
     * 插件类型 jar,class,js,python
     */
    private String type;
    /**
     * 文件/目录
     */
    private String fileName;
    /**
     * 状态：enable,disable，error
     */
    private PluginState state;
    /**
     * 插件加载器
     */
    private AutoCloseable pluginClassLoader;
    /**
     * 插件配置文件
     */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Properties properties;
    /**
     * 插件描述符
     */
    private PluginDescriptor descriptor;
    /**
     * 解析jar将所有Class,Annotation都解析出来
     * @return Boolean 是否加载成功
     * load 方法主要流程：
     * 1) 组合插件路径与文件名，检查文件是否存在；不存在则记录错误并返回 false。
     * 2) 从 ServiceLoader 中按 type 获取对应的插件加载器。
     * 3) 调用加载器完成初始化并返回可关闭的加载器实例。
     * 4) 设置状态为 ENABLE。
     */
    public boolean load() {
        File file = new File(this.fileName);
        if (!file.exists()) {
            log.error("插件文件:{} 不存在.", fileName);
            log.info("可以去市场下载，后面做吧");
            return false;
        }
        try {
            descriptor = PluginDescriptorReader.readFromJar(fileName);
            if (descriptor == null) {
                log.warn("ssa-plugin.yml not found in {}", fileName);
            } else if (!validateDescriptor(descriptor)) {
                return false;
            }
            SsaPluginLoader loader = SsaPluginLoaderRegistry.getLoader(this.type);
            if (loader == null) {
                log.error("No plugin loader found for type: {}", this.type);
                return false;
            }
            pluginClassLoader = loader.load(fileName, this.getKey(), Thread.currentThread().getContextClassLoader(), descriptor);
            if (pluginClassLoader == null) {
                log.error("Plugin loader returned null for file: {}", fileName);
                return false;
            }
            setState(PluginState.ENABLE);
            return true;
        } catch (Exception e) {
            // 打印完整堆栈，便于定位插件加载失败根因（如 McpController @PostConstruct 初始化异常）
            log.error("load plugin error", e);
            return false;
        }
    }

    private boolean validateDescriptor(PluginDescriptor descriptor) {
        String descKey = descriptor.getKey();
        String descVersion = descriptor.getVersion();
        if (descKey == null || descKey.isEmpty() || descVersion == null || descVersion.isEmpty()) {
            log.error("ssa-plugin.yml missing required fields: key/version for plugin {}", this.fileName);
            return false;
        }
        if (this.key != null && !this.key.isEmpty() && !this.key.equals(descKey)) {
            log.warn("ssa-plugin.yml key ({}) differs from config key ({})", descKey, this.key);
        }
        return true;
    }

    /**
     * 卸载插件
     * @return Boolean 是否卸载成功
     */
    public boolean unLoad() {
        if (pluginClassLoader == null) {
            return false;
        }
        try {
            pluginClassLoader.close();
            return true;
        } catch (Exception e) {
            this.state = PluginState.ERROR;
            log.error("unload plugin error:{}", e.getMessage());
            return false;
        } finally {
            this.state = PluginState.DISABLE;
        }
    }

    public String getPluginDoc(String resourceFileName) {
        String jarPath = fileName;
        try (JarFile jarFile = new JarFile(jarPath)) {
            JarEntry entry = jarFile.getJarEntry(resourceFileName);
            if (entry == null) {
                return "未找到资源文件：" + resourceFileName;
            }
            try (InputStream inputStream = jarFile.getInputStream(entry)) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("Failed to read plugin doc {} from {}: {}", fileName, jarPath, e.getMessage());
            return null;
        }
    }
}
