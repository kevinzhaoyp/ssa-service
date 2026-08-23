package com.ssa.plugin;

import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public final class PluginDescriptorReader {
    private PluginDescriptorReader() {}

    public static PluginDescriptor readFromJar(String jarPath) {
        String descriptorPath = "ssa-plugin.yml";
        try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jarPath)) {
            java.util.jar.JarEntry entry = jarFile.getJarEntry(descriptorPath);
            if (entry == null) {
                return null;
            }
            try (InputStream inputStream = jarFile.getInputStream(entry)) {
                return parseYaml(inputStream);
            }
        } catch (Exception e) {
            log.warn("Failed to read {} from {}: {}", descriptorPath, jarPath, e.getMessage());
            return null;
        }
    }

    public static PluginDescriptor readFromFile(String filePath) {
        try (InputStream inputStream = new FileInputStream(filePath)) {
            return parseYaml(inputStream);
        } catch (Exception e) {
            log.warn("Failed to read plugin descriptor from {}: {}", filePath, e.getMessage());
            return null;
        }
    }

    private static PluginDescriptor parseYaml(InputStream inputStream) {
        Yaml yaml = new Yaml();
        Map<String, Object> data = yaml.load(inputStream);
        if (data == null) {
            return null;
        }
        PluginDescriptor descriptor = new PluginDescriptor();
        descriptor.setKey(asString(data.get("key")));
        descriptor.setVersion(asString(data.get("version")));
        descriptor.setMainClass(asString(data.get("mainClass")));
        descriptor.setRoutePrefix(asString(data.get("routePrefix")));
        descriptor.setDependencies(asStringList(data.get("dependencies")));
        descriptor.setAuthor(asString(data.get("author")));
        descriptor.setReleaseDate(asString(data.get("releaseDate")));
        descriptor.setDescription(asString(data.get("description")));
        return descriptor;
    }

    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value).trim();
    }

    private static List<String> asStringList(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            List<String> results = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    String text = String.valueOf(item).trim();
                    if (!text.isEmpty()) {
                        results.add(text);
                    }
                }
            }
            return results.isEmpty() ? null : results;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : List.of(text);
    }
}
