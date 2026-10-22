package com.db2chm.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 达梦 DM 适配器. 兼容 Oracle 方言, 使用 ALL_* 视图.
 *
 * <p>支持:
 * <ul>
 *   <li>ALL_TABLES / ALL_TAB_COMMENTS / ALL_TAB_COLUMNS / ALL_COL_COMMENTS</li>
 *   <li>ALL_CONSTRAINTS + ALL_CONS_COLUMNS 取主键</li>
 *   <li>DM 8 自增列 IDENTITY_COLUMN 字段</li>
 * </ul></p>
 */
public class DmAdapter extends AbstractDbAdapter {

    public DmAdapter(ConnectionInfo info) {
        super(info);
    }

    @Override
    public List<TableInfo> listTables() throws Exception {
        String owner = info.getSchema();
        String sql;
        if (owner == null || owner.trim().isEmpty()) {
            sql = "SELECT TABLE_NAME FROM ALL_TABLES WHERE OWNER = USER ORDER BY TABLE_NAME";
        } else {
            sql = "SELECT TABLE_NAME FROM ALL_TABLES WHERE OWNER = ? ORDER BY TABLE_NAME";
        }
        Map<String, String> cmts = loadTableComments(owner);

        List<TableInfo> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (owner != null && !owner.trim().isEmpty()) {
                ps.setString(1, owner.toUpperCase());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    result.add(new TableInfo(name, cmts.getOrDefault(name, "")));
                }
            }
        }
        return result;
    }

    private Map<String, String> loadTableComments(String owner) throws Exception {
        Map<String, String> map = new LinkedHashMap<>();
        String sql;
        if (owner == null || owner.trim().isEmpty()) {
            sql = "SELECT TABLE_NAME, COMMENTS FROM ALL_TAB_COMMENTS "
                + " WHERE OWNER=USER AND TABLE_TYPE='TABLE' AND COMMENTS IS NOT NULL";
        } else {
            sql = "SELECT TABLE_NAME, COMMENTS FROM ALL_TAB_COMMENTS "
                + " WHERE OWNER=? AND TABLE_TYPE='TABLE' AND COMMENTS IS NOT NULL";
        }
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (owner != null && !owner.trim().isEmpty()) {
                ps.setString(1, owner.toUpperCase());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) map.put(rs.getString(1), rs.getString(2));
            }
        }
        return map;
    }

    @Override
    public TableInfo getTableColumns(String tableName) throws Exception {
        String owner = (info.getSchema() == null || info.getSchema().trim().isEmpty())
                ? null : info.getSchema().toUpperCase();
        TableInfo table = new TableInfo(tableName, "");

        String sql;
        if (owner == null) {
            sql = "SELECT c.COLUMN_NAME, c.DATA_TYPE, c.DATA_LENGTH, c.DATA_PRECISION, "
                + "       c.DATA_SCALE, c.NULLABLE, cmts.COMMENTS "
                + "  FROM ALL_TAB_COLUMNS c "
                + "  LEFT JOIN ALL_COL_COMMENTS cmts "
                + "    ON cmts.OWNER=c.OWNER AND cmts.TABLE_NAME=c.TABLE_NAME "
                + "   AND cmts.COLUMN_NAME=c.COLUMN_NAME "
                + " WHERE c.OWNER = USER AND c.TABLE_NAME = ? "
                + " ORDER BY c.COLUMN_ID";
        } else {
            sql = "SELECT c.COLUMN_NAME, c.DATA_TYPE, c.DATA_LENGTH, c.DATA_PRECISION, "
                + "       c.DATA_SCALE, c.NULLABLE, cmts.COMMENTS "
                + "  FROM ALL_TAB_COLUMNS c "
                + "  LEFT JOIN ALL_COL_COMMENTS cmts "
                + "    ON cmts.OWNER=c.OWNER AND cmts.TABLE_NAME=c.TABLE_NAME "
                + "   AND cmts.COLUMN_NAME=c.COLUMN_NAME "
                + " WHERE c.OWNER = ? AND c.TABLE_NAME = ? "
                + " ORDER BY c.COLUMN_ID";
        }

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            if (owner != null) ps.setString(idx++, owner);
            ps.setString(idx, tableName.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                int seq = 0;
                while (rs.next()) {
                    seq++;
                    String dt = rs.getString("DATA_TYPE");
                    int dataLength = rs.getInt("DATA_LENGTH");
                    Integer precision = toInt(rs.getObject("DATA_PRECISION"));
                    Integer scale = toInt(rs.getObject("DATA_SCALE"));
                    String lengthStr = formatLength(dt, dataLength, precision, scale);
                    boolean nullable = "Y".equalsIgnoreCase(rs.getString("NULLABLE"));
                    table.addColumn(new ColumnInfo(seq, rs.getString("COLUMN_NAME"),
                            dt, lengthStr, false, 0, false, nullable,
                            safeComment(rs.getString("COMMENTS"))));
                }
            }
        }

        // 主键
        String pkSql;
        if (owner == null) {
            pkSql = "SELECT cc.COLUMN_NAME, cc.POSITION "
                  + "  FROM ALL_CONSTRAINTS c "
                  + "  JOIN ALL_CONS_COLUMNS cc ON cc.OWNER=c.OWNER AND cc.CONSTRAINT_NAME=c.CONSTRAINT_NAME "
                  + " WHERE c.OWNER=USER AND c.TABLE_NAME=? AND c.CONSTRAINT_TYPE='P' "
                  + " ORDER BY cc.POSITION";
        } else {
            pkSql = "SELECT cc.COLUMN_NAME, cc.POSITION "
                  + "  FROM ALL_CONSTRAINTS c "
                  + "  JOIN ALL_CONS_COLUMNS cc ON cc.OWNER=c.OWNER AND cc.CONSTRAINT_NAME=c.CONSTRAINT_NAME "
                  + " WHERE c.OWNER=? AND c.TABLE_NAME=? AND c.CONSTRAINT_TYPE='P' "
                  + " ORDER BY cc.POSITION";
        }
        Map<String, Integer> pkMap = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(pkSql)) {
            int idx = 1;
            if (owner != null) ps.setString(idx++, owner);
            ps.setString(idx, tableName.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) pkMap.put(rs.getString(1), rs.getInt(2));
            }
        }
        for (ColumnInfo c : table.getColumns()) {
            Integer pos = pkMap.get(c.getName());
            if (pos != null) {
                c.setPk(true);
                c.setPkSeq(pos);
            }
        }

        // 表注释
        String cmtSql = (owner == null)
                ? "SELECT COMMENTS FROM ALL_TAB_COMMENTS WHERE OWNER=USER AND TABLE_NAME=? AND TABLE_TYPE='TABLE'"
                : "SELECT COMMENTS FROM ALL_TAB_COMMENTS WHERE OWNER=? AND TABLE_NAME=? AND TABLE_TYPE='TABLE'";
        try (PreparedStatement ps = conn.prepareStatement(cmtSql)) {
            int idx = 1;
            if (owner != null) ps.setString(idx++, owner);
            ps.setString(idx, tableName.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) table.setComment(safeComment(rs.getString(1)));
            }
        }

        return table;
    }

    private static String formatLength(String dataType, int dataLength, Integer precision, Integer scale) {
        if (dataType == null) return "";
        String dt = dataType.toUpperCase();
        if ("NUMBER".equals(dt) || "DECIMAL".equals(dt)) {
            if (precision == null) return "";
            if (scale == null || scale == 0) return String.valueOf(precision);
            return precision + "," + scale;
        }
        if ("DATE".equals(dt) || "BLOB".equals(dt) || "CLOB".equals(dt)
                || "TEXT".equals(dt) || "BFILE".equals(dt)) {
            return "";
        }
        return String.valueOf(dataLength);
    }
}