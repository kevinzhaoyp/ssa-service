package com.ssa.plugin.sqlApi;

/**
 * 原 类型
 * 1  execSql
 * 2  openSql
 * 3  execProc(不带返回结果)
 * 4  openProc(带返回结果集) dbname 参数
 * 41 openProc(执行存储过程，返回第一条记录对象)
 * 5  getList (使用getList sql 模板)返回结果集 data 为 JSON 数据 [],
 * 51 返回结果集的第一条 data 为 JSON 对象 {}
 * 6  执行updata,delete 等，返回 操作的行数
 * 7  getList 带分页 如果 ReturnType = 7 使用了分页查询 不能使用 order by 如果要加 增加  TOP 100 PERCENT
 *   其中分页中有固定的两参数，pageSize 每页显示的条数，minRN 最小行号 从0开始
 *   每条数据自动带两字段，rn 行号 tc=TotalCount
 * 11 调用Java接口
 *
 * [params] 说明，先不用JSON格式了，因为java 解析后key顺序会有变化，导致执行存储过程 参数顺序出错
 * 新模式如下： 参数名:类型,参数名2:类型  如： zcbfid:string,projectid:int
 * 如果带输出参数在类型前加  out_ 如zcbfid:out_string,projectid:out_int
 * 注：dbname 在 执行存储过程时为 关键字 参数名不能使用
 * params  和 paramsLen 可合并为一个，Opt 选项
 *
 * 11 Java接口 sqlText 说明
 *   sqlText为一个JSON对象
 *   {
 *   "package":"包名.类名",
 *
 *   }
 *   方法 写在参数中
 *   function
 *
 * 12 export接口，会导出一个文件流，目前只有excel
 * sqlText 说明  是一个JSON数组，一个对象为一个sheet,如果有其它文件的导出在考虑吧
 * [{"sheetname":"","sql":""},{"sheetname":"","sql":""}]
 * 计划，要让 支持多个list的输出
 * @author kevin
 */
public enum ReturnType {
    EXEC_PROC,      // 执行存储过程，无返回结果，如，update,delete等
    OPEN_PROC,      // 执行存储过程，返回一个对象，列表的第一条。
    OPEN_PROC_LIST, // 执行存储过程，返回一个列表
    LIST,           // 执行sql语句，返回列表
    PAGE_LIST,      // 执行sql语句，返回分页列表
    OBJECT,         // 执行sql语句，返回对象，列表第一条
    OBJECT_SUB,     // 执行sql语句，返回对象，支持主子表。

    UPDATE,         // 执行sql语句，一般为update,delete,返回影响的行数
    UPDATE_TABLE,   // 批量执行sql语句，原updateTable,现在合并进来
    JAVA_CLASS,     // 先不用
    ERROR           // 加载错误时，类型为Error直接返回 content 存放错误信息
}