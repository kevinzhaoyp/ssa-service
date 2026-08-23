package com.ssa.plugin.importApi.model;

import lombok.Data;

@Data
public class ImportApiColumn {
    /**
     * 字段名
     */
    private String field;
    /**
     * 字段类型
     */
    private String type = "string";
    /**
     * 对应excel列索引
     */
    private Integer index;
    /**
     * 对应excel列标题
     */
    private String excelTitle;
}
