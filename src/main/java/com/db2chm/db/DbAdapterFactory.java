package com.db2chm.db;

/**
 * 适配器工厂. 直接 new 对应实现, 也可后续通过 SPI 扩展.
 */
public final class DbAdapterFactory {

    private DbAdapterFactory() {}

    public static DbAdapter create(DatabaseType type, ConnectionInfo info) {
        switch (type) {
            case ORACLE:   return new OracleAdapter(info);
            case MYSQL:    return new MysqlAdapter(info);
            case DM:       return new DmAdapter(info);
            case VASTBASE: return new VastbaseAdapter(info);
            case KINGBASE: return new KingbaseAdapter(info);
            default:
                throw new IllegalArgumentException("Unsupported database type: " + type);
        }
    }
}