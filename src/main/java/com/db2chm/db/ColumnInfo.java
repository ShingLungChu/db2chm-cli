package com.db2chm.db;

/**
 * 单列元数据. 字段严格对应 CHM 表结构页的 9 列展示.
 */
public class ColumnInfo {

    private int seq;                  // 序号(列在表中的位置, 1-based)
    private String name;              // 列名
    private String dataType;          // 数据类型 (VARCHAR2 / NUMBER / DATE ...)
    private String length;            // 长度(VARCHAR2(50)-> "50"; NUMBER(18,2)-> "18,2"; 空表示无长度)
    private boolean isPk;             // 是否主键列
    private int pkSeq;                // 复合主键中的顺序(1-based, 非主键为 0)
    private boolean isAutoIncrement;  // 是否自增列
    private boolean nullable;         // 是否允许 NULL
    private String comment;           // 列注释/说明

    public ColumnInfo() {}

    public ColumnInfo(int seq, String name, String dataType, String length,
                      boolean isPk, int pkSeq, boolean isAutoIncrement,
                      boolean nullable, String comment) {
        this.seq = seq;
        this.name = name;
        this.dataType = dataType;
        this.length = length;
        this.isPk = isPk;
        this.pkSeq = pkSeq;
        this.isAutoIncrement = isAutoIncrement;
        this.nullable = nullable;
        this.comment = comment == null ? "" : comment;
    }

    public int getSeq() { return seq; }
    public void setSeq(int seq) { this.seq = seq; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }

    public String getLength() { return length; }
    public void setLength(String length) { this.length = length; }

    public boolean isPk() { return isPk; }
    public void setPk(boolean pk) { isPk = pk; }

    public int getPkSeq() { return pkSeq; }
    public void setPkSeq(int pkSeq) { this.pkSeq = pkSeq; }

    public boolean isAutoIncrement() { return isAutoIncrement; }
    public void setAutoIncrement(boolean autoIncrement) { isAutoIncrement = autoIncrement; }

    public boolean isNullable() { return nullable; }
    public void setNullable(boolean nullable) { this.nullable = nullable; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment == null ? "" : comment; }

    /** 用于显示的完整类型(VARCHAR2(50) / NUMBER(18,2) / DATE 等). */
    public String getFullType() {
        if (length == null || length.isEmpty()) {
            return dataType;
        }
        return dataType + "(" + length + ")";
    }

    public String getNullableDisplay() {
        return nullable ? "Y" : "N";
    }

    public String getPkDisplay() {
        if (!isPk) return "";
        return pkSeq > 1 ? "PK(" + pkSeq + ")" : "PK";
    }

    public String getAutoIncDisplay() {
        return isAutoIncrement ? "Y" : "";
    }
}