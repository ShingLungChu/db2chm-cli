package com.db2chm.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Oracle 数据库适配器. 使用 ALL_* 视图(不要求 DBA 权限).
 *
 * <p>支持:
 * <ul>
 *   <li>表/列注释(ALL_TAB_COMMENTS / ALL_COL_COMMENTS)</li>
 *   <li>主键(ALL_CONSTRAINTS + ALL_CONS_COLUMNS)</li>
 *   <li>自增列(12c+ Identity column; 老版本由 trigger 实现, 简化用 data_default 判定)</li>
 * </ul></p>
 */
public class OracleAdapter extends AbstractDbAdapter {

    public OracleAdapter(ConnectionInfo info) {
        super(info);
    }

    @Override
    public List<TableInfo> listTables() throws Exception {
        String owner = info.getSchema();
        String sql;
        if (owner == null || owner.trim().isEmpty()) {
            // 默认查当前用户下的表
            sql = "SELECT TABLE_NAME FROM ALL_TABLES "
                + " WHERE OWNER = USER AND SECONDARY='N' "
                + " ORDER BY TABLE_NAME";
        } else {
            sql = "SELECT TABLE_NAME FROM ALL_TABLES "
                + " WHERE OWNER = ? AND SECONDARY='N' "
                + " ORDER BY TABLE_NAME";
        }

        List<TableInfo> result = new ArrayList<>();
        Map<String, String> comments = loadTableComments(owner);

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (owner != null && !owner.trim().isEmpty()) {
                ps.setString(1, owner.toUpperCase());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    String cmt = comments.getOrDefault(name, "");
                    result.add(new TableInfo(name, cmt));
                }
            }
        }
        return result;
    }

    private Map<String, String> loadTableComments(String owner) throws SQLException {
        Map<String, String> map = new LinkedHashMap<>();
        String sql;
        if (owner == null || owner.trim().isEmpty()) {
            sql = "SELECT TABLE_NAME, COMMENTS FROM ALL_TAB_COMMENTS "
                + " WHERE OWNER = USER AND TABLE_TYPE='TABLE' AND COMMENTS IS NOT NULL";
        } else {
            sql = "SELECT TABLE_NAME, COMMENTS FROM ALL_TAB_COMMENTS "
                + " WHERE OWNER = ? AND TABLE_TYPE='TABLE' AND COMMENTS IS NOT NULL";
        }
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (owner != null && !owner.trim().isEmpty()) {
                ps.setString(1, owner.toUpperCase());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getString(1), rs.getString(2));
                }
            }
        }
        return map;
    }

    @Override
    public TableInfo getTableColumns(String tableName) throws Exception {
        String owner = (info.getSchema() == null || info.getSchema().trim().isEmpty())
                ? null : info.getSchema().toUpperCase();
        String sql;
        if (owner == null) {
            sql = "SELECT c.COLUMN_NAME, c.DATA_TYPE, c.DATA_LENGTH, c.DATA_PRECISION, "
                + "       c.DATA_SCALE, c.NULLABLE, c.IDENTITY_COLUMN, c.DEFAULT_LENGTH, "
                + "       c.DATA_DEFAULT, cmts.COMMENTS "
                + "  FROM ALL_TAB_COLUMNS c "
                + "  LEFT JOIN ALL_COL_COMMENTS cmts "
                + "    ON cmts.OWNER = c.OWNER AND cmts.TABLE_NAME = c.TABLE_NAME "
                + "   AND cmts.COLUMN_NAME = c.COLUMN_NAME "
                + " WHERE c.OWNER = USER AND c.TABLE_NAME = ? "
                + " ORDER BY c.COLUMN_ID";
        } else {
            sql = "SELECT c.COLUMN_NAME, c.DATA_TYPE, c.DATA_LENGTH, c.DATA_PRECISION, "
                + "       c.DATA_SCALE, c.NULLABLE, c.IDENTITY_COLUMN, c.DEFAULT_LENGTH, "
                + "       c.DATA_DEFAULT, cmts.COMMENTS "
                + "  FROM ALL_TAB_COLUMNS c "
                + "  LEFT JOIN ALL_COL_COMMENTS cmts "
                + "    ON cmts.OWNER = c.OWNER AND cmts.TABLE_NAME = c.TABLE_NAME "
                + "   AND cmts.COLUMN_NAME = c.COLUMN_NAME "
                + " WHERE c.OWNER = ? AND c.TABLE_NAME = ? "
                + " ORDER BY c.COLUMN_ID";
        }

        TableInfo table = new TableInfo(tableName, "");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            if (owner != null) ps.setString(idx++, owner);
            ps.setString(idx, tableName.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                int seq = 0;
                while (rs.next()) {
                    seq++;
                    String dataType = rs.getString("DATA_TYPE");
                    int dataLength = rs.getInt("DATA_LENGTH");
                    Integer precision = toInt(rs.getObject("DATA_PRECISION"));
                    Integer scale = toInt(rs.getObject("DATA_SCALE"));
                    String lengthStr = formatLength(dataType, dataLength, precision, scale);
                    boolean nullable = "Y".equalsIgnoreCase(rs.getString("NULLABLE"));
                    String identity = rs.getString("IDENTITY_COLUMN");
                    boolean isAutoInc = "YES".equalsIgnoreCase(identity);

                    // 兜底: 老版本可能没 IDENTITY_COLUMN 字段, 用 data_default 含 IDENTITY 判定
                    if (!isAutoInc) {
                        String def = rs.getString("DATA_DEFAULT");
                        if (def != null && def.toUpperCase().contains("IDENTITY")) {
                            isAutoInc = true;
                        }
                    }

                    String comment = safeComment(rs.getString("COMMENTS"));
                    table.addColumn(new ColumnInfo(seq, rs.getString("COLUMN_NAME"),
                            dataType, lengthStr, false, 0, isAutoInc, nullable, comment));
                }
            }
        }

        // 主键
        Map<String, Integer> pkMap = loadPrimaryKeys(owner, tableName);
        for (ColumnInfo c : table.getColumns()) {
            Integer pos = pkMap.get(c.getName());
            if (pos != null) {
                c.setPk(true);
                c.setPkSeq(pos);
            }
        }

        // 表注释
        table.setComment(loadTableComment(owner, tableName));
        return table;
    }

    private Map<String, Integer> loadPrimaryKeys(String owner, String tableName) throws SQLException {
        Map<String, Integer> map = new LinkedHashMap<>();
        String sql;
        if (owner == null) {
            sql = "SELECT cc.COLUMN_NAME, cc.POSITION "
                + "  FROM ALL_CONSTRAINTS c "
                + "  JOIN ALL_CONS_COLUMNS cc "
                + "    ON cc.OWNER = c.OWNER AND cc.CONSTRAINT_NAME = c.CONSTRAINT_NAME "
                + " WHERE c.OWNER = USER AND c.TABLE_NAME = ? "
                + "   AND c.CONSTRAINT_TYPE = 'P' "
                + " ORDER BY cc.POSITION";
        } else {
            sql = "SELECT cc.COLUMN_NAME, cc.POSITION "
                + "  FROM ALL_CONSTRAINTS c "
                + "  JOIN ALL_CONS_COLUMNS cc "
                + "    ON cc.OWNER = c.OWNER AND cc.CONSTRAINT_NAME = c.CONSTRAINT_NAME "
                + " WHERE c.OWNER = ? AND c.TABLE_NAME = ? "
                + "   AND c.CONSTRAINT_TYPE = 'P' "
                + " ORDER BY cc.POSITION";
        }
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            if (owner != null) ps.setString(idx++, owner);
            ps.setString(idx, tableName.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getString(1), rs.getInt(2));
                }
            }
        }
        return map;
    }

    private String loadTableComment(String owner, String tableName) throws SQLException {
        String sql = (owner == null)
                ? "SELECT COMMENTS FROM ALL_TAB_COMMENTS WHERE OWNER=USER AND TABLE_NAME=? AND TABLE_TYPE='TABLE'"
                : "SELECT COMMENTS FROM ALL_TAB_COMMENTS WHERE OWNER=? AND TABLE_NAME=? AND TABLE_TYPE='TABLE'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            if (owner != null) ps.setString(idx++, owner);
            ps.setString(idx, tableName.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return safeComment(rs.getString(1));
            }
        }
        return "";
    }

    /** 把 Oracle 字段类型/长度统一成字符串. */
    private static String formatLength(String dataType, int dataLength, Integer precision, Integer scale) {
        if (dataType == null) return "";
        String dt = dataType.toUpperCase();
        if ("NUMBER".equals(dt)) {
            if (precision == null) return "";
            if (scale == null || scale == 0) return String.valueOf(precision);
            return precision + "," + scale;
        }
        if ("DATE".equals(dt) || "BLOB".equals(dt) || "CLOB".equals(dt) || "NCLOB".equals(dt)
                || "LONG".equals(dt) || "BFILE".equals(dt) || "RAW".equals(dt)) {
            return "";
        }
        return String.valueOf(dataLength);
    }
}