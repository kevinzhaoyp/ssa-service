package com.ssa.plugin.fileApi;

import com.jfinal.plugin.activerecord.DbPro;
import com.ssa.core.service.ApiBase;
import com.ssa.core.service.ApiState;
import com.ssa.core.service.ISsaApiManager;
import com.ssa.core.utils.*;
import com.ssa.plugin.fileApi.enums.FileOperate;
import com.ssa.plugin.fileApi.exec.FileApiDelete;
import com.ssa.plugin.fileApi.exec.FileApiDownload;
import com.ssa.plugin.fileApi.exec.FileApiUpload;
import com.ssa.plugin.fileApi.model.FileApi;
import com.ssa.core.service.ApiParam;
import com.ssa.core.service.CurrentUser;
import com.ssa.core.service.UserContext;
import com.ssa.plugin.sqlApi.ISqlApiExec;
import com.ssa.plugin.sqlApi.event.SqlApiEventExec;
import com.ssa.plugin.sqlApi.utils.ApiLoaderYml;
import com.ssa.plugin.sqlApi.utils.DbUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.ServletWebRequest;

import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 文件相关API Manager
 * @author kevin
 */

@Service("fileApiManager")
@Order(2)
@Slf4j
public class FileApiManager implements ISsaApiManager {
    private static final Map<String, Class<? extends ISqlApiExec>> apiExecMap = new HashMap<>();
    public static void regApiExec(String type, Class<? extends ISqlApiExec> execClass) {
        apiExecMap.put(type.toLowerCase(), execClass);
    }
    public static ISqlApiExec createExec(String type) {
        if (type == null || type.isEmpty()) {
            throw new ApiException("类型不能为空");
        }

        Class<? extends ISqlApiExec> execClass = apiExecMap.get(type.toLowerCase());
        if (execClass == null) {
            throw new ApiException("未知的执行器类型: " + type);
        }

        try {
            return execClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("创建执行器实例失败: " + type, e);
        }
    }
    /**
     * 获取实参 包括验证
     * @param fileApi 文件Api实例
     * @param args 原params必须包含 request
     * @return 获取并验证成功返回true,参数会put到args中，如果失败，返回false并put一个errorMsg
     * 参考：RequestParamMethodArgumentResolver
     */
    private boolean getArgs(FileApi fileApi, Map<String, Object> args) {
//        RequestParamMethodArgumentResolver
        ServletWebRequest request = (ServletWebRequest) args.get("request");
        List<ApiParam> paramList = fileApi.getParams2();
        for (ApiParam param : paramList) {
            String[] paramValues = request.getParameterValues(param.getName());
            Object arg = null;
            if (paramValues != null) {
                arg = paramValues.length == 1 ? paramValues[0] : paramValues;
            }
            // 必填判断,这里暂时没什么好办法全判断了。
            if (param.isRequired() && (arg == null)) {
                args.put("errorMsg", param.getName() + " is required");
                return false;
            }
            args.put(param.getName(), arg);
        }
        return true;
    }

    /**
     * 从request中获取实参
     * @param args 原实参，会增加到args中
     * @return
     */
    private boolean getArgs(Map<String, Object> args) {
        ServletWebRequest request = (ServletWebRequest) args.get("request");
        Map<String, String[]> params = request.getParameterMap();
        for (String key: params.keySet()) {
            String[] values = params.get(key);
            Object value = null;
            if (values != null) {
                value = values.length == 1 ? values[0] : values;
            }
            args.put(key, value);
        }
        return true;
    }

    public static ApiResult<Object> runApi(String name, ServletWebRequest request, HttpServletResponse response) {
        FileApiManager fileApiManager = SpringUtil.getBean(FileApiManager.class);
        Map<String, Object> params = new HashMap<>(16);
        params.put("request", request);
        params.put("response", response);
        return fileApiManager.run(name, params, true);
    }

    /**
     * 解析路径，返回一个正确的路径
     * @param path 路径模板 $(fileapi.filePath)/#(subdir)/ 或绝对路径
     * @param args 实参
     * @return 解析后的路径
     * <p>注意：不要用 Db.templateByString。SQL 模板会处理反斜杠转义（如 \f），
     * 且 #(x) 可能给字符串加引号，导致落盘路径错误。</p>
     */
    public static String getPath(String path, Map<String, Object> args) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        // 1) 替换 $(config.key)
        java.util.regex.Pattern cfgPattern = java.util.regex.Pattern.compile("\\$\\(([^)]+)\\)");
        java.util.regex.Matcher cfgMatcher = cfgPattern.matcher(path);
        StringBuffer cfgBuf = new StringBuffer();
        while (cfgMatcher.find()) {
            String key = cfgMatcher.group(1);
            String val = SpringUtil.getEnvironment().getProperty(key);
            if (val == null) {
                val = "";
            }
            // 统一成 /，避免 \f \t \n 等被后续解析吃掉
            val = val.replace('\\', '/');
            cfgMatcher.appendReplacement(cfgBuf, java.util.regex.Matcher.quoteReplacement(val));
        }
        cfgMatcher.appendTail(cfgBuf);
        path = cfgBuf.toString();

        // 2) 替换 #(var) 为实参字面量（不加 SQL 引号）
        java.util.regex.Pattern varPattern = java.util.regex.Pattern.compile("#\\(([^)]+)\\)");
        java.util.regex.Matcher varMatcher = varPattern.matcher(path);
        StringBuffer varBuf = new StringBuffer();
        while (varMatcher.find()) {
            String key = varMatcher.group(1);
            Object raw = args == null ? null : args.get(key);
            String val = raw == null ? "" : String.valueOf(raw);
            varMatcher.appendReplacement(varBuf, java.util.regex.Matcher.quoteReplacement(val));
        }
        varMatcher.appendTail(varBuf);
        return varBuf.toString().replace('\\', '/');
    }

    @Override
    public ApiBase getApi(String name, boolean reload) {
        ApiLoaderYml apiLoaderYml = SpringUtil.getBean(ApiLoaderYml.class);
        return apiLoaderYml.getApi(name, FileApi.class);
    }

    @Override
    public boolean reload() {
        return false;
    }

    @Override
    public ApiResult<Object> run(String name, Map<String, Object> params, boolean reload) {
        // 获取 SqlApi 实例
        ApiBase api = getApi(name, reload);
        if (api == null) {
            return ApiResult.failed("not found API: " + name);
        }
        return run(api, params, reload);
    }
    /**
     * 运行接口
     * RequestParamMethodArgumentResolver  获取RequestParam的源码
     * @param api ApiBase
     * @param params 实参
     * @param reload 是否重新加载，只有 SqlApi存在。
     * @return ApiResult
     */
    @Override
    public ApiResult<Object> run(ApiBase api, Map<String, Object> params, boolean reload) {
        if(ApiState.ERROR.equals(api.getState())) {
            return ApiResult.failed(api.getDescribe());
        }
        // 先获取 db 判断Dialect
        FileApi fileApi = (FileApi) api;
        DbPro db = DbUtils.getDb(fileApi.getDatasource());
        if (db == null) {
            return ApiResult.failed(ApiResultCode.NOT_FOUND, "datasource " + fileApi.getDatasource() + " not found.");
        }
        // 获取实参
        if (!getArgs(params)) {
            return ApiResult.failed(params.getOrDefault("errorMsg", "获取实参出错 ").toString());
        }
        // 注入当前登录用户到 params（cur_user 对象），供 YML 模板(#para(cur_user.user_id) 等)
        // 与事件(args.get("cur_user"))按需取用；未登录时跳过（与 SqlApiManager 行为一致）。
        // 放在 getArgs 之后，确保服务端鉴权身份覆盖客户端可能传入的同名参数，避免越权。
        CurrentUser curUser = UserContext.get();
        if (curUser != null) {
            Map<String, Object> curUserInfo = new HashMap<>(4);
            curUserInfo.put("user_id", curUser.getUserId());
            curUserInfo.put("user_name", curUser.getUserName());
            curUserInfo.put("real_name", curUser.getRealName());
            params.put("cur_user", curUserInfo);
        }
        // 执行前置操作
        SqlApiEventExec eventExec = new SqlApiEventExec(fileApi, db, params);
        try {
            if (!eventExec.exec(fileApi.getOnBefore())) {
                return ApiResult.failed("前置事件执行错误，终止执行");
            }
        } catch (RuntimeException e) {
            // 迭代 1：前置校验失败不抛 500，按业务失败返回（与 SqlApiManager 行为对齐）
            return ApiResult.failed(e.getMessage());
        }
        // 执行
        ApiResult<Object> apiResult;
        ISqlApiExec apiExec;
        if (Objects.requireNonNull(fileApi.getOptions().getOperate()) == FileOperate.DOWNLOAD) {
            apiExec = new FileApiDownload();
        } else if (Objects.requireNonNull(fileApi.getOptions().getOperate()) == FileOperate.DELETE) {
            apiExec = new FileApiDelete();
        } else {
            apiExec = new FileApiUpload();
        }
        apiResult = apiExec.exec(db, api, params);
        // 后置事件
        if (apiResult.isOk()) {
            eventExec.exec(fileApi.getOnSuccess());
        } else {
            eventExec.exec(fileApi.getOnError());
        }
        return apiResult;
    }

}
