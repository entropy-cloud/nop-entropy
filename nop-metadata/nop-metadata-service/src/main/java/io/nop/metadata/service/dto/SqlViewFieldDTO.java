/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.metadata.service.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;

/**
 * SQL 视图字段 DTO（来源：{@code NopMetaTableBizModel.createSqlTable} / {@code previewSqlFields}）。
 */
@DataBean
public class SqlViewFieldDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private String alias;
    private String type;

    public SqlViewFieldDTO() {
    }

    public SqlViewFieldDTO(String name, String alias, String type) {
        this.name = name;
        this.alias = alias;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
