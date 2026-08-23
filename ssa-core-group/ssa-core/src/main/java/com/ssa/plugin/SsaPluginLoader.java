package com.ssa.plugin;

public interface SsaPluginLoader {
    boolean supports(String type);

    default AutoCloseable load(String fileName, String namespace, ClassLoader parent, PluginDescriptor descriptor) throws Exception {
        return load(fileName, namespace, parent);
    }

    AutoCloseable load(String fileName, String namespace, ClassLoader parent) throws Exception;
}
