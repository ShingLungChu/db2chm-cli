package com.db2chm.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 人大金仓 KingbaseES 适配器 (PG 协议变体).
 *
 * <p>KingbaseES V8 兼容 PostgreSQL 协议, 查询语法基本一致;
 * 自增列可能用 SERIAL/BIGSERIAL 或 GENERATED AS IDENTITY.</p>
 */
public class KingbaseAdapter extends AbstractDbAdapter {

    public KingbaseAdapter(ConnectionInfo info) {
        super(info);
    }

    @Override
    public List<TableInfo> listTables() throws Exception {
        String schema = resolveSchema();
        // 优先用 information_schema(更标准); 兼容老版本用 pg_class
        String sql = "SELECT c.relname AS table_name, "
                   + "       obj_description(c.oid, 'pg_class') AS table_comment "
                   + "  FROM pg_class c "
                   + "  JOIN pg_namespace n ON n.oid = c.relnamespace "
                   + " WHERE c.relkind = 'r' AND n.nspname = ? "
                   + " ORDER BY c.relname";
        List<TableInfo> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new TableInfo(rs.getString(1), safeComment(rs.getString(2))));
                }
            }
        }
        return result;
    }

    @Override
    public TableInfo getTableColumns(String tableName) throws Exception {
        String schema = resolveSchema();
        TableInfo table = new TableInfo(tableName, loadTableComment(schema, tableName));

        // information_schema.columns + is_identity 字段判自增 (KingbaseES 支持)
        String sql = "SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, "
                   + "       COLUMN_DEFAULT, IDENTITY_GENERATION, "
                   + "       CHARACTER_MAXIMUM_LENGTH, NUMERIC_PRECISION, NUMERIC_SCALE, "
                   + "       ORDINAL_POSITION, IFNULL(COMMENT, '') AS COLUMN_COMMENT "
                   + "  FROM information_schema.COLUMNS "
                   + " WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? "
                   + " ORDER BY ORDINAL_POSITION";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                int seq = 0;
                while (rs.next()) {
                    seq++;
                    String dt = rs.getString("DATA_TYPE");
                    Integer charLen = (Integer) rs.getObject("CHARACTER_MAXIMUM_LENGTH");
                    Integer precision = (Integer) rs.getObject("NUMERIC_PRECISION");
                    Integer scale = (Integer) rs.getObject("NUMERIC_SCALE");
                    String lenStr = formatLength(dt, charLen, precision, scale);
                    boolean nullable = "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE"));
                    String idGen = rs.getString("IDENTITY_GENERATION");
                    boolean isAutoInc = (idGen != null && !idGen.isEmpty()
                            && !"NO".equalsIgnoreCase(idGen));
                    // 兜底: 若 default 含 nextval('seq') 也算自增
                    if (!isAutoInc) {
                        String def = rs.getString("COLUMN_DEFAULT");
                        if (def != null && def.toLowerCase().contains("nextval")) {
                            isAutoInc = true;
                        }
                    }
                    String cmt = safeComment(rs.getString("COLUMN_COMMENT"));
                    table.addColumn(new ColumnInfo(seq, rs.getString("COLUMN_NAME"),
                            dt, lenStr, false, 0, isAutoInc, nullable, cmt));
                }
            }
        }

        // 主键
        Map<String, Integer> pkMap = loadPrimaryKeys(schema, tableName);
        for (ColumnInfo c : table.getColumns()) {
            Integer pos = pkMap.get(c.getName());
            if (pos != null) {
                c.setPk(true);
                c.setPkSeq(pos);
            }
        }
        return table;
    }

    private Map<String, Integer> loadPrimaryKeys(String schema, String tableName) throws Exception {
        Map<String, Integer> map = new LinkedHashMap<>();
        String sql = "SELECT kcu.COLUMN_NAME, kcu.ORDINAL_POSITION "
                   + "  FROM information_schema.TABLE_CONSTRAINTS tc "
                   + "  JOIN information_schema.KEY_COLUMN_USAGE kcu "
                   + "    ON kcu.CONSTRAINT_NAME = tc.CONSTRAINT_NAME "
                   + "   AND kcu.TABLE_SCHEMA = tc.TABLE_SCHEMA "
                   + " WHERE tc.TABLE_SCHEMA = ? AND tc.TABLE_NAME = ? "
                   + "   AND tc.CONSTRAINT_TYPE = 'PRIMARY KEY' "
                   + " ORDER BY kcu.ORDINAL_POSITION";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) map.put(rs.getString(1), rs.getInt(2));
            }
        }
        return map;
    }

    private String loadTableComment(String schema, String tableName) throws Exception {
        String sql = "SELECT obj_description(c.oid, 'pg_class') AS cmt "
                   + "  FROM pg_class c "
                   + "  JOIN pg_namespace n ON n.oid = c.relnamespace "
                   + " WHERE c.relname = ? AND n.nspname = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            ps.setString(2, schema);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return safeComment(rs.getString(1));
            }
        }
        return "";
    }

    private String resolveSchema() {
        String s = info.getSchema();
        if (s == null || s.trim().isEmpty()) s = "public";
        return s;
    }

    private static String formatLength(String dt, Integer charLen, Integer precision, Integer scale) {
        if (dt == null) return "";
        String u = dt.toUpperCase();
        if (u.contains("CHAR") || u.equals("VARCHAR") || u.equals("CHARACTER")) {
            return charLen == null ? "" : String.valueOf(charLen);
        }
        if (u.contains("DECIMAL") || u.contains("NUMERIC")) {
            if (precision == null) return "";
            if (scale == null || scale == 0) return String.valueOf(precision);
            return precision + "," + scale;
        }
        return "";
    }
}