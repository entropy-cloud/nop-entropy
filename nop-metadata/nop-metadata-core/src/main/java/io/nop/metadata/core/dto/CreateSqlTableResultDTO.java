/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.metadata.core.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 创建 SQL 视图表结果 DTO（来源：{@code NopMetaTableBizModel.createSqlTable}）。
 */
@DataBean
public class CreateSqlTableResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String metaTableId;
    private String tableName;
    private String tableType;
    private List<SqlViewFieldDTO> fields = new ArrayList<>();

    public String getMetaTableId() {
        return metaTableId;
    }

    public void setMetaTableId(String metaTableId) {
        this.metaTableId = metaTableId;
    }

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

    public List<SqlViewFieldDTO> getFields() {
        return fields;
    }

    public void setFields(List<SqlViewFieldDTO> fields) {
        this.fields = fields;
    }
}
