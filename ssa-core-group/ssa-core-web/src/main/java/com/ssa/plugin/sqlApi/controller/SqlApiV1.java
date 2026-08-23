package com.ssa.plugin.sqlApi.controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.ssa.core.service.ApiRunner;
import com.ssa.core.utils.ApiResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sqlApi/v1")
public class SqlApiV1 {
    @GetMapping("/index")
    public String index() {
        return "SqlApi V1 服务已启动";
    }

    @PostMapping("/getData")
    public Object getData(@RequestBody JSONObject jo) {

        return ApiRunner.safeRun(jo.getString("name"),
                jo.getJSONObject("params"),
                jo.getBooleanValue("reload"));

    }
    /**
     * 执行sql模板接口，并返回结果
     * 增加 支持 直接调用 存储过程 方法，减少 apis 的数量
     * 返回的data类型{return:{},result: any} //这两都是关键词，不知道会不会有问题
     *
     * @param jo {name:'', params:{},reload: true}
     * @return RestResult
     * 后期扩展 执行前，执行后 参数
     */
    @PostMapping("/{apiName}")
    public Object runApi(@PathVariable String apiName, @RequestBody JSONObject jo) {

        return ApiRunner.safeRun(apiName, jo, false);

    }

    @PostMapping("/{path}/{apiName}")
    public Object runApi(@PathVariable String path, @PathVariable String apiName, @RequestBody JSONObject params) {
        apiName = path + "/" + apiName;
        return ApiRunner.safeRun(apiName, params, false);
    }
    /**
     * 支持 一次请求多个接口的情况 先让前端一个一个的调吧。。
     * jo {params:{}共享参数，apis:[{和单个的一致}] api列表}
     * 返回值 data: {"apiName": ApiResult}
     */
    @PostMapping("/getApisData")
    public Object getApisData(@RequestBody JSONObject jo) {
        JSONArray ja = jo.getJSONArray("apis");
        JSONObject params = jo.getJSONObject("params");
        JSONObject result = new JSONObject();
        for (int i = 0; i < ja.size(); i++) {
            JSONObject api = ja.getJSONObject(i);
            String apiName = api.getString("name");
            JSONObject args = new JSONObject();
            if (params != null) {
                args.putAll(params);
            }
            args.putAll(api.getJSONObject("params"));
            ApiResult<Object> apiResult = ApiRunner.safeRun(apiName, args, jo.getBooleanValue("reload"));
            result.put(apiName, apiResult);
        }
        return ApiResult.success(result);
    }
}
