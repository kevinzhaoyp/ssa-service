package com.ssa.core;

import com.ssa.core.service.ApiRunner;
import com.ssa.core.utils.SpringUtil;
import com.ssa.plugin.SsaPluginManager;
import com.ssa.plugin.methodApi.MethodApiManager;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * Api入口，加载插件，加载API列表等
 * 监听 ApplicationListener 启动后加载插件，防止插件加载错误后导致无法启动问题
 * SpringBoot启动时的6个事件（可参考一下）： https://www.jianshu.com/p/3166ec8a5337
 */
@Configuration
@Slf4j
public class AppListener implements ApplicationListener<ApplicationEvent> { //ContextRefreshedEvent原
    private static boolean loaded = false;

    /**
     * 加载插件，API等
     * @param appContext 当前 ApplicationContext
     */
    private void loadApi(ApplicationContext appContext) {
        if (appContext != null) {
            SpringUtil.setAppContext(appContext);
        }
        log.info("api start load plugins");
        SsaPluginManager pluginManager = SpringUtil.getBean(SsaPluginManager.class);
        log.info("pluginPath:{}", SsaPluginManager.getPluginPath());
        if (new File(SsaPluginManager.getPluginPath()).exists()) {
            // 如果目录不存在 不加载插件
            pluginManager.loadPlugins();
        }
        // 加载 API
        log.info("api start load methodApi");
        SpringUtil.getBean(MethodApiManager.class).reload();
        // 加载 APIManager
        ApiRunner.getApiManagerList();
        loaded = true;
    }
    @Override
    public void onApplicationEvent(@NonNull ApplicationEvent event) {
        // ApplicationFailedEvent 应用启动失败后产生这个事件
        if (loaded) return;
        //context已经refresh且application and command-line runners（如果有） 调用之前发送这个事件
        if (event instanceof ApplicationStartedEvent) {
            loadApi(((ApplicationStartedEvent) event).getApplicationContext());
        }
        //ApplicationReadyEvent  application and command-line runners （如果有）执行完后发送这个事件，此时应用已经启动完毕
    }
}
