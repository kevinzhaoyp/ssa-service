package com.ssa.plugin.importApi.exec;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.util.ListUtils;
import com.jfinal.plugin.activerecord.DbPro;
import com.jfinal.plugin.activerecord.SqlPara;
import com.ssa.plugin.importApi.model.ImportApi;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class ImportDataListener extends AnalysisEventListener<Map<String, Object>> {
    public int totalCount;
    /**
     * 每隔BATCH_COUNT条存储数据库，实际使用中可以100条，然后清理list ，方便内存回收
     */
    private int batchCount;
    /**
     * 缓存数据，最大为batchCount
     */
    private List<Map<String, Object>> cachedDataList;
    /**
     *
     */
    private ImportApi importApi;
    private Map args;
    private DbPro db;
    /**
     *
     * @param importApi
     */
    public ImportDataListener(ImportApi importApi, Map args, DbPro db) {
        this.importApi = importApi;
        this.args = args;
        this.db = db;
        this.batchCount = importApi.getOptions().getBatchCount();
        this.cachedDataList = ListUtils.newArrayListWithExpectedSize(batchCount);
    }

    @Override
    public void onException(Exception exception, AnalysisContext context) throws Exception {
        super.onException(exception, context);
    }

    @Override
    public void invoke(Map<String, Object> integerStringMap, AnalysisContext analysisContext) {
        totalCount++;
//        log.info("解析到一条数据:{}", JSON.toJSONString(integerStringMap));
        Map<String, Object> map = new HashMap();
        importApi.getOptions().getColumns().forEach(column -> {
            map.put(column.getField(), integerStringMap.get(column.getIndex()));
        });
        cachedDataList.add(map);
        if (cachedDataList.size() >= batchCount) {
            saveData();
            cachedDataList = ListUtils.newArrayListWithExpectedSize(batchCount);
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {
        saveData();
        log.info("所有数据解析完成！");
    }

    /**
     * 存储数据库
     */
    private void saveData() {
        log.info("{}条数据，开始存储数据库！", cachedDataList.size());
        this.args.put("data", cachedDataList);
        SqlPara sqlPara = this.db.templateByString(this.importApi.getContent(), this.args).getSqlPara();
        this.db.update(sqlPara);
        log.info("存储数据库成功！");
    }

}
