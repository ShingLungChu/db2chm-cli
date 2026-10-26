package com.db2chm.db;

import com.db2chm.cli.CliFailure;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.*;

/** Connection capability allowlist. Only audited metadata queries are exposed by the adapters. */
public final class JdbcPolicy {
    private JdbcPolicy() { }
    public static void validate(ConnectionInfo info) throws CliFailure {
        DatabaseType type = info.getDbType();
        if (type == null) throw CliFailure.usage("Database type is required.");
        if (!type.driverClass().equals(info.getDriverClass())) reject("Driver class must match the selected database type.");
        String url = info.getUrl();
        if (url.length() > 8192 || controls(url) || controls(info.getSchema()) || controls(info.getUser())) reject("Connection contains invalid control characters.");
        try { Charset.forName(info.getCharset()); } catch (Exception e) { reject("Unsupported connection charset."); }
        String prefix;
        switch (type) {
            case MYSQL: prefix = "jdbc:mysql:"; break;
            case ORACLE: prefix = "jdbc:oracle:thin:@"; break;
            case DM: prefix = "jdbc:dm:"; break;
            case VASTBASE: prefix = "jdbc:postgresql:"; break;
            case KINGBASE: prefix = "jdbc:kingbase8:"; break;
            default: throw CliFailure.usage("Unsupported database type.");
        }
        if (!url.startsWith(prefix)) reject("JDBC URL does not match the selected database type.");
        if ((type == DatabaseType.ORACLE || type == DatabaseType.DM) && info.getUser() != null && !info.getUser().isEmpty()
                && !info.getUser().matches("[\\p{L}\\p{N}_.$#-]+")) reject("Use a simple metadata account name without role or credential syntax.");
        String rest = url.substring(prefix.length());
        if (type == DatabaseType.ORACLE) {
            // No descriptor, TNS alias, properties, credential syntax or arbitrary protocol.
            if (!rest.matches("(?://)?(?:[A-Za-z0-9._-]+|\\[[0-9A-Fa-f:]+\\]):[0-9]{1,5}[/:][A-Za-z0-9._-]+")) reject("Only a direct Oracle host:port/service or host:port:SID URL is supported.");
            java.util.regex.Matcher port = java.util.regex.Pattern.compile(":([0-9]{1,5})[/:][A-Za-z0-9._-]+$").matcher(rest);
            if (!port.find() || Integer.parseInt(port.group(1)) < 1 || Integer.parseInt(port.group(1)) > 65535) reject("Oracle port must be in 1..65535.");
            return;
        }
        int q = rest.indexOf('?');
        String base = q < 0 ? rest : rest.substring(0, q);
        try {
            URI parsed = new URI("db:" + base);
            if (!base.startsWith("//") || parsed.getHost() == null || parsed.getRawUserInfo() != null || parsed.getRawFragment() != null || parsed.getPort() < -1 || parsed.getPort() == 0 || parsed.getPort() > 65535 || base.contains(";") || base.contains("%") || base.contains("\\")) reject("Only a direct host[:port]/database JDBC URL is supported.");
            if (type != DatabaseType.DM && (parsed.getPath() == null || parsed.getPath().length() < 2)) reject("Database name is required in the JDBC URL.");
        } catch (java.net.URISyntaxException e) { reject("Malformed JDBC URL."); }
        if (q < 0) return;
        Set<String> allowed = new HashSet<>(Arrays.asList("connecttimeout", "sockettimeout"));
        switch (type) {
            case MYSQL: allowed.addAll(Arrays.asList("useunicode", "characterencoding", "usessl", "requiressl", "verifyservercertificate", "sslmode", "servertimezone", "tcpkeepalive")); break;
            case VASTBASE: case KINGBASE: allowed.addAll(Arrays.asList("currentschema", "ssl", "sslmode", "applicationname", "tcpkeepalive")); break;
            case DM: allowed.addAll(Arrays.asList("schema", "connecttimeout", "sockettimeout")); break;
            default: break;
        }
        Set<String> seen = new HashSet<>();
        for (String pair : rest.substring(q + 1).split("&", -1)) {
            int eq = pair.indexOf('=');
            if (eq < 1) reject("Malformed JDBC connection property.");
            String key = decode(pair.substring(0, eq)).toLowerCase(Locale.ROOT);
            String value = decode(pair.substring(eq + 1));
            if (!allowed.contains(key) || !seen.add(key)) reject("Unsupported or duplicate JDBC connection property.");
            if (value.length() > 256 || controls(value) || value.indexOf(';') >= 0 || value.indexOf('%') >= 0 || value.indexOf('&') >= 0) reject("Invalid JDBC connection property value.");
            if (key.endsWith("timeout") && (!value.matches("[0-9]{1,6}") || Integer.parseInt(value) < 1)) reject("Connection timeouts must be positive and bounded.");
            if (Arrays.asList("useunicode", "usessl", "requiressl", "verifyservercertificate", "ssl", "tcpkeepalive").contains(key) && !Arrays.asList("true", "false").contains(value.toLowerCase(Locale.ROOT))) reject("Invalid boolean connection property.");
        }
    }
    private static String decode(String value) throws CliFailure {
        try { return URLDecoder.decode(value, "UTF-8"); } catch (Exception e) { reject("Malformed URL encoding."); return ""; }
    }
    private static boolean controls(String value) { return value != null && value.chars().anyMatch(c -> c < 32 || c == 127); }
    private static void reject(String message) throws CliFailure { throw new CliFailure(8, "CONNECTION_POLICY_REJECTED", message); }
    public static Properties properties(ConnectionInfo info) {
        Properties p = new Properties();
        if (info.getUser() != null) p.setProperty("user", info.getUser());
        if (info.getPassword() != null) p.setProperty("password", info.getPassword());
        switch (info.getDbType()) {
            case MYSQL:
                p.setProperty("allowMultiQueries", "false"); p.setProperty("allowLoadLocalInfile", "false");
                p.setProperty("allowUrlInLocalInfile", "false"); p.setProperty("autoDeserialize", "false");
                p.setProperty("connectTimeout", String.valueOf(info.getConnectTimeout() * 1000));
                p.setProperty("socketTimeout", String.valueOf(info.getQueryTimeout() * 1000));
                p.setProperty("characterEncoding", Charset.forName(info.getCharset()).name()); break;
            case VASTBASE: case KINGBASE:
                p.setProperty("connectTimeout", String.valueOf(info.getConnectTimeout()));
                p.setProperty("socketTimeout", String.valueOf(info.getQueryTimeout()));
                p.setProperty("ApplicationName", "db2chm"); break;
            case ORACLE:
                p.setProperty("oracle.net.CONNECT_TIMEOUT", String.valueOf(info.getConnectTimeout() * 1000));
                p.setProperty("oracle.jdbc.ReadTimeout", String.valueOf(info.getQueryTimeout() * 1000)); break;
            default: break;
        }
        return p;
    }
}
