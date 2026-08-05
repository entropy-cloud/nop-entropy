
package io.nop.metadata.api.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;

/**
 * 数据源下 catalog 表统计 DTO（来源：{@code NopMetaDataSourceBizModel.collectCatalog}）。
 */
@DataBean
public class CollectCatalogTableDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String tableName;
    private String metaSchema;
    private String tableType;
    private Long rowCount;
    private Long sizeBytes;

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getMetaSchema() {
        return metaSchema;
    }

    public void setMetaSchema(String metaSchema) {
        this.metaSchema = metaSchema;
    }

    public String getTableType() {
        return tableType;
    }

    public void setTableType(String tableType) {
        this.tableType = tableType;
    }

    public Long getRowCount() {
        return rowCount;
    }

    public void setRowCount(Long rowCount) {
        this.rowCount = rowCount;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }
}
