package com.db2chm.db;

/**
 * 数据库连接信息. 字段直接对应 GUI 精简表单:
 * <pre>
 *   数据库类型 + 驱动类 + 链接串 + 用户名 + 密码 + 字符集
 * </pre>
 *
 * <p>schema 不再由界面输入, 按类型自动推导:
 * <ul>
 *   <li>Oracle / 达梦: 默认取登录用户的默认 Schema (OWNER = USER)</li>
 *   <li>MySQL: 取链接串里的库名</li>
 *   <li>vastbase / 金仓: 取链接串 currentSchema 参数, 缺省 public</li>
 * </ul></p>
 */
public class ConnectionInfo {

    private DatabaseType dbType;
    private String driverClass;  // JDBC 驱动类名
    private String url;          // 完整 JDBC 链接串
    private String user;
    private String password;
    private String charset;      // 输出文档编码: UTF-8 / GBK / GB2312

    /** 可选: 手工指定 schema(不填则自动推导). */
    private String schema;

    public ConnectionInfo() {}

    public DatabaseType getDbType() { return dbType; }
    public void setDbType(DatabaseType dbType) { this.dbType = dbType; }

    public String getDriverClass() {
        if (driverClass == null || driverClass.trim().isEmpty()) {
            return dbType == null ? null : dbType.driverClass();
        }
        return driverClass.trim();
    }
    public void setDriverClass(String driverClass) { this.driverClass = driverClass; }

    public String getUrl() { return url == null ? "" : url.trim(); }
    public void setUrl(String url) { this.url = url; }

    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getCharset() {
        return (charset == null || charset.trim().isEmpty()) ? "UTF-8" : charset.trim();
    }
    public void setCharset(String charset) { this.charset = charset; }

    public void setSchema(String schema) { this.schema = schema; }

    /**
     * 推导要导出的 schema:
     * Oracle/DM → null(用登录用户); MySQL → 链接串库名; PG系 → currentSchema 或 public.
     */
    public String getSchema() {
        if (schema != null && !schema.trim().isEmpty()) return schema.trim();
        if (dbType == null) return null;
        String u = getUrl();
        switch (dbType) {
            case MYSQL:
                // jdbc:mysql://host:port/dbname?params
                int slash = u.indexOf('/', u.indexOf("//") + 2);
                if (slash < 0) return null;
                String rest = u.substring(slash + 1);
                int q = rest.indexOf('?');
                return (q < 0 ? rest : rest.substring(0, q)).trim();
            case VASTBASE:
            case KINGBASE:
                int cs = u.indexOf("currentSchema=");
                if (cs >= 0) {
                    String v = u.substring(cs + "currentSchema=".length());
                    int end = v.indexOf('&');
                    if (end >= 0) v = v.substring(0, end);
                    return v.trim();
                }
                return "public";
            default:
                return null; // Oracle / DM → OWNER = USER
        }
    }

    /** 从链接串提取展示用的库名(封面/标题用). */
    public String getDisplayName() {
        String u = getUrl();
        if (u.isEmpty()) return user == null ? "" : user;
        // 取 //host:port/ 后面的第一段
        int p = u.indexOf("//");
        if (p >= 0) {
            int slash = u.indexOf('/', p + 2);
            if (slash >= 0) {
                String rest = u.substring(slash + 1);
                int q = rest.indexOf('?');
                if (q >= 0) rest = rest.substring(0, q);
                if (!rest.trim().isEmpty()) return rest.trim();
            }
        }
        // Oracle 形如 jdbc:oracle:thin:@host:1521/ORCLPDB1
        p = u.lastIndexOf('/');
        if (p >= 0 && p > u.indexOf('@')) {
            String tail = u.substring(p + 1);
            if (!tail.isEmpty()) return tail;
        }
        return user == null ? "" : user;
    }

    /** 显示用一行概要, 不含密码. */
    public String describe() {
        return String.format("%s | %s | user=%s",
                dbType == null ? "?" : dbType.displayName(),
                getUrl(), user);
    }
}
