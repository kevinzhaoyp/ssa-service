package com.ssa.plugin.methodApi;

import com.ssa.core.service.ApiState;
import com.ssa.core.service.ApiBase;
import com.ssa.core.utils.ApiException;
import com.ssa.plugin.annotation.SsaApiMethod;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

public class MethodApi extends ApiBase {
    /**
     * 需要调用的方法
     */
    private final Method method;
    /**
     * 方法 method 所在的类
     */
    private final Class<?> cla;
    /**
     * 已经被实例化的对象
     */
    private final Object instance;

    /**
     * 每次执行是否需要一个新的对象
     */
    private final boolean needNew = false;
    /**
     * 获取参数值
     * @param paramCount 形参数量
     * @param params 实参
     * @return 返回实参
     */
    private Object[] getArgs(int paramCount, Map<String, Object> params) {
        Object[] objs = new Object[paramCount];
        for(int i = 0; i < paramCount; i++) {
            Parameter p = method.getParameters()[i];
            String parameterName = p.getName();
            if (params == null) {
                objs[i] = null;
            } else {
                objs[i] = params.get(parameterName);
            }
        }
        return objs;
    }

    public MethodApi(Class<?> cla, Object insObject, Method method) {
        this.cla = cla;
        this.instance = insObject;
        this.method = method;
    }

    /**
     * 执行 MethodApi
     * @param params 方法参数
     * @return 方法返回结果值
     */
    public Object run(Map<String, Object> params) throws ApiException {
        if (this.method == null) {
            throw new ApiException("can not get methodApi：");
        }
        if (this.getState() == ApiState.DISABLE) {
            throw new ApiException(this.method.getName() + " api is disabled.");
        }

        Object obj = this.instance;
        if (needNew) {
            try {
                obj = this.cla.getDeclaredConstructor().newInstance();
            } catch (Exception ex) {
                throw new ApiException("create instance failed: " + this.cla.getName(), ex);
            }
        }
        int paramCount = method.getParameterCount();
        try {
            if (paramCount <= 0) {
                return method.invoke(obj);
            } else if((paramCount == 1) && (method.getParameters()[0].getType().isAssignableFrom(Map.class))) {
                // 接收一个Map参数，发现也有这种场景
                return method.invoke(obj, params);
            }
            // 只传 JSONObject 会不会效率高些？？但这样，接口形参只能接受一个，原 return method.invoke(obj, params);
            return method.invoke(obj, getArgs(paramCount, params));
        } catch (Exception e) {
            throw new ApiException("调用方法API：" + method.getName() + "失败：" + e.getMessage(), e);
        }
    }
    /**
     * 获取当前 API信息
     * @return 返回当前 API信息JSON对象
     */
    public Map<String, Object> getInfo() {
        Map<String, Object> jo = new HashMap<>(16);
        // 获取说明
        SsaApiMethod methodAnnotation = method.getAnnotation(SsaApiMethod.class);
        if (methodAnnotation != null) {
            jo.put("describe", methodAnnotation.describe());
        }
        // 获取所在类名
        jo.put("package", cla.getName());
        // 获取参数
        int parameterCount = method.getParameterCount();
        Map<String, Object> parameterJO = new HashMap<>(16);
        for(int i = 0; i < parameterCount; i++) {
            Parameter parameter = method.getParameters()[i];
            parameterJO.put(parameter.getName(), parameter.getType().getSimpleName());
        }
        jo.put("params", parameterJO);
        return jo;
    }

    public Boolean getRequireAuth() {
        SsaApiMethod methodAnnotation = method.getAnnotation(SsaApiMethod.class);
        if (methodAnnotation != null) {
            return methodAnnotation.requireAuth();
        }
        return true;
    }

//    @Override
    public String getDescribe() {
        SsaApiMethod methodAnnotation = method.getAnnotation(SsaApiMethod.class);
        if (methodAnnotation != null) {
            return methodAnnotation.describe();
        }
        return "";
    }
}
