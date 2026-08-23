package com.ssa.apis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

//exclude = {DataSourceAutoConfiguration.class}
@SpringBootApplication
@ComponentScan(basePackages = {"com.ssa.*"})
@ConfigurationPropertiesScan
@EnableAsync
@Slf4j
public class SerApiApp {
    public static void main(String[] args) {
        log.info("SsaApiApp-启动。");
        ApplicationContext context = SpringApplication.run(SerApiApp.class, args);
        System.out.println("SsaApiApp-已启动。server.port:" + context.getEnvironment().getProperty("server.port"));
    }

    @Bean(name = "ssaTaskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("ssa-audit-");
        executor.initialize();
        return executor;
    }
}
