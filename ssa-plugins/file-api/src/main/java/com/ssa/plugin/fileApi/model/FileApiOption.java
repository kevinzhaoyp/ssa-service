package com.ssa.plugin.fileApi.model;

import com.ssa.plugin.fileApi.enums.FileOperate;
import lombok.Data;
import java.util.List;

/**
 * 文件API的选项 对应yml文件中的 options:
 * @author kevin
 */
@Data
public class FileApiOption {
    /**
     * 操作，upload,download
     */
    private FileOperate operate = FileOperate.UPLOAD;
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
     * 仅上传 是否支持同时上传多个文件，默认false
     */
    private boolean multipartFile = false;
    /**
     * 仅上传 支持最大的文件大小，0 不限制，默认 2MB
     */
    private long maxSize = 2*1024*1024;
    /**
     * 仅上传 多文件时 最大支持同时上传几个
     */
    private int maxFile = 5;
    /**
     * 是否压缩存储
     */
    private boolean zip = false;
    /**
     * 保存成功后，需要提取的数据，会增加到params中
     * "SIZE", "MD5", "createTime", "modifyTime"
     */
    private List<String> fileInfo;
    /**
     * 仅删除 删除物理文件遇到错误时，是否继续执行 content 中的 SQL。
     * 默认 true：文件删除失败（如文件不存在/无权限）只记 warn 日志并继续删除数据，不阻断后续业务；
     * 设为 false：文件删除失败则直接返回失败，不执行 content。
     * <p>注：因 ApiLoaderYml 始终按 FileApiOption 基类反序列化 options，
     * 该字段直接放在基类中才能被 yml 正确解析（早期独立的 FileApiOptionDel 会被当未知字段静默忽略）。
     */
    private boolean onErrorGotoNext = true;
}
