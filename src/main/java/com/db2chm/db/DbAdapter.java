package com.db2chm.db;

import java.util.List;

/**
 * 数据库适配器统一接口.
 *
 * <p>每种数据库(Oracle/MySQL/达梦/vastbase/人大金仓)给出一个实现, 屏蔽方言差异.
 * GUI 与生成器只依赖该接口.</p>
 */
public interface DbAdapter {

    /**
     * 打开连接. 失败抛 RuntimeException.
     */
    void connect() throws Exception;

    /**
     * 关闭连接.
     */
    void close();

    /**
     * 拉取当前 schema/库 下所有"基础表"列表. 不含视图.
     */
    List<TableInfo> listTables() throws Exception;

    /**
     * 拉取单张表的完整列信息(含主键/自增/注释).
     */
    TableInfo getTableColumns(String tableName) throws Exception;

    /**
     * 该数据库类型. 便于生成器在 CHM 封面展示.
     */
    DatabaseType getDatabaseType();
}