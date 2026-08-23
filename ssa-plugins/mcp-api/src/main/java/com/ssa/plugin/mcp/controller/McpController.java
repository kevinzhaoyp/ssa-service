package com.ssa.plugin.mcp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssa.plugin.mcp.config.McpConfig;
import com.ssa.plugin.mcp.service.BuildMcpTools;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.ServletContextAware;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * MCP server（基于官方 MCP Java SDK 的 Streamable HTTP 传输层）。
 *
 * <p>作为 ssa 插件随 ssa-ser 一同启动：插件加载器把本 @RestController 注册进主服务的
 * RequestMappingHandlerMapping，并按 ssa-plugin.yml 的 routePrefix=/mcp 自动拼装路由，
 * 最终对外暴露 POST/GET/DELETE /mcp。
 *
 * <p>协议细节（initialize 版本协商、Mcp-Session-Id 会话、SSE 流式、tools/list、
 * 入参 JSON Schema 校验等）全部交由官方 SDK 的 HttpServletStreamableServerTransportProvider
 * + McpServer 托管；本类只负责：① 把真实 ServletContext 注入 provider（手动 init）；
 * ② 把 HTTP 请求委托给 provider 的 doXxx；③ 把 {@link BuildMcpTools#buildToolSpecifications()}
 * 动态产出的工具注册到 server。
 *
 * <p><b>所有工具都在运行时由 {@link BuildMcpTools} 动态扫描 ymlapi 目录得到</b>（yml 声明 mpctool:true），
 * 本 Controller 不写死任何工具定义。工具构建与调用原语全部收敛在 BuildMcpTools（service 层），
 * 本 Controller 职责单一、易于维护。
 *
 * <p>所有 sql-api 调用直接走 ApiRunner.safeRun（与 /sqlApi 同进程、同 JVM），不再经 HTTP 代理；
 * 鉴权复用现有 JwtValidationFilter（请求带 Bearer 即注入 cur_user）。
 */
@RestController
@RequestMapping
@Slf4j
public class McpController implements ServletContextAware {

    private static final String SERVER_NAME = "ssa-mcp";
    private static final String SERVER_VERSION = "0.3.0";

    @Resource
    private McpConfig mcpConfig;

    /** 工具构建器：动态扫描 ymlapi、Schema 拼装、接口调用原语均在此（无状态） */
    @Resource
    private BuildMcpTools buildMcpTools;

    private ServletContext servletContext;
    private HttpServletStreamableServerTransportProvider provider;
    private McpSyncServer mcpServer;

    /** 已注册工具名集合，用于去重（扫描目录可能映射出同名，或 rescan 重复调用） */
    private final Set<String> registeredToolNames = new HashSet<>();

    private Method doPost;
    private Method doGet;
    private Method doDelete;

    @Override
    public void setServletContext(@NonNull ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    @PostConstruct
    public void init() {
        if (servletContext == null) {
            throw new IllegalStateException("ServletContext 未注入，无法初始化 MCP 传输层");
        }
        // 插件类加载器与主应用的类加载器不同，按类型从 Spring 容器获取 ObjectMapper 会失败
        //（主服务大量使用 fastjson，未将 jackson ObjectMapper 注册为 Spring bean），
        // 这里直接新建实例（MCP SDK 仅使用其基本 JSON 读写能力），并自动注册 classpath 内的 Jackson 模块
        // （如 JavaTimeModule），避免序列化时间类型时报错。
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        McpJsonMapper jsonMapper = new JacksonMcpJsonMapper(objectMapper);

        provider = HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(jsonMapper)
                .mcpEndpoint(mcpConfig.getEndPoint())
                .build();

        // 手动初始化 provider，把真实 ServletContext 注入，使其会话管理（Mcp-Session-Id）可用
        try {
            provider.init(new ServletConfig() {
                @Override
                public String getServletName() {
                    return "ssa-mcp-provider";
                }

                @Override
                public ServletContext getServletContext() {
                    return servletContext;
                }

                @Override
                public String getInitParameter(String name) {
                    return null;
                }

                @Override
                public Enumeration<String> getInitParameterNames() {
                    return Collections.emptyEnumeration();
                }
            });
        } catch (ServletException e) {
            throw new IllegalStateException("初始化 MCP 传输层失败", e);
        }

        // provider 的 doPost/doGet/doDelete 为 protected，缓存方法句柄以便反射调用
        Class<?> providerClass = HttpServletStreamableServerTransportProvider.class;
        this.doPost = findDeclaredMethod(providerClass, "doPost",
                HttpServletRequest.class, HttpServletResponse.class);
        this.doGet = findDeclaredMethod(providerClass, "doGet",
                HttpServletRequest.class, HttpServletResponse.class);
        this.doDelete = findDeclaredMethod(providerClass, "doDelete",
                HttpServletRequest.class, HttpServletResponse.class);
        this.doPost.setAccessible(true);
        this.doGet.setAccessible(true);
        this.doDelete.setAccessible(true);

        mcpServer = McpServer.sync(provider)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .build();

        // 动态扫描 ymlapi 目录，把声明了 mpctool 的接口注册为 MCP 工具
        registerDynamicTools();
    }

    private static Method findDeclaredMethod(Class<?> start, String name, Class<?>... paramTypes) {
        Class<?> c = start;
        while (c != null) {
            try {
                return c.getDeclaredMethod(name, paramTypes);
            } catch (NoSuchMethodException e) {
                c = c.getSuperclass();
            }
        }
        throw new IllegalStateException("找不到方法 " + name);
    }

    // ============ MCP 传输端点（routePrefix=/mcp 由插件加载器拼接） ============

    @PostMapping(consumes = "application/json", produces = {"application/json", "text/event-stream"})
    public void handlePost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        invoke(doPost, request, response);
    }

    @GetMapping(produces = "text/event-stream")
    public void handleGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        invoke(doGet, request, response);
    }

    @DeleteMapping
    public void handleDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        invoke(doDelete, request, response);
    }

    /**
     * 运行时热刷新：重新扫描 ymlapi 目录，把新增标记 {@code mpctool: true} 的接口注册为 MCP 工具，
     * 已注册的工具自动跳过（不重复注册）。便于在不重启 ssa-ser 的前提下，增量开放新接口。
     *
     * <p>映射路径：插件 routePrefix(/mcp) + 本方法 → POST /mcp/rescan
     */
    @PostMapping("/rescan")
    public Map<String, Object> rescan() {
        int before = registeredToolNames.size();
        registerDynamicTools();
        // 通知已连接的 MCP 客户端工具列表已变更，促其重新拉取 tools/list 以发现新注册的工具
        try {
            mcpServer.notifyToolsListChanged();
        } catch (Exception e) {
            log.debug("通知工具列表变更失败（客户端可能尚未连接）: {}", e.getMessage());
        }
        Map<String, Object> result = new HashMap<>(4);
        result.put("ok", true);
        result.put("registeredTools", registeredToolNames.size());
        result.put("added", registeredToolNames.size() - before);
        return result;
    }

    private void invoke(Method m, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            m.invoke(provider, request, response);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            if (cause instanceof ServletException) {
                throw new IOException("MCP 处理异常: " + cause.getMessage(), cause);
            }
            throw new IOException("MCP 处理异常: " + cause, cause);
        } catch (IllegalAccessException e) {
            throw new IOException("MCP 处理异常: " + e.getMessage(), e);
        }
    }

    // ============ 动态工具注册 ============

    /**
     * 从 {@link BuildMcpTools#buildToolSpecifications()} 取得动态工具规格数组，把尚未注册的工具
     * 逐个 addTool 到 MCP server（已注册的跳过，避免重复注册）。供 init / rescan 共用。
     */
    private void registerDynamicTools() {
        if (mcpServer == null) {
            log.warn("MCP server 未初始化，跳过工具注册");
            return;
        }
        McpServerFeatures.SyncToolSpecification[] specs = buildMcpTools.buildToolSpecifications();
        int added = 0;
        for (McpServerFeatures.SyncToolSpecification spec : specs) {
            String name = spec.tool().name();
            if (registeredToolNames.contains(name)) {
                continue;
            }
            try {
                mcpServer.addTool(spec);
                registeredToolNames.add(name);
                added++;
                log.info("注册 MCP 工具 {} -> api {}", name, apiNameOf(spec));
            } catch (Exception e) {
                log.error("注册 MCP 工具失败 {}: {}", name, e.getMessage());
            }
        }
        log.info("MCP 动态注册完成：本次新增 {} 个工具，累计已注册 {} 个", added, registeredToolNames.size());
    }

    /** 工具描述里已含 api 名；此处仅作日志冗余信息提取（描述形如 "sql-api 接口: xxx"） */
    private String apiNameOf(McpServerFeatures.SyncToolSpecification spec) {
        String desc = spec.tool().description();
        int idx = desc != null ? desc.indexOf("sql-api 接口: ") : -1;
        return idx >= 0 ? desc.substring(idx + "sql-api 接口: ".length()) : spec.tool().name();
    }
}
