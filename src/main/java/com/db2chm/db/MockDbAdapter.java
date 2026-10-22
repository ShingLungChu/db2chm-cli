package com.db2chm.db;

import java.util.ArrayList;
import java.util.List;

/**
 * 挡板(Mock)数据源 —— 不连接任何数据库, 在内存中生成确定性的模拟表结构.
 *
 * <p>用途: 无数据库环境下验证「元数据 → HTML 渲染 → CHM 编译」全链路,
 * 以及回归测试(HTML 转义 / 复合主键 / 自增 / 中文注释等边界场景)。</p>
 *
 * <p>生成的表带业务语义(SYS_USER / BIZ_ORDER 等), 刻意覆盖:
 * <ul>
 *   <li>复合主键(2列, 验证 PK(1)/PK(2) 展示)</li>
 *   <li>自增列(验证 Y 标记)</li>
 *   <li>注释含 &lt; &gt; &amp; 特殊字符(验证 HTML 转义)</li>
 *   <li>允许空 / 不允许空两种情况</li>
 *   <li>各数据库方言的数据类型(Oracle:VARCHAR2 / MySQL:varchar / PG系:varchar)</li>
 * </ul></p>
 */
public class MockDbAdapter implements DbAdapter {

    /** 模拟业务表模板(名称前缀, 表说明, 模板序号). */
    private static final String[][] TABLE_TEMPLATES = {
            {"SYS_USER",           "系统用户表"},
            {"SYS_ROLE",           "角色表"},
            {"SYS_USER_ROLE",      "用户角色关联表(复合主键)"},
            {"BIZ_CUSTOMER",       "客户信息表"},
            {"BIZ_ORDER",          "订单主表"},
            {"BIZ_ORDER_ITEM",     "订单明细表(复合主键)"},
            {"BIZ_PAY_FLOW",       "支付流水表(自增主键)"},
            {"SYS_DICT",           "数据字典表"},
            {"AUDIT_LOG",          "审计日志表(注释含特殊字符)"},
            {"BIZ_REFUND",         "退款记录表"},
    };

    private final ConnectionInfo info;
    private final List<TableInfo> tables = new ArrayList<>();

    public MockDbAdapter(ConnectionInfo info, int tableCount) {
        this.info = info == null ? new ConnectionInfo() : info;
        int n = tableCount > 0 ? tableCount : TABLE_TEMPLATES.length;
        buildTables(n);
    }

    // ------------------------------------------------------------------

    @Override
    public void connect() {
        // 挡板: 无连接动作
    }

    @Override
    public void close() {
        // 挡板: 无关闭动作
    }

    @Override
    public List<TableInfo> listTables() {
        return new ArrayList<>(tables);
    }

    @Override
    public TableInfo getTableColumns(String tableName) {
        for (TableInfo t : tables) {
            if (t.getName().equalsIgnoreCase(tableName)) return t;
        }
        return null;
    }

    @Override
    public DatabaseType getDatabaseType() {
        return info.getDbType();
    }

    // ------------------------------------------------------------------
    // 模拟数据构造
    // ------------------------------------------------------------------

    private void buildTables(int n) {
        DatabaseType type = info.getDbType() == null ? DatabaseType.ORACLE : info.getDbType();
        for (int i = 0; i < n; i++) {
            String[] tpl = TABLE_TEMPLATES[i % TABLE_TEMPLATES.length];
            int round = i / TABLE_TEMPLATES.length; // 超过模板数后加后缀 _2/_3
            String name = tpl[0] + (round > 0 ? "_" + (round + 1) : "");
            String comment = tpl[1] + (round > 0 ? "(第" + (round + 1) + "批模拟)" : "");
            tables.add(buildTable(name, comment, type));
        }
    }

    private TableInfo buildTable(String name, String comment, DatabaseType type) {
        TableInfo t = new TableInfo(name, comment);
        int c = 1;
        switch (name) {
            case "SYS_USER":
                t.addColumn(col(c++, "USER_ID", typeId(type), "18", true, 1, type != DatabaseType.MYSQL, false, "用户ID, 主键"));
                t.addColumn(col(c++, "USER_NAME", typeStr(type), "64", false, 0, false, false, "登录账号, 唯一"));
                t.addColumn(col(c++, "REAL_NAME", typeStr(type), "64", false, 0, false, true, "真实姓名"));
                t.addColumn(col(c++, "PWD_HASH", typeStr(type), "128", false, 0, false, false, "密码散列值(SHA-256)"));
                t.addColumn(col(c++, "EMAIL", typeStr(type), "128", false, 0, false, true, "电子邮箱"));
                t.addColumn(col(c++, "STATUS", typeNum(type), "2", false, 0, false, false, "状态: 1-启用 0-停用"));
                t.addColumn(col(c++, "LAST_LOGIN", typeTime(type), null, false, 0, false, true, "最后登录时间"));
                t.addColumn(col(c++, "REMARK", typeText(type), null, false, 0, false, true, "备注"));
                break;
            case "SYS_USER_ROLE":
            case "BIZ_ORDER_ITEM":
                // 复合主键样本
                boolean isOrderItem = name.equals("BIZ_ORDER_ITEM");
                t.addColumn(col(c++, isOrderItem ? "ORDER_ID" : "USER_ID", typeNum(type), "18", true, 1, false, false, isOrderItem ? "订单ID(联合主键1)" : "用户ID(联合主键1)"));
                t.addColumn(col(c++, isOrderItem ? "ITEM_NO" : "ROLE_ID", typeNum(type), "18", true, 2, false, false, isOrderItem ? "明细行号(联合主键2)" : "角色ID(联合主键2)"));
                t.addColumn(col(c++, isOrderItem ? "PRODUCT_CODE" : "GRANTED_BY", typeStr(type), "32", false, 0, false, false, isOrderItem ? "商品编码" : "授权人"));
                t.addColumn(col(c++, isOrderItem ? "QUANTITY" : "GRANT_TIME", isOrderItem ? typeNum(type) : typeTime(type), isOrderItem ? "18,2" : null, false, 0, false, isOrderItem, isOrderItem ? "数量" : "授权时间"));
                t.addColumn(col(c++, isOrderItem ? "AMOUNT" : "EXPIRE_TIME", typeNum(type), "18,2", false, 0, false, true, isOrderItem ? "金额(元)" : "过期时间(时间戳数值)"));
                break;
            case "BIZ_PAY_FLOW":
                t.addColumn(col(c++, "FLOW_ID", typeNum(type), "18", true, 1, true, false, "流水ID, 自增主键"));
                t.addColumn(col(c++, "BIZ_NO", typeStr(type), "64", false, 0, false, false, "业务单号"));
                t.addColumn(col(c++, "CHANNEL", typeStr(type), "16", false, 0, false, false, "支付渠道: WECHAT/ALIPAY/BANK"));
                t.addColumn(col(c++, "AMOUNT", typeNum(type), "18,2", false, 0, false, false, "交易金额(元)"));
                t.addColumn(col(c++, "TRADE_TIME", typeTime(type), null, false, 0, false, false, "交易时间"));
                t.addColumn(col(c++, "STATE", typeNum(type), "2", false, 0, false, false, "状态: 0-处理中 1-成功 2-失败"));
                break;
            case "AUDIT_LOG":
                t.addColumn(col(c++, "LOG_ID", typeNum(type), "18", true, 1, type != DatabaseType.MYSQL, false, "日志ID"));
                t.addColumn(col(c++, "OPERATOR", typeStr(type), "64", false, 0, false, false, "操作人"));
                t.addColumn(col(c++, "ACTION", typeStr(type), "128", false, 0, false, false, "操作动作"));
                t.addColumn(col(c++, "DETAIL", typeText(type), null, false, 0, false, true, "详情, 注释含特殊字符: 金额<0 时报警 & 状态>2 记堆栈"));
                t.addColumn(col(c++, "CREATED", typeTime(type), null, false, 0, false, false, "记录时间"));
                break;
            default:
                // 通用表
                t.addColumn(col(c++, "ID", typeId(type), "18", true, 1, type == DatabaseType.MYSQL, false, "主键ID"));
                t.addColumn(col(c++, "CODE", typeStr(type), "32", false, 0, false, false, "业务编码"));
                t.addColumn(col(c++, "NAME_CN", typeStr(type), "128", false, 0, false, false, "中文名称"));
                t.addColumn(col(c++, "VALUE", typeNum(type), "18,2", false, 0, false, true, "数值(允许为空)"));
                t.addColumn(col(c++, "DESCRIPTION", typeText(type), null, false, 0, false, true, "描述说明"));
                t.addColumn(col(c++, "CREATED_AT", typeTime(type), null, false, 0, false, true, "创建时间"));
                t.addColumn(col(c++, "UPDATED_AT", typeTime(type), null, false, 0, false, true, "更新时间"));
                break;
        }
        return t;
    }

    private static ColumnInfo col(int seq, String name, String dataType, String length,
                                  boolean pk, int pkSeq, boolean autoInc,
                                  boolean nullable, String comment) {
        return new ColumnInfo(seq, name, dataType, length, pk, pkSeq, autoInc, nullable, comment);
    }

    // ---------------- 方言化的数据类型 ----------------

    /** 整型主键类型: Oracle/DM→NUMBER, MySQL→bigint, PG系→int8. */
    private static String typeId(DatabaseType t) {
        switch (t) {
            case MYSQL:              return "bigint";
            case VASTBASE:
            case KINGBASE:           return "int8";
            default:                 return "NUMBER";
        }
    }

    /** 数值类型: Oracle/DM→NUMBER, MySQL→decimal, PG系→numeric. */
    private static String typeNum(DatabaseType t) {
        switch (t) {
            case MYSQL:              return "decimal";
            case VASTBASE:
            case KINGBASE:           return "numeric";
            default:                 return "NUMBER";
        }
    }

    /** 字符串类型: Oracle/DM→VARCHAR2, MySQL→varchar, PG系→varchar. */
    private static String typeStr(DatabaseType t) {
        switch (t) {
            case MYSQL:
            case VASTBASE:
            case KINGBASE:           return "varchar";
            default:                 return "VARCHAR2";
        }
    }

    /** 时间类型: Oracle/DM→TIMESTAMP, MySQL→datetime, PG系→timestamp. */
    private static String typeTime(DatabaseType t) {
        switch (t) {
            case MYSQL:              return "datetime";
            default:                 return "TIMESTAMP";
        }
    }

    /** 大文本类型: Oracle/DM→CLOB, MySQL→text, PG系→text. */
    private static String typeText(DatabaseType t) {
        switch (t) {
            case ORACLE:
            case DM:                 return "CLOB";
            default:                 return "text";
        }
    }
}
