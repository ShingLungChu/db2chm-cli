package com.mysql.cj.jdbc;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;
import java.lang.reflect.Proxy;

/** Test-only JDBC implementation. No sockets, database or driver registration. */
public final class Driver implements java.sql.Driver {
    public static boolean readOnlyAccepted = true, readOnlyCalled, closed;
    public static int connections, statementTimeout;
    public static Properties received;
    public Connection connect(String url, Properties properties) {
        connections++; closed = false; readOnlyCalled = false; received = properties;
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
            switch(method.getName()) {
                case "setReadOnly": readOnlyCalled = Boolean.TRUE.equals(args[0]); return null;
                case "isReadOnly": return readOnlyAccepted && readOnlyCalled;
                case "close": closed = true; return null;
                case "isClosed": return closed;
                case "prepareStatement":
                    return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class}, (p, m, a) -> {
                        if(m.getName().equals("setQueryTimeout")) { statementTimeout = (Integer)a[0]; return null; }
                        if(m.getName().equals("close")) return null;
                        throw new UnsupportedOperationException(m.getName());
                    });
                default: throw new UnsupportedOperationException(method.getName());
            }
        });
    }
    public boolean acceptsURL(String url) { return url.startsWith("jdbc:mysql:"); }
    public DriverPropertyInfo[] getPropertyInfo(String u, Properties p) { return new DriverPropertyInfo[0]; }
    public int getMajorVersion() { return 0; }
    public int getMinorVersion() { return 0; }
    public boolean jdbcCompliant() { return false; }
    public Logger getParentLogger() { return Logger.getGlobal(); }
}
