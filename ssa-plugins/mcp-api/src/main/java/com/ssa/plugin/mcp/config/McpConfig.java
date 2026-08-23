package com.ssa.plugin.mcp.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@RefreshScope
@Configuration
@Data
public class McpConfig {

    /**
     * mcp api endpoint
     */
    @Value("${ssa.mcp.endPoint:/mcp}")
    private String endPoint;
}
