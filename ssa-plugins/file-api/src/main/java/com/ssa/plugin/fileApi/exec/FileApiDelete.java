package com.ssa.plugin.fileApi.exec;

import com.jfinal.plugin.activerecord.DbPro;
import com.ssa.core.service.ApiBase;
import com.ssa.core.utils.ApiResult;
import com.ssa.plugin.fileApi.FileApiManager;
import com.ssa.plugin.fileApi.model.FileApi;
import com.ssa.plugin.sqlApi.ISqlApiExec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * FileApi 之 删除文件（事务安全版）
 * 语义（2026-08-22 优化）：content 一般为“删除数据库记录”的 SQL。
 *   1) 开启事务，先执行 content（删除数据）；
 *   2) 数据删除成功后，再删除物理文件；
 *   3) 若物理文件删除失败（文件存在但 f.delete() 返回 false / IOException），在事务内返回 false，
 *      触发事务回滚，撤销刚才的数据库删除，保证“数据库记录”与“物理文件”最终一致（要么都删，要么都不删）。
 *
 * <p>与 UPLOAD（先存文件后 content，SQL 失败回滚删文件）、DOWNLOAD（只读文件）并列。</p>
 *
 * <p>文件不存在：视为已删除（幂等），不触发回滚，事务正常提交（数据已删、文件本就不在，属合理终态）。</p>
 *
 * <p>注意：{@code options.onErrorGotoNext} 对 DELETE 已不再生效——
 * 文件删除失败一律回滚（事务安全语义），不再提供“跳过文件继续”的旁路。</p>
 *
 * @author kevin
 */
@Slf4j
public class FileApiDelete implements ISqlApiExec {

    @Override
    public ApiResult<Object> exec(DbPro db, ApiBase api, Map<String, Object> params) {
        FileApi fileApi = (FileApi) api;
        try {
            // 解析完整文件路径（与 DOWNLOAD 一致：path 模板含文件名）
            String path = FileApiManager.getPath(fileApi.getOptions().getPath(), params);
            File f = new File(path);

            boolean contentExecuted;
            boolean fileDeleted;

            if (StringUtils.hasText(fileApi.getContent())) {
                // 有 content：事务包裹“先删数据 + 后删文件”，文件删失败则整体回滚
                boolean txOk = db.tx(() -> {
                    try {
                        // 1) 先执行 content（删除数据库记录）
                        db.templateByString(fileApi.getContent(), params).update();
                        // 2) 后删物理文件（仅删常规文件，禁止误删目录）
                        File file = new File(path);
                        if (file.exists()) {
                            if (file.isDirectory()) {
                                log.error("目标路径是目录，拒绝删除以避免误删目录，事务回滚: {}", path);
                                return false; // 回滚数据删除
                            }
                            if (!file.delete()) {
                                log.error("物理文件删除失败，事务回滚: {}", path);
                                return false; // 回滚数据删除
                            }
                        }
                        // 文件不存在 -> 视为已删除，正常提交
                        return true;
                    } catch (Exception e) {
                        log.error("删除数据时异常，事务回滚: {}", path, e);
                        return false;
                    }
                });
                if (!txOk) {
                    return ApiResult.failed("删除失败（数据已回滚，物理文件保留）: " + path);
                }
                contentExecuted = true;
                fileDeleted = !new File(path).exists();
            } else {
                // 无 content：仅删物理文件，无需事务
                if (f.exists()) {
                    if (f.isDirectory()) {
                        return ApiResult.failed("目标路径是目录，拒绝删除以避免误删目录: " + path);
                    }
                    if (f.delete()) {
                        log.info("物理文件删除成功（无 content）: {}", path);
                        fileDeleted = true;
                    } else {
                        return ApiResult.failed("删除文件失败: " + path);
                    }
                } else {
                    log.info("删除文件时文件不存在（按已删除处理）: {}", path);
                    fileDeleted = true;
                }
                contentExecuted = false;
            }

            Map<String, Object> result = new LinkedHashMap<>(8);
            result.put("deleted", fileDeleted);
            result.put("path", f.getAbsolutePath().replace('\\', '/'));
            result.put("contentExecuted", contentExecuted);
            return ApiResult.success(result);
        } catch (RuntimeException e) {
            return ApiResult.failed("删除文件操作异常: " + e.getMessage());
        }
    }
}
