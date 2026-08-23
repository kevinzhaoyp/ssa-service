package com.ssa.core.service;

/**
 * 当前登录用户线程上下文
 * <p>
 * 基于 {@link ThreadLocal} 在请求线程内透传 {@link CurrentUser}，
 * 供 sql-api 的 {@code SqlApiManager} 注入到 {@code params} 的 {@code cur_user} 对象。
 * <p>
 * <b>注意：</b>请求处理完成后（通常在过滤器 finally 中）必须调用 {@link #clear()}，
 * 使用 {@code remove()} 而非 {@code set(null)}，避免线程池复用导致用户信息串号与内存泄漏。
 *
 * @author ssa
 */
public class UserContext {

    private static final ThreadLocal<CurrentUser> CURRENT_USER = new ThreadLocal<>();

    /**
     * 设置当前登录用户
     *
     * @param user 当前登录用户
     */
    public static void set(CurrentUser user) {
        CURRENT_USER.set(user);
    }

    /**
     * 获取当前登录用户（未设置时返回 null）
     *
     * @return 当前登录用户，可能为 null
     */
    public static CurrentUser get() {
        return CURRENT_USER.get();
    }

    /**
     * 清理当前线程的用户上下文，防止内存泄漏与线程复用串号
     */
    public static void clear() {
        CURRENT_USER.remove();
    }
}
