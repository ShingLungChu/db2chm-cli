package com.db2chm.db;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 表元数据. 含表注释和列集合.
 */
public class TableInfo {

    private String name;
    private String comment;
    private List<ColumnInfo> columns = new ArrayList<>();

    public TableInfo() {}

    public TableInfo(String name, String comment) {
        this.name = name;
        this.comment = comment == null ? "" : comment;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment == null ? "" : comment; }

    public List<ColumnInfo> getColumns() { return columns; }
    public void setColumns(List<ColumnInfo> columns) {
        this.columns = columns == null ? new ArrayList<>() : columns;
    }

    public void addColumn(ColumnInfo col) {
        if (this.columns == null) this.columns = new ArrayList<>();
        this.columns.add(col);
    }

    public List<ColumnInfo> getColumnsReadonly() {
        return Collections.unmodifiableList(columns);
    }

    public int getColumnCount() {
        return columns == null ? 0 : columns.size();
    }
}