package com.ssa.plugin.sqlApi.utils;

import com.ssa.core.service.ApiBase;
import com.ssa.core.service.ApiState;
import com.ssa.core.utils.SpringUtil;
import com.ssa.plugin.sqlApi.ISqlApiLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 从 yml文件中加载API
 * @author ssa
 */
@Component
@Order(6)
@Slf4j
@ConfigurationProperties(prefix = "ssa.sqlapi.template")
public class ApiLoaderYml implements ISqlApiLoader {
    /**
     * 路径中需要保留的点。最多保留 1个点两级，其它的全转换为路径。如 a.b.c.d.e.f 转换后  a/b/c/d/e.f
     */
    private static final int PATH_LEVEL = 2;
    /**
     * 模板目录列表 从配置文件中读取
     */
    private static List<String> path;

    public void setPath(List<String> path) {
        ApiLoaderYml.path = path;
        if (ApiLoaderYml.path.isEmpty()) {
            ApiLoaderYml.path.add(SpringUtil.getJarHome() + "/sqlapi/");
        }
        log.info("模板路径：{}", ApiLoaderYml.path);
    }
    /**
     * 根据 apiName 解析路径
     * 最多保留 1个点两级，其它的全转换为路径。如 a.b.c.d.e.f 转换后  a/b/c/d/e.f
     * 支持将 _ 转为路径 如：a_b 会转为 a/b.yml
     * @param apiName api 名称
     * @return 返回文件名（全路径，但不包含扩展名）
     */
    public static String getApiFileByApiName(String apiName) {
        String[] names = apiName.split("\\.");
        if (names.length <= PATH_LEVEL) {
            return apiName.replace("_", File.separator);
        }
        StringBuilder newName = new StringBuilder();
        for(int i = 0; i < names.length; i++) {
            String separator = i < names.length - PATH_LEVEL ? File.separator : i < names.length - 1 ? "." : "";
            newName.append(names[i]).append(separator);
        }
        return newName.toString().replace("_", File.separator);
    }

    @Override
    public <T> ApiBase getApi(String apiName, Class<T> cla) {
        // yaml 文件路径
        String yamlFile = "";
        if((ApiLoaderYml.path == null) || (ApiLoaderYml.path.isEmpty())) {
            return null;
        }
        // 找出正确的 yml文件
        for(String p: ApiLoaderYml.path) {
            // 自动在路径后面加上 / 免得出错
            yamlFile = p + (p.endsWith(File.separator) || p.endsWith("/") ? "" : File.separator) +  getApiFileByApiName(apiName) + ".yml";
            if (new File(yamlFile).exists()) {
                break;
            }
        }
        //
        try (InputStream inputStream = new FileInputStream(yamlFile)) {
            LoaderOptions options = new LoaderOptions();
            // key 不允许重复
            options.setAllowDuplicateKeys(false);
            Constructor constructor = new Constructor(cla, options);
            Yaml yaml = new Yaml(constructor);

            try {
                Thread currentThread = Thread.currentThread();
                ClassLoader oldClassLoader = currentThread.getContextClassLoader();
                try {
                    currentThread.setContextClassLoader(cla.getClassLoader());
                    T api = yaml.loadAs(inputStream, cla);
                    ((ApiBase) api).setLoaderFrom("type:SqlApi,from:" + yamlFile);
                    return (ApiBase) api;
                } finally {
                    currentThread.setContextClassLoader(oldClassLoader);
                }
            } catch (RuntimeException e) {
                ApiBase errorApi = newErrorApi(cla, yamlFile + " 解析出错:" + e.getMessage());
                log.error("解析文件 {} 出错: {}", yamlFile, e.getMessage());
                return errorApi;
            }
        } catch (IOException e) {
            log.error("找不到文件: {}", yamlFile);
            return null;
        }
    }

    private <T> ApiBase newErrorApi(Class<T> cla, String describe) {
        try {
            ApiBase errorApi = (ApiBase) cla.getDeclaredConstructor().newInstance();
            errorApi.setState(ApiState.ERROR);
            errorApi.setDescribe(describe);
            return errorApi;
        } catch (ReflectiveOperationException e) {
            ApiBase fallback = new ApiBase();
            fallback.setState(ApiState.ERROR);
            fallback.setDescribe(describe + " (实例化失败: " + e.getClass().getSimpleName() + ")");
            return fallback;
        }
    }
}
