package com.ssa.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.ConfigurableApplicationContext;

import lombok.extern.slf4j.Slf4j;

/**
 * SSA Gateway Application
 * 网关服务启动类
 * 
 * @author ssa
 */
@SpringBootApplication
@EnableDiscoveryClient
@Slf4j
public class GatewayApp {

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        log.info("Starting SSA Gateway...");
        ConfigurableApplicationContext context = SpringApplication.run(GatewayApp.class, args);
        String port = context.getEnvironment().getProperty("local.server.port",
                context.getEnvironment().getProperty("server.port", "unknown"));
        long durationMs = System.currentTimeMillis() - startTime;
        log.info("SSA Gateway started.port:{} | startup:{}ms", port, durationMs);
    }

}