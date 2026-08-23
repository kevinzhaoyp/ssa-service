package com.ssa.oauth.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色实体类
 * 
 * @author ssa
 */
@Data
public class SysRole {

    private String roleId;

    private String roleName;

    private String roleCode;

    private String description;

    private Boolean status = true;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private List<SysPermission> permissions;
}