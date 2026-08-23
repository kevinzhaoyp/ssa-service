package com.ssa.apis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.ssa.*"})
@ConfigurationPropertiesScan
@Slf4j
public class ClientApiApp {
    public static void main(String[] args) {
        log.info("ClientApiApp-启动。");
        ApplicationContext context = SpringApplication.run(ClientApiApp.class, args);
        log.info("ClientApiApp-已启动。server.port:{}", context.getEnvironment().getProperty("server.port"));
    }
}