package com.ssa.core.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Slf4j
public final class ApiUtils {
    private ApiUtils() {}
    /**
     * 有很多地方判断了，也不想引用太多三方包，所以就简单写一个
     * @param str 字符串
     * @return 是否为空
     */
    public static boolean strIsEmpty(String str) {
        return (str == null) || str.isEmpty();
    }
    /**
     * 获取类名，并将第一个字母小写 如： MethodApiManager 结果为： methodApiManager
     * @param c 类
     * @return 返回类名 首字母小写
     * 有 StringUtils.uncapitalize 这无意我还自己写一个干啥。。。。。
     * char[] charName = c.getSimpleName().toCharArray();
     * // 首字母小写方法，大写会变成小写，如果小写首字母会消失,暂不考虑不规范的类名
     * charName[0] += 32;
     */
    @Deprecated
    public static String getClassSimpleName(Class<?> c) {
        return StringUtils.uncapitalize(c.getSimpleName());
    }

    /**
     * 判断是否为空
     * @param value Object值
     * @return boolean
     */
    public static boolean isNullOrEmpty(Object value) {
        return Objects.isNull(value) || (value instanceof String && ((String) value).isEmpty());
    }
}
