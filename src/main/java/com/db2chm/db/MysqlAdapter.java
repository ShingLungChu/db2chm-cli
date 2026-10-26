package com.db2chm.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MySQL 适配器. 基于 information_schema.
 */
public class MysqlAdapter extends AbstractDbAdapter {

    public MysqlAdapter(ConnectionInfo info) {
        super(info);
    }

    @Override
    public List<TableInfo> listTables() throws Exception {
        String schema = info.getSchema();
        String sql = "SELECT TABLE_NAME, IFNULL(TABLE_COMMENT,'') AS TABLE_COMMENT "
                   + "  FROM information_schema.TABLES "
                   + " WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE' "
                   + " ORDER BY TABLE_NAME";
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
        String schema = info.getSchema();
        TableInfo table = new TableInfo(tableName, loadTableComment(schema, tableName));

        String sql = "SELECT COLUMN_NAME, DATA_TYPE, COLUMN_TYPE, IS_NULLABLE, "
                   + "       COLUMN_KEY, EXTRA, IFNULL(COLUMN_COMMENT,'') AS COLUMN_COMMENT, "
                   + "       ORDINAL_POSITION "
                   + "  FROM information_schema.COLUMNS "
                   + " WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? "
                   + " ORDER BY ORDINAL_POSITION";

        try (PreparedStatement ps = prepareMetadataQuery(sql)) {
            ps.setString(1, schema);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                int seq = 0;
                while (rs.next()) {
                    seq++;
                    String ctype = rs.getString("COLUMN_TYPE");
                    String dataType = rs.getString("DATA_TYPE");
                    String lengthStr = "";
                    if (ctype != null && ctype.contains("(")) {
                        lengthStr = ctype.substring(ctype.indexOf('(') + 1, ctype.lastIndexOf(')'));
                    }
                    boolean nullable = "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE"));
                    boolean isAutoInc = (rs.getString("EXTRA") != null)
                            && rs.getString("EXTRA").toLowerCase().contains("auto_increment");
                    String cmt = safeComment(rs.getString("COLUMN_COMMENT"));
                    table.addColumn(new ColumnInfo(seq, rs.getString("COLUMN_NAME"),
                            dataType, lengthStr, false, 0, isAutoInc, nullable, cmt));
                }
            }
        }

        // 主键(用 STATISTICS 排序)
        Map<String, Integer> pkMap = new LinkedHashMap<>();
        String pkSql = "SELECT COLUMN_NAME, SEQ_IN_INDEX FROM information_schema.STATISTICS "
                     + " WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND INDEX_NAME = 'PRIMARY' "
                     + " ORDER BY SEQ_IN_INDEX";
        try (PreparedStatement ps = prepareMetadataQuery(pkSql)) {
            ps.setString(1, schema);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    pkMap.put(rs.getString(1), rs.getInt(2));
                }
            }
        }
        for (ColumnInfo c : table.getColumns()) {
            Integer pos = pkMap.get(c.getName());
            if (pos != null) {
                c.setPk(true);
                c.setPkSeq(pos);
            }
        }
        return table;
    }

    private String loadTableComment(String schema, String tableName) throws Exception {
        String sql = "SELECT IFNULL(TABLE_COMMENT,'') AS TABLE_COMMENT "
                   + "  FROM information_schema.TABLES "
                   + " WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
        try (PreparedStatement ps = prepareMetadataQuery(sql)) {
            ps.setString(1, schema);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return safeComment(rs.getString(1));
            }
        }
        return "";
    }
}