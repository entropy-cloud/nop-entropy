package io.nop.metadata.service.sync;

/**
 * 外部数据源扫描到的单列结构快照（列名 / 类型 / 可空 / 注释 / 序号 / 默认值）。
 * 对应 {@code information_schema.COLUMNS} 的核心字段（架构基线 §2.5.1）。
 */
public class ExternalColumnInfo {

    private String columnName;
    /** JDBC TYPE_NAME（数据库原生类型名，如 VARCHAR / BIGINT / TIMESTAMP） */
    private String dataType;
    private int precision;
    private int scale;
    private boolean nullable;
    private String remark;
    private int ordinal;
    private String defaultValue;

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public int getPrecision() {
        return precision;
    }

    public void setPrecision(int precision) {
        this.precision = precision;
    }

    public int getScale() {
        return scale;
    }

    public void setScale(int scale) {
        this.scale = scale;
    }

    public boolean isNullable() {
        return nullable;
    }

    public void setNullable(boolean nullable) {
        this.nullable = nullable;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public void setOrdinal(int ordinal) {
        this.ordinal = ordinal;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }
}
