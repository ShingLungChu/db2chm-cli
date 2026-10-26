package com.db2chm.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vastbase 适配器 (PG 协议). 基于 information_schema.
 *
 * <p>vastbase 兼容 PostgreSQL 协议, 用 PG JDBC 驱动即可.
 * 自增列靠 sequence + serial 或 GENERATED AS IDENTITY.</p>
 */
public class VastbaseAdapter extends AbstractDbAdapter {

    public VastbaseAdapter(ConnectionInfo info) {
        super(info);
    }

    @Override
    public List<TableInfo> listTables() throws Exception {
        String schema = resolveSchema();
        String sql = "SELECT c.relname AS table_name, "
                   + "       obj_description(c.oid, 'pg_class') AS table_comment "
                   + "  FROM pg_class c "
                   + "  JOIN pg_namespace n ON n.oid = c.relnamespace "
                   + " WHERE c.relkind = 'r' AND n.nspname = ? "
                   + " ORDER BY c.relname";
        List<TableInfo> result = new ArrayList<>();
        try (PreparedStatement ps = prepareMetadataQuery(sql)) {
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

        String sql = "SELECT a.attname AS column_name, "
                   + "       format_type(a.atttypid, a.atttypmod) AS data_type, "
                   + "       a.attnotnull AS not_null, "
                   + "       col_description(c.oid, a.attnum) AS column_comment, "
                   + "       a.attnum AS ordinal_position, "
                   + "       pg_get_serial_sequence(quote_ident(?)||'.'||quote_ident(?), a.attname) AS seq_name, "
                   + "       t.typname AS base_type, "
                   + "       a.atttypmod AS typmod "
                   + "  FROM pg_attribute a "
                   + "  JOIN pg_class c ON c.oid = a.attrelid "
                   + "  JOIN pg_namespace n ON n.oid = c.relnamespace "
                   + "  JOIN pg_type t ON t.oid = a.atttypid "
                   + " WHERE c.relname = ? AND n.nspname = ? "
                   + "   AND a.attnum > 0 AND NOT a.attisdropped "
                   + " ORDER BY a.attnum";

        try (PreparedStatement ps = prepareMetadataQuery(sql)) {
            ps.setString(1, schema);
            ps.setString(2, tableName);
            ps.setString(3, tableName);
            ps.setString(4, schema);
            try (ResultSet rs = ps.executeQuery()) {
                int seq = 0;
                while (rs.next()) {
                    seq++;
                    String dt = rs.getString("data_type");
                    String lenStr = extractLength(dt);
                    String dtOnly = extractTypeName(dt);
                    boolean notNull = rs.getBoolean("not_null");
                    String seqName = rs.getString("seq_name");
                    boolean isAutoInc = (seqName != null && !seqName.isEmpty());
                    String cmt = safeComment(rs.getString("column_comment"));
                    table.addColumn(new ColumnInfo(seq, rs.getString("column_name"),
                            dtOnly, lenStr, false, 0, isAutoInc, !notNull, cmt));
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
        try (PreparedStatement ps = prepareMetadataQuery(sql)) {
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
        try (PreparedStatement ps = prepareMetadataQuery(sql)) {
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

    /** 从 format_type 返回值(如 varchar(50)/numeric(18,2))提取长度串. */
    private static String extractLength(String full) {
        if (full == null) return "";
        int p = full.indexOf('(');
        if (p < 0) return "";
        int q = full.indexOf(')', p);
        if (q < 0) return "";
        return full.substring(p + 1, q);
    }

    private static String extractTypeName(String full) {
        if (full == null) return "";
        int p = full.indexOf('(');
        return p < 0 ? full : full.substring(0, p);
    }
}