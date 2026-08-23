package com.ssa.plugin.fileApi.exec;

import com.jfinal.plugin.activerecord.DbPro;
import com.ssa.core.service.ApiBase;
import com.ssa.core.utils.ApiResult;
import com.ssa.plugin.fileApi.FileApiManager;
import com.ssa.plugin.fileApi.model.FileApi;
import com.ssa.plugin.sqlApi.ISqlApiExec;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.URLEncoder;
import java.util.Map;
/**
 * FileApi 之 文件下载
 * 内置关键字 fileName
 * 参数如果传fileName，下载时会以fileName名名称
 * fileName支持从onBefore中获取
 * @author kevin
 */
@Slf4j
public class FileApiDownload implements ISqlApiExec {
    /**
     * 下载时使用，固定参数，如果有，下载时使用FILENAME做文件名称
     */
    private static final String FILENAME = "fileName";

    /** 流式拷贝缓冲区大小：32KB，避免大文件整块载入 JVM 堆 */
    private static final int COPY_BUFFER_SIZE = 32 * 1024;

    @Override
    public ApiResult<Object> exec(DbPro db, ApiBase api, Map<String, Object> params) {
        HttpServletResponse response = (HttpServletResponse) params.get("response");
        FileApi fileApi = (FileApi) api;
        // 下载文件
        String fileName = FileApiManager.getPath(fileApi.getOptions().getPath(), params);
        File f = new File(fileName);
        if (!f.exists()) {
            return ApiResult.failed("File Not Exists " + fileName);
        }
        try {
            response.reset();
            response.setContentType("application/octet-stream; charset=utf-8");
            response.setCharacterEncoding("utf-8");
            if (params.get(FILENAME) != null) {
                // 从参数中获取文件名称
                String fileTitle = params.get(FILENAME).toString();
                response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileTitle, "utf-8"));
            }
            // 加这个长度,解决部分文件下载的不了,PDF打不开的问题
            response.setHeader("Content-Length", f.length() + "");
            // 流式拷贝：固定缓冲区循环读写，避免整文件读入 JVM 堆（防 OOM / int 越界 / 截断）
            try (InputStream in = new BufferedInputStream(new FileInputStream(f))) {
                OutputStream out = new BufferedOutputStream(response.getOutputStream());
                byte[] buf = new byte[COPY_BUFFER_SIZE];
                int len;
                while ((len = in.read(buf)) != -1) {
                    out.write(buf, 0, len);
                }
                out.flush();
                return ApiResult.success("ok");
            } catch (IOException ex) {
                log.error("文件下载失败: {}", fileName, ex);
                return ApiResult.failed("downLoad Error: " + ex.getMessage());
            }
        } catch (Exception e) {
            return ApiResult.failed("downLoad Error: " + e.getMessage());
        }
    }
}
