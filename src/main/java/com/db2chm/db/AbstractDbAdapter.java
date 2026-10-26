package com.db2chm.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Locale;
import com.db2chm.cli.CliFailure;

/**
 * 适配器抽象基类. 负责驱动加载与连接管理, 子类实现元数据查询.
 *
 * <p>连接方式: 用户填写的完整 JDBC 链接串 + 驱动类名直连.
 * 字符集仅对 MySQL 自动追加 characterEncoding 参数(若链接串中没有).</p>
 */
public abstract class AbstractDbAdapter implements DbAdapter {

    protected final ConnectionInfo info;
    protected Connection conn;
    private DriverLoader driverLoader;

    protected AbstractDbAdapter(ConnectionInfo info) {
        if (info == null) throw new IllegalArgumentException("ConnectionInfo is null");
        this.info = info;
    }

    @Override
    public void connect() throws Exception {
        JdbcPolicy.validate(info);
        driverLoader = new DriverLoader();
        try {
            this.conn = driverLoader.load(info).connect(info.getUrl(), JdbcPolicy.properties(info));
            if (conn == null) throw new CliFailure(4, "CONNECTION_FAILED", "The JDBC driver did not accept the connection.");
            try { conn.setReadOnly(true); } catch (java.sql.SQLException e) { throw new CliFailure(8, "READ_ONLY_REQUIRED", "The JDBC driver cannot establish the required read-only session."); }
            if (!conn.isReadOnly()) throw new CliFailure(8, "READ_ONLY_REQUIRED", "The JDBC driver did not establish a read-only connection.");
        } catch (Exception e) { close(); throw e; }
    }

    /**
     * MySQL 且用户设了字符集且链接串未带 characterEncoding 时自动追加.
     * 其余库: 字符集只影响导出文档编码, 不动链接串.
     */
    protected final PreparedStatement prepareMetadataQuery(String sql) throws java.sql.SQLException {
        String normalized = sql.trim().toUpperCase(Locale.ROOT);
        if (!normalized.startsWith("SELECT ") || sql.contains(";") || sql.contains("--") || sql.contains("/*")) throw new java.sql.SQLException("Only audited metadata SELECT statements are permitted.", "42000");
        PreparedStatement statement = conn.prepareStatement(sql);
        try { statement.setQueryTimeout(info.getQueryTimeout()); return statement; }
        catch (Exception e) { statement.close(); throw e; }
    }

    @Override
    public void close() {
        if (conn != null) {
            try { conn.close(); } catch (Exception ignored) { /* best-effort */ }
            conn = null;
        }
        if (driverLoader != null) { driverLoader.close(); driverLoader = null; }
    }

    @Override
    public DatabaseType getDatabaseType() {
        return info.getDbType();
    }

    /** 注释为空时的兜底. */
    protected static String safeComment(String c) {
        return c == null ? "" : c.trim();
    }

    /** JDBC 元数据数值列可能返回 Integer/BigDecimal 等, 统一转 Integer. */
    protected static Integer toInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(o.toString().trim()); } catch (Exception e) { return null; }
    }
}
