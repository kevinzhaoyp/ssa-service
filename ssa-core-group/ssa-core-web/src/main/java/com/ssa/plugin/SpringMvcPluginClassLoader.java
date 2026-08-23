package com.ssa.plugin;

import com.ssa.core.utils.ApiUtils;
import com.ssa.core.utils.SpringUtil;
import com.ssa.plugin.annotation.SsaApiClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 插件加载器，目前好像没啥需要扩展的。
 * 1.7以后能够直接调用close方法关闭打开的jar，须要判断当前运行的环境是否支持close方法，若是不支持，须要缓存，避免卸载模块后没法删除jar
 * 所以，只能使用1.7以后版本不打算支持1.7以下的了。
 */
@Slf4j
public class SpringMvcPluginClassLoader extends URLClassLoader {
    /**
     * 用到的 Spring对象
     */
    private ApplicationContext getAppContextOrNull() {
        return SpringUtil.getAppContext();
    }

    private RequestMappingHandlerMapping getRequestMappingHandlerMappingOrNull() {
        ApplicationContext ctx = getAppContextOrNull();
        if (ctx == null) {
            log.warn("Spring context is null; skip controller mapping registration");
            return null;
        }
        try {
            return ctx.getBean(RequestMappingHandlerMapping.class);
        } catch (Exception e) {
            log.error("RequestMappingHandlerMapping not available: {}", e.getMessage());
            return null;
        }
    }

    private BeanDefinitionRegistry getBeanDefinitionRegistryOrNull() {
        ApplicationContext ctx = getAppContextOrNull();
        if (!(ctx instanceof ConfigurableApplicationContext)) {
            log.warn("Spring context is not configurable; skip bean registration");
            return null;
        }
        return (BeanDefinitionRegistry) ((ConfigurableApplicationContext) ctx).getBeanFactory();
    }

    private JarFile jarFile;
    /**
     * 存放MappingInfo,卸载Jar时使用
     */
    private Set<RequestMappingInfo> requestMappingInfos;
    /**
     * 存放已 注册到 BeanDefinition 中所有的Class，卸载Jar时使用
     */
    private Set<Class<?>> beanDefinitionClasses;

    private final PluginDescriptor descriptor;
    private String pluginNamespace;

    private boolean canRegistryBeanDefinition(Class<?> aClass) {
        // 这里以后可以加，现在还不知道加什么
        return aClass.getDeclaredAnnotations().length > 0;
    }

    /**
     * 将 aClass 注册到beanDefinitionRegistry中
     *
     * @param aClass 类
     */
    private void regBeanDefinition(Class<?> aClass) {
        BeanDefinitionRegistry registry = getBeanDefinitionRegistryOrNull();
        if (registry == null) {
            return;
        }
        if (!registry.containsBeanDefinition(aClass.getName())) {
            BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(aClass);
            BeanDefinition beanDefinition = builder.getBeanDefinition();
            // 设置单利模式,后期考虑其它的吧。
            beanDefinition.setScope("singleton");
            registry.registerBeanDefinition(aClass.getName(), beanDefinition);
            beanDefinitionClasses.add(aClass);
        } else {
            log.error("{} Bean 已存在。", aClass.getName());
        }
    }

    /**
     * 从 beanDefinitionRegistry 中移除已注册的Bean 从API中移除
     */
    private void unRegBeanDefinition() {
        BeanDefinitionRegistry registry = getBeanDefinitionRegistryOrNull();
        if (registry == null) {
            return;
        }
        for (Class<?> aClass : beanDefinitionClasses) {
            registry.removeBeanDefinition(aClass.getName());
        }
        beanDefinitionClasses.clear();
    }

    /**
     * 注册 Controller 中的 RequestMapping
     *
     * @param controllerBeanName ControllerBeanName
     */
    private void regController(String controllerBeanName) {
        RequestMappingHandlerMapping requestMappingHandlerMapping = getRequestMappingHandlerMappingOrNull();
        ApplicationContext appContext = getAppContextOrNull();
        if (requestMappingHandlerMapping == null || appContext == null) {
            return;
        }
        Method getMappingForMethod =
                ReflectionUtils.findMethod(RequestMappingHandlerMapping.class, "getMappingForMethod", Method.class, Class.class);
        if (getMappingForMethod == null) {
            log.error("getMappingForMethod not found");
            return;
        }
        getMappingForMethod.setAccessible(true);
        Object controller = appContext.getBean(controllerBeanName);
        Method[] methods = controller.getClass().getDeclaredMethods();
        String[] shortNames = controllerBeanName.contains(".") ? controllerBeanName.split("\\.") : new String[]{controllerBeanName};
        String shortName = shortNames[shortNames.length - 1];
        String routePrefix = normalizeRoutePrefix(descriptor == null ? null : descriptor.getRoutePrefix());
        try {
            for (Method method : methods) {
                RequestMappingInfo requestMappingInfo = (RequestMappingInfo) getMappingForMethod.invoke(requestMappingHandlerMapping,
                        method, controller.getClass());
                if (requestMappingInfo != null) {
                    RequestMappingInfo mappingToRegister = requestMappingInfo;
                    if (!routePrefix.isEmpty()) {
                        RequestMappingInfo prefixInfo = RequestMappingInfo
                                .paths(routePrefix)
                                .build();
                        mappingToRegister = prefixInfo.combine(requestMappingInfo);
                    }
                    if (requestMappingHandlerMapping.getHandlerMethods().containsKey(mappingToRegister)) {
                        log.warn("Route conflict detected. plugin={}, mapping={}", pluginNamespace, mappingToRegister);
                        continue;
                    }
                    requestMappingHandlerMapping.registerMapping(mappingToRegister, controller, method);
                    requestMappingInfos.add(mappingToRegister);

                    log.info("成功注册Controller: {}/{}()，完整路由：{}", shortName, method.getName(), mappingToRegister);
                }
            }
        } catch (Exception e) {
            log.error("controller register error:{}", e.getMessage(), e);
        }
    }

    private String normalizeRoutePrefix(String routePrefix) {
        if (routePrefix == null) {
            return "";
        }
        String value = routePrefix.trim();
        if (value.isEmpty()) {
            return "";
        }
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    /**
     * 加载 Jar包中的文件
     * @param namespace 插件的 key
     */
    private void load(String namespace) {
        Enumeration<JarEntry> entry = jarFile.entries();
        ArrayList<String> controllerNameList = new ArrayList<>();
        while (entry.hasMoreElements()) {
            JarEntry jarEntry = entry.nextElement();
            String fileName = jarEntry.getName();
            if (jarEntry.isDirectory() || !fileName.endsWith(".class")) {
                continue;
            }
            // 只解析 .class 文件
            Class<?> aClass;
            try {
                String className = fileName.replace("/", ".").substring(0, fileName.length() - 6);
                // 自己定义的 loader路径可以找到
                aClass = this.loadClass(className);
            } catch (ClassNotFoundException e) {
                log.error("{} 加载失败({})", fileName, e.getMessage());
                break;
            }
            // 这里只记录了一下 class对应的 pluginKey,不知道有没有其它办法了。
            if ((!ApiUtils.strIsEmpty(namespace)) && (aClass.getAnnotation(SsaApiClass.class) != null)) {
                SsaPluginManager.classPluginKey.put(aClass.getName(), namespace);
            }
            // 这里只判断有注册就 注册bean
            if (canRegistryBeanDefinition(aClass)) {
                regBeanDefinition(aClass);
            }
            // 如果是Controller，需要注册一下. 先统一记录下来，后面统一注册，以免依赖问题
            if (aClass.getAnnotation(RestController.class) != null || aClass.getAnnotation(Controller.class) != null) {
                controllerNameList.add(aClass.getName());
            }
        }
        //最后再统一注册 Controller
        for (String s : controllerNameList) {
            regController(s);
        }
    }

    public SpringMvcPluginClassLoader(URL[] urls, ClassLoader parent, PluginDescriptor descriptor) {
        super(urls, parent);
        this.descriptor = descriptor;
    }

    public SpringMvcPluginClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
        this.descriptor = null;
    }

    public void init(String fileName, String namespace) throws IOException {
        // LinkedHashSet HashSet
        requestMappingInfos = new HashSet<>();
        beanDefinitionClasses = new HashSet<>();
        jarFile = new JarFile(fileName);
        pluginNamespace = namespace;
        load(namespace);
    }

    /**
     * 卸载时调用，处理卸载时逻辑
     *
     * @throws IOException IO 异常
     */
    @Override
    public void close() throws IOException {
        try {
//            unRegController();
            unRegBeanDefinition();
            super.close();
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
}