package com.ssa.plugin.methodApi;

import com.ssa.core.service.ApiBase;
import com.ssa.core.service.ISsaApiManager;
import com.ssa.core.utils.ApiResult;
import com.ssa.core.utils.SpringUtil;
import com.ssa.plugin.annotation.SsaApiClass;
import com.ssa.plugin.annotation.SsaApiMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ssa.core.utils.ApiResultCode.NOT_API_NAME;

@Service("methodApiManager")
@Order(0)
@SsaApiClass("api")
@Slf4j
public class MethodApiManager implements ISsaApiManager {
    /**
     * 所有方法 如果要按顺序使用 LinkedHashMap TreeMap,如果要快使用HashMap
     */
    private final static Map<String, MethodApi> apiMap = new LinkedHashMap<>();
    private final static Map<Class<?>, List<Method>> annotatedMethodCache = new LinkedHashMap<>();
    private final static Map<Class<?>, String> classPrefixCache = new LinkedHashMap<>();
    private MethodApiManager() {}
    /**
     * 重新加载 API 列表
     * @return 是否成功
     */
    @Override
    @SsaApiMethod(name = "reloadMethodApi", describe = "重新加载所有方法")
    public boolean reload() {
        apiMap.clear();
        Map<String, Object> apiClasses = SpringUtil.getAppContext().getBeansWithAnnotation(SsaApiClass.class);
        // 获取ApiClass中所有加了 ApiMethod的方法。
        for (Object classObj : apiClasses.values()) {
            addClassMethod(classObj.getClass(), classObj);
        }
        return true;
    }

    public int addClassMethod(Class<?> aClass, Object classObject) {
        int result = 0;
        // 获取类名
        String classAnnotationValue = classPrefixCache.computeIfAbsent(aClass, clazz -> {
            String value = StringUtils.uncapitalize(clazz.getSimpleName());
            SsaApiClass classAnnotation = clazz.getAnnotation(SsaApiClass.class);
            if ((classAnnotation != null) && (!classAnnotation.value().equals(""))) {
                value = classAnnotation.value();
            }
            return value;
        });

        // 获取所有 MethodApi
        List<Method> methods = annotatedMethodCache.computeIfAbsent(aClass, clazz -> {
            Method[] allMethods = clazz.getMethods();
            List<Method> list = new ArrayList<>(allMethods.length);
            for (Method method : allMethods) {
                if (method.getAnnotation(SsaApiMethod.class) != null) {
                    list.add(method);
                }
            }
            return list;
        });

        for (Method method : methods) {
            SsaApiMethod methodAnnotation = method.getAnnotation(SsaApiMethod.class);
            if (methodAnnotation == null) {
                continue;
            }
            String name = method.getName();
            if (!methodAnnotation.name().isEmpty()) {
                name = methodAnnotation.name();
            }
            if (!methodAnnotation.ignoreClassValue()) {
                name = classAnnotationValue + "." + name;
            }
            MethodApi api = new MethodApi(aClass, classObject, method);
            api.setState(methodAnnotation.state());
            api.setDescribe(methodAnnotation.describe());
            api.setRequireAuth(methodAnnotation.requireAuth());
            api.setLoaderFrom("type:MethodApi,from:" + aClass.getName());
            // 防止覆盖
            if (apiMap.containsKey(name)) {
                log.error("{}已存在。Class:{}", name, aClass.getName());
            } else {
                result++;
                apiMap.put(name, api);
                log.info("添加接口：{} 成功。Class: {}", name, aClass.getName());
            }
        }
        return result;
    }

    public int removeClassMethod(Class<?> cla) {
        // 还没想好怎么remove 因为有可能会把不属于自己的Method给remove了。
        return 0;
    }

    /**
     * 获取 Api信息
     * @param name Api 名称
     * @return 如果没找到，返回字符 未找到接口，否则返回JSONObject Api信息
     */
    @SsaApiMethod(describe = "获取 ApiMethod 信息")
    public Object getMethodApiInfo(String name) {
        MethodApi api = (MethodApi)getApi(name, false);
        if (api == null) {
            return "未找到接口：" + name;
        }
        Map<String, Object> jo = api.getInfo();
        jo.put("apiName", name);
        return jo;
    }

    @SsaApiMethod(describe = "获取所有 MethodApi 名称")
    public String[] getMethodApiNames() {
        return apiMap.keySet().toArray(new String[0]);
    }

    @SsaApiMethod(describe = "获取 Api 列表")
    public List<Map<String, Object>> getMethodApiList(String searchText) {
        boolean search = !(searchText == null || searchText.isEmpty());
        List<Map<String, Object>> list = new ArrayList<>();
        apiMap.forEach((key, value) -> {
            Map<String, Object> map = value.getInfo();
            if (search) {
                if (key.contains(searchText)) {
                    map.put("name", key);
                    list.add(map);
                }
            } else {
                map.put("name", key);
                list.add(map);
            }
        });
        return list;
    }

    @Override
    public ApiBase getApi(String name, boolean reload) {
        return apiMap.get(name);
    }

    @Override
    public ApiResult<Object> run(String name, Map<String, Object> params, boolean reload) {
        if ((name == null) || name.isEmpty()) {
            return ApiResult.failed(NOT_API_NAME);
        }
        ApiBase api = this.getApi(name, false);
        return run(api, params, reload);
    }

    @Override
    @SuppressWarnings("unchecked")  // 屏蔽 unchecked 警告
    public ApiResult<Object> run(ApiBase api, Map<String, Object> params, boolean reload) {
        if (api == null) {
            return ApiResult.failed("method is null");
        }
        try {
            Object obj = ((MethodApi)api).run(params);
            if (obj instanceof ApiResult) {
                return (ApiResult<Object>) obj;
            }
            return ApiResult.success(obj, api.getLoaderFrom());
        }catch (Exception e) {
            String msg = e.getMessage();
            log.error("接口调用出错：{}", msg, e);
            return ApiResult.failed( api.getLoaderFrom() + ",error：" + msg);
        }
    }
}
