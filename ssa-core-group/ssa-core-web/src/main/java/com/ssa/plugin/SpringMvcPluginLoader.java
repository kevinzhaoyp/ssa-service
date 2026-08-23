package com.ssa.plugin;

import java.net.URL;

public class SpringMvcPluginLoader implements SsaPluginLoader {
    @Override
    public boolean supports(String type) {
        return type == null || type.isEmpty() || "jar".equalsIgnoreCase(type);
    }

    @Override
    public AutoCloseable load(String fileName, String namespace, ClassLoader parent) throws Exception {
        return load(fileName, namespace, parent, null);
    }

    @Override
    public AutoCloseable load(String fileName, String namespace, ClassLoader parent, PluginDescriptor descriptor) throws Exception {
        URL url = new URL("file:" + fileName);
        SpringMvcPluginClassLoader loader = new SpringMvcPluginClassLoader(new URL[]{url}, parent, descriptor);
        loader.init(fileName, namespace);
        return loader;
    }
}
