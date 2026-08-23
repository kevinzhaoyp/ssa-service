package com.ssa.plugin.importApi.exec;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelReader;
import com.alibaba.excel.read.metadata.ReadSheet;
import com.jfinal.plugin.activerecord.DbPro;
import com.ssa.core.service.ApiBase;
import com.ssa.core.utils.ApiResult;
import com.ssa.plugin.importApi.model.ImportApi;
import com.ssa.plugin.sqlApi.ISqlApiExec;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartRequest;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ImportExec implements ISqlApiExec {
    /**
     * Controller 中的 request 下载文件时使用
     */
    private ServletWebRequest request;
    /**
     * fileApi实体对象
     */
    private ImportApi importApi;
    /**
     * 实际参数
     */
    private Map args = new HashMap<>(16);

    @Override
    public ApiResult<Object> exec(DbPro db, ApiBase api, Map<String, Object> params) {
        this.request = (ServletWebRequest) params.get("request");
        this.importApi = (ImportApi) api;
        // 移除request,response 后面没用了
        params.remove("request");
        params.remove("response");
        this.args = params;

        MultipartRequest multipartRequest = request.getNativeRequest(MultipartRequest.class);
        if (multipartRequest == null) {
            return ApiResult.failed("未发现上传的文件");
        }
        MultipartFile file = multipartRequest.getFile("file");
        if (file.isEmpty()) {
            return ApiResult.failed("未发现需要导入的文件");
        }

        // 开始上传
        try {
            ImportDataListener listener = new ImportDataListener(this.importApi, this.args, db);
            try (ExcelReader excelReader = EasyExcel.read(file.getInputStream(), listener).build()) {
                // 构建一个sheet 这里可以指定名字或者no
                int headRowNumber = this.importApi.getOptions().getHeadRowNumber();
                Object sheet = this.importApi.getOptions().getSheet();
                ReadSheet readSheet;
                if (sheet == null) {
                    readSheet =  EasyExcel.readSheet(0).headRowNumber(headRowNumber).build();
                } else if (sheet instanceof String) {
                    readSheet =  EasyExcel.readSheet((String) sheet).headRowNumber(headRowNumber).build();
                } else if (sheet instanceof Integer) {
                    readSheet =  EasyExcel.readSheet((Integer) sheet).headRowNumber(headRowNumber).build();
                } else {
                    readSheet =  EasyExcel.readSheet(0).headRowNumber(headRowNumber).build();
                }
                // 读取一个sheet
                excelReader.read(readSheet);
            }
            return ApiResult.success(listener.totalCount);
        } catch (IOException e) {
            e.printStackTrace();
            return ApiResult.failed(e.getMessage());
        }
    }
}
