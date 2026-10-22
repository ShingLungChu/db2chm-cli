package com.db2chm.db;

import java.util.HashMap;
import java.util.Map;

/**
 * 支持的数据库类型枚举.
 *
 * <p>每个枚举项携带默认驱动类名与示例链接串, 供 GUI 预填.</p>
 */
public enum DatabaseType {

    ORACLE("Oracle", "oracle.jdbc.OracleDriver", 1521,
            "jdbc:oracle:thin:@localhost:1521/ORCLPDB1"),

    MYSQL("MySQL", "com.mysql.cj.jdbc.Driver", 3306,
            "jdbc:mysql://127.0.0.1:3306/mydb?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai"),

    DM("达梦 DM", "dm.jdbc.driver.DmDriver", 5236,
            "jdbc:dm://localhost:5236"),

    VASTBASE("Vastbase (PG协议)", "org.postgresql.Driver", 5432,
            "jdbc:postgresql://localhost:5432/example?currentSchema=public"),

    KINGBASE("人大金仓 KingbaseES", "com.kingbase8.Driver", 54321,
            "jdbc:kingbase8://127.0.0.1:54321/mydb?currentSchema=public");

    private final String displayName;
    private final String driverClass;
    private final int defaultPort;
    private final String exampleUrl;

    DatabaseType(String displayName, String driverClass, int defaultPort, String exampleUrl) {
        this.displayName = displayName;
        this.driverClass = driverClass;
        this.defaultPort = defaultPort;
        this.exampleUrl = exampleUrl;
    }

    public String displayName() { return displayName; }
    public String driverClass() { return driverClass; }
    public int defaultPort() { return defaultPort; }
    public String exampleUrl() { return exampleUrl; }

    private static final Map<String, DatabaseType> NAME_MAP = new HashMap<>();
    static {
        for (DatabaseType t : values()) {
            NAME_MAP.put(t.name(), t);
            NAME_MAP.put(t.displayName, t);
        }
    }

    /** 按枚举名或显示名查找, 找不到返回 null. */
    public static DatabaseType byName(String name) {
        return name == null ? null : NAME_MAP.get(name.trim());
    }
}
