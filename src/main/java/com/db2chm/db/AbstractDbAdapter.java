package com.db2chm.db;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * 适配器抽象基类. 负责驱动加载与连接管理, 子类实现元数据查询.
 *
 * <p>连接方式: 用户填写的完整 JDBC 链接串 + 驱动类名直连.
 * 字符集仅对 MySQL 自动追加 characterEncoding 参数(若链接串中没有).</p>
 */
public abstract class AbstractDbAdapter implements DbAdapter {

    protected final ConnectionInfo info;
    protected Connection conn;

    protected AbstractDbAdapter(ConnectionInfo info) {
        if (info == null) throw new IllegalArgumentException("ConnectionInfo is null");
        this.info = info;
    }

    @Override
    public void connect() throws Exception {
        String driver = info.getDriverClass();
        if (driver == null || driver.isEmpty()) {
            throw new IllegalStateException("未指定 JDBC 驱动类");
        }
        String url = info.getUrl();
        if (url == null || url.isEmpty()) {
            throw new IllegalStateException("未填写 JDBC 链接串");
        }
        Class.forName(driver);
        this.conn = DriverManager.getConnection(applyCharset(url), info.getUser(), info.getPassword());
    }

    /**
     * MySQL 且用户设了字符集且链接串未带 characterEncoding 时自动追加.
     * 其余库: 字符集只影响导出文档编码, 不动链接串.
     */
    private String applyCharset(String url) {
        if (info.getDbType() != DatabaseType.MYSQL) return url;
        if (url.contains("characterEncoding=")) return url;
        String cs = info.getCharset();
        if (cs == null || cs.isEmpty()) return url;
        // MySQL 驱动的 characterEncoding 取 utf8/gbk 这类值, UTF-8 也兼容
        String v = "UTF-8".equalsIgnoreCase(cs) ? "utf8" : cs;
        return url + (url.contains("?") ? "&" : "?") + "characterEncoding=" + v;
    }

    @Override
    public void close() {
        if (conn != null) {
            try { conn.close(); } catch (Exception ignored) { /* best-effort */ }
            conn = null;
        }
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
