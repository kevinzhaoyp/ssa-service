package com.ssa.plugin.fileApi.controller;

import com.ssa.plugin.fileApi.FileApiManager;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.ServletWebRequest;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 文件相关通用APIA
 * @author kevin
 * 专门为文件传输，操作而生
 * 原 HttpServletRequest request 使用 ServletWebRequest
 * https://blog.csdn.net/Boy_Martin/article/details/126058565
 */
@RestController
@RequestMapping("/v1")
public class FileApiController {

    @RequestMapping("/{apiName}")
    public Object runFileApi(@PathVariable String apiName, ServletWebRequest request, HttpServletResponse response)
    {
        return FileApiManager.runApi(apiName, request, response);
    }

    /**
     * 支持使用两级路径来调用
     * @param path1 路由
     * @param apiName api名称如a.b
     * @return Api
     */
    @RequestMapping("/{path1}/{apiName}")
    public Object runFileApi1(@PathVariable String path1, @PathVariable String apiName,
                              ServletWebRequest request, HttpServletResponse response) {
        String newApiName = path1 + "." + apiName;
        return FileApiManager.runApi(newApiName, request, response);
    }

}
