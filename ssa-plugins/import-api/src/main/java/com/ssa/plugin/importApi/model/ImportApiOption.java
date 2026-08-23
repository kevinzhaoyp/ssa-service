package com.ssa.plugin.importApi.model;

import com.ssa.plugin.importApi.enums.ImportApiOperate;
import lombok.Data;

import java.util.List;

import static com.ssa.plugin.importApi.enums.ImportApiOperate.IMPORT;

@Data
public class ImportApiOption {
    /**
     * 操作，IMPORT 导入 EXPORT 导出
     */
    private ImportApiOperate operate = IMPORT;
    /**
     * 默认批量执行数，默认每100条执行一次。大数据量时有用。
     */
    private Integer batchCount = 100;

    private Boolean transaction = true;
    /**
     * 导入的sheet，如果是excel文件，可以是sheet的序号，也可以是sheet的名称
     */
    private Object sheet;
    /**
     * 头行行号，从1开始
     */
    private Integer headRowNumber = 1;
    /**
     * 是否使用模板
     */
    private Boolean useTemplate = false;
    /**
     * 模板文件，只有useTemplate为true时生效
     */
    private String templateFile;
    /**
     * 导入列
     */
    private List<ImportApiColumn> columns;

}
