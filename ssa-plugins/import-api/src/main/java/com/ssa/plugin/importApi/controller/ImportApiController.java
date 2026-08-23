package com.ssa.plugin.importApi.controller;

import com.ssa.plugin.importApi.ImportApiManager;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 数据导入导出相关通用API
 * @author kevin
 * 目前只支持excel的导入导出
 */
@RestController
@RequestMapping("/importApi/v1")
public class ImportApiController {
    @RequestMapping("/{apiName}")
    public Object runFileApi(@PathVariable String apiName, ServletWebRequest request, HttpServletResponse response)
    {
        return ImportApiManager.runApi(apiName, request, response);
    }

    /**
     * 支持使用两级路径来调用
     * @param path1
     * @param apiName
     * @param request
     * @param response
     * @return Api
     */
    @RequestMapping("/{path1}/{apiName}")
    public Object runFileApi1(@PathVariable String path1, @PathVariable String apiName,
                              ServletWebRequest request, HttpServletResponse response) {
        String newApiName = path1 + "." + apiName;
        return ImportApiManager.runApi(newApiName, request, response);
    }
}
