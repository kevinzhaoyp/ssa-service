package com.ssa.oauth.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限实体类
 * 
 * @author ssa
 */
@Data
public class SysPermission {

    private String permissionId;

    private String permissionName;

    private String permissionCode;

    private String permissionType;

    private String uri;

    private String parentId;

    private Integer orderNum = 0;

    private Boolean visible = true;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}