
package io.nop.metadata.api.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 创建 SQL 视图表结果 DTO（来源：{@code NopMetaEntityBizModel.createSqlView}）。
 */
@DataBean
public class CreateSqlViewResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String metaEntityId;
    private String tableName;
    private String entityKind;
    private List<SqlViewFieldDTO> fields = new ArrayList<>();

    public String getMetaEntityId() {
        return metaEntityId;
    }

    public void setMetaEntityId(String metaEntityId) {
        this.metaEntityId = metaEntityId;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getEntityKind() {
        return entityKind;
    }

    public void setEntityKind(String entityKind) {
        this.entityKind = entityKind;
    }

    public List<SqlViewFieldDTO> getFields() {
        return fields;
    }

    public void setFields(List<SqlViewFieldDTO> fields) {
        this.fields = fields;
    }
}
