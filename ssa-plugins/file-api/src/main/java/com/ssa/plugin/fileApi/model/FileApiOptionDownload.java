package com.ssa.plugin.fileApi.model;

import lombok.Data;

import java.util.List;

@Data
public class FileApiOptionDownload {
    /**
     * 文件上传的位置root,指定名称，会才主程序中的配置中获取
     * 支持enjoy模板，可动态替换路径中的变量
     * 如：D:/file/#(projectCode)/#(partID)/fj/
     * 会替换 projectCode partID
     * 如果以$开头，$()中的内容会替换为配置中设置的值
     */
    private String path = "/";
    /**
     * 是否压缩,先压缩，后下载
     */
    private boolean zip = false;
}
