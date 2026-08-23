package com.ssa.core.service;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 当前登录用户上下文对象
 * <p>
 * 用于在请求线程内透传当前登录用户的关键信息，便于 sql-api 的 YML 模板与事件
 * 通过 {@code params} 中的 {@code cur_user} 对象按需取用（不强制、不覆盖业务参数）。
 *
 * @author ssa
 */
@Data
public class CurrentUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID（sys_user.user_id）
     */
    private String userId;

    /**
     * 登录用户名（sys_user.username）
     */
    private String userName;

    /**
     * 真实姓名（sys_user.real_name）
     */
    private String realName;
}
