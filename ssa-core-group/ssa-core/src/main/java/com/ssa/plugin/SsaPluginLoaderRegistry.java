package com.ssa.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public final class SsaPluginLoaderRegistry {
    private static final List<SsaPluginLoader> LOADERS = new ArrayList<>();
    private static volatile boolean initialized = false;

    private SsaPluginLoaderRegistry() {}

    public static SsaPluginLoader getLoader(String type) {
        initIfNeeded();
        String safeType = type == null ? "" : type.trim().toLowerCase();
        for (SsaPluginLoader loader : LOADERS) {
            if (loader.supports(safeType)) {
                return loader;
            }
        }
        return null;
    }

    private static synchronized void initIfNeeded() {
        if (initialized) {
            return;
        }
        ServiceLoader<SsaPluginLoader> serviceLoader = ServiceLoader.load(SsaPluginLoader.class);
        for (SsaPluginLoader loader : serviceLoader) {
            LOADERS.add(loader);
        }
        initialized = true;
    }
}

