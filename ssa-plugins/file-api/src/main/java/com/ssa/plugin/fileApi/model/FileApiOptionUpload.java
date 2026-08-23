package com.ssa.plugin.fileApi.model;

import lombok.Data;

import java.util.List;

/**
 * 文件上传选项
 */
@Data
public class FileApiOptionUpload {
    /**
     * 文件上传的位置root,指定名称，会才主程序中的配置中获取
     * 支持enjoy模板，可动态替换路径中的变量
     * 如：D:/file/#(projectCode)/#(partID)/fj/
     * 会替换 projectCode partID
     * 如果以$开头，$()中的内容会替换为配置中设置的值
     */
    private String path = "/";
    /**
     * 文件名称，目前在上传时使用，如果指定了会以fileName为文件名称（含扩展名），如果未指定取上传文件的文件名。
     * 支持enjoy模板
     */
    private String fileName = null;
    /**
     * 最大支持上传文件数量 1 表示单文件，多文件时，如：2 表示最多上传两个文件
     */
    private int maxMultipartFile = 1;
    /**
     * 支持最大的文件大小，0 不限制，默认 2MB，单文件
     */
    private long maxSize = 2*1024*1024;
    /**
     * 是否压缩存储
     */
    private boolean zip = false;
    /**
     * 保存成功后，需要提取的数据，会增加到params中
     * "SIZE", "MD5", "createTime", "modifyTime"
     */
    private List<String> fileInfo;
}
