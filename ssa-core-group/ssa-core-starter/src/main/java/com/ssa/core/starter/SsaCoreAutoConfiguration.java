package com.ssa.core.starter;

import com.ssa.plugin.config.SsaPluginProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration
@ComponentScan(basePackages = "com.ssa")
@EnableConfigurationProperties(SsaPluginProperties.class)
public class SsaCoreAutoConfiguration {
}
