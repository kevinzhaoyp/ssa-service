package com.ssa.core.utils;


import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URL;

/**
 * Spring相关全局变量 最早执行 需要通过 @Import
 * @author ssa
 */
@Service
public class SpringUtil {
    private SpringUtil() {
    }

    /**
     * 全局 ApplicationContext 是在 SSA AppListener中赋值的
     */
    private static ApplicationContext appContext;
    /**
     * jar 所在目录
     */
    private static final String jarHome = resolveJarHome();

    private static String resolveJarHome() {
        try {
            URL location = SpringUtil.class.getProtectionDomain().getCodeSource().getLocation();
            if (location == null) {
                return null;
            }
            File file = new File(location.toURI());
            File dir = file.isFile() ? file.getParentFile() : file;
            return dir != null ? dir.getAbsolutePath() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    public static void setAppContext(ApplicationContext appContext) {
        SpringUtil.appContext = appContext;
    }

    public static ApplicationContext getAppContext() {
        return SpringUtil.appContext;
    }

    public static Environment getEnvironment() {
        return SpringUtil.getAppContext().getEnvironment();
    }

    public static String getJarHome() {
        return SpringUtil.jarHome;
    }

    public static <T> T getBean(Class<T> tClass) {
        if (appContext == null) {
            throw new NoSuchBeanDefinitionException("Spring context is null");
        }
        return appContext.getBean(tClass);
    }

    public static <T> T getBean(String name, Class<T> tClass) {
        if (appContext == null) {
            throw new NoSuchBeanDefinitionException("Spring context is null");
        }
        return appContext.getBean(name, tClass);
    }
}