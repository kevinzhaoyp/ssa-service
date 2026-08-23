package com.ssa.plugin.fileApi.exec;

import com.jfinal.plugin.activerecord.DbPro;
import com.ssa.core.service.ApiBase;
import com.ssa.core.utils.ApiResult;
import com.ssa.plugin.fileApi.FileApiManager;
import com.ssa.plugin.fileApi.model.FileApi;
import com.ssa.plugin.sqlApi.ISqlApiExec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartRequest;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FileApi执行工具
 * 上传：先保存文件，在操作数据库。数据库操作失败时，删除文件。
 * 下载：
 * @author kevin
 */
@Slf4j
public class FileApiUpload implements ISqlApiExec {
    /**
     * fileApi实体对象
     */
    private FileApi fileApi;
    /**
     * 实际参数
     */
    private Map args = new HashMap<>(16);

    /**
     * 将文件信息放到 info中.key=fineInfo
     * @param file 原File文件
     * @param info 原Map会写到这里
     * @return
     */
    private boolean setFileInfo(File file, Map info) {
        // 需要获取的文件信息
        List<String> items = fileApi.getOptions().getFileInfo();
        if ((items == null) || (items.isEmpty())) {
            return true;
        }
        Map fileInfo = new HashMap<>(16);
        if (!file.isFile()) {
            fileInfo.put("errorMsg", "不是有效的文件");
            return false;
        }
        for (String item : items) {
            if (item.equalsIgnoreCase("size")) {
                fileInfo.put(item, file.length());
            } else if (item.equalsIgnoreCase("md5")) {
//                try {
                    fileInfo.put(item, "暂不支持");//DigestUtils.md5Hex(new FileInputStream(file))
//                } catch (IOException e) {
//                    fileInfo.put("md5", "md5 error");
//                    e.printStackTrace();
//                }
            } else if (item.equalsIgnoreCase("lastModified")) {
                fileInfo.put(item, file.lastModified());
            } else if (item.equalsIgnoreCase("ext")) {
                fileInfo.put(item, file.getName().substring(file.getName().lastIndexOf(".")));
            } else {
                fileInfo.put(item, "未实现");
            }
        }
        info.put("fileInfo", fileInfo);
        return true;
    }

    /**
     * 上传文件到 options.path，成功返回目标 File，失败返回 null。
     * 同时把 storageName / fileSize / originalFilename 写入 args，供 content SQL 使用。
     */
    private File uploadFile(MultipartFile file) {
        String storeFileName = fileApi.getOptions().getPath();
        if (StringUtils.hasText(fileApi.getOptions().getFileName())) {
            storeFileName += fileApi.getOptions().getFileName();
        } else {
            storeFileName += file.getOriginalFilename();
        }
        storeFileName = FileApiManager.getPath(storeFileName, args);
        File destFile = new File(storeFileName);
        if (destFile.isDirectory()) {
            throw new RuntimeException("上传的目标文件不正确:" + storeFileName);
        }
        File fileDir = destFile.getParentFile();
        if (fileDir != null && !fileDir.isFile() && !fileDir.exists()) {
            fileDir.mkdirs();
        }
        try {
            file.transferTo(destFile);
            args.put("originalFilename", file.getOriginalFilename());
            args.put("storageName", destFile.getName());
            args.put("storagePath", destFile.getAbsolutePath().replace('\\', '/'));
            args.put("fileSize", destFile.length());
            setFileInfo(destFile, args);
            // 扁平化 fileInfo，便于 content 中直接 #para(size)
            Object fi = args.get("fileInfo");
            if (fi instanceof Map) {
                ((Map<?, ?>) fi).forEach((k, v) -> {
                    if (k != null && !args.containsKey(String.valueOf(k))) {
                        args.put(String.valueOf(k), v);
                    }
                });
            }
            return destFile;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public ApiResult exec(DbPro db, ApiBase api, Map params) {
        ServletWebRequest request = (ServletWebRequest) params.get("request");
        this.fileApi = (FileApi) api;
        this.args = params;

        MultipartRequest multipartRequest = request.getNativeRequest(MultipartRequest.class);
        if (multipartRequest == null) {
            return ApiResult.failed("未发现上传的文件");
        }
        List<MultipartFile> files = multipartRequest.getFiles("file");
        if (files.isEmpty()) {
            return ApiResult.failed("未发现上传的文件");
        }
        if (!fileApi.getOptions().isMultipartFile() && files.size() > 1) {
            return ApiResult.failed("只能上传一个文件");
        }
        Map result = new HashMap(16);
        for (MultipartFile file : files) {
            File destFile = null;
            try {
                destFile = uploadFile(file);
            } catch (RuntimeException e) {
                result.put(file.getOriginalFilename(), e.getMessage());
                continue;
            }
            if (destFile == null) {
                result.put(file.getOriginalFilename(), "upload failed");
                continue;
            }
            try {
                if (StringUtils.hasText(((FileApi) api).getContent())) {
                    boolean txOk = db.tx(() -> {
                        try {
                            db.templateByString(((FileApi) api).getContent(), args).update();
                            return true;
                        } catch (Exception e) {
                            log.error("上传底稿事务执行失败", e);
                            return false;
                        }
                    });
                    if (!txOk) {
                        throw new RuntimeException("上传底稿事务失败（含日志写入）");
                    }
                }
                Map one = new HashMap(8);
                one.put("ok", true);
                one.put("id", args.get("id"));
                one.put("storageName", args.get("storageName"));
                one.put("fileSize", args.get("fileSize"));
                result.put(file.getOriginalFilename(), one);
            } catch (Exception e) {
                e.printStackTrace();
                if (destFile.exists() && !destFile.delete()) {
                    System.err.println("SQL 失败后删除文件失败: " + destFile.getAbsolutePath());
                }
                result.put(file.getOriginalFilename(), e.getMessage());
            }
        }
        return ApiResult.success(result);
    }

}
