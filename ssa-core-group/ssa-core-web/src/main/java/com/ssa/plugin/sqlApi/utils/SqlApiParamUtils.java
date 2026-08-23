package com.ssa.plugin.sqlApi.utils;

import com.ssa.core.utils.ApiException;
import com.ssa.core.utils.ApiUtils;
import com.ssa.core.service.ApiParam;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SqlApiParamUtils {
    private SqlApiParamUtils() {
    }

    /**
     * 检查参数是否正确
     * @param args 实参
     * @param params 定义的参数
     */
    public static void checkParams(Map<String, Object> args, List<ApiParam> params) throws ApiException {
        if (args == null || params == null || params.isEmpty()) {
            return;
        }
        for (ApiParam param : params) {
            Object arg = args.get(param.getName());
            // 迭代 2（底稿复核）：参数缺失/为空且声明了 defValue 时统一填充默认值（不限 required），
            // 保证模板中可选参数变量始终已定义（如 memo/paper_memos），避免 Enjoy 严格模式
            // "parameter must be assigned" 报错；原 required 行为不变（缺失且无 defValue 抛参数异常）。
            if (ApiUtils.isNullOrEmpty(arg)) {
                if (Objects.nonNull(param.getDefValue())) {
                    args.put(param.getName(), param.getDefValue());
                } else if (param.isRequired()) {
                    String hint = param.getHint();
                    if (ApiUtils.isNullOrEmpty(hint)) {
                        hint = "参数：" + param.getName() + " 不能为空。";
                    }
                    throw new ApiException(hint);
                }
            }
        }
    }
}

