package io.nop.metadata.service.sync;

import java.util.ArrayList;
import java.util.List;

/**
 * 外部数据源扫描到的单张物理表结构快照（表名 + 列结构）。
 * 由 {@link ExternalTableStructureReader#read} 产出，供 BizModel 序列化写入目录。
 */
public class ExternalTableInfo {

    private String tableName;
    /** JDBC TABLE_TYPE（"TABLE" / "VIEW" 等），仅用于诊断/日志 */
    private String tableType;
    private String remark;
    private final List<ExternalColumnInfo> columns = new ArrayList<>();

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getTableType() {
        return tableType;
    }

    public void setTableType(String tableType) {
        this.tableType = tableType;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public List<ExternalColumnInfo> getColumns() {
        return columns;
    }
}
