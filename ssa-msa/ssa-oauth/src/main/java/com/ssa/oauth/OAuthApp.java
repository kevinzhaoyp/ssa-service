package com.ssa.oauth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * SSA OAuth Service Application
 * 认证授权中心启动类
 * 
 * @author ssa
 */
@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan(basePackages = {"com.ssa.*"})
@ConfigurationPropertiesScan
public class OAuthApp {

    public static void main(String[] args) {
        SpringApplication.run(OAuthApp.class, args);
    }

}