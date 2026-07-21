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
 * SQL 字段预览结果 DTO（来源：{@code NopMetaTableBizModel.previewSqlFields}）。
 */
@DataBean
public class PreviewSqlFieldsResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<SqlViewFieldDTO> fields = new ArrayList<>();

    public List<SqlViewFieldDTO> getFields() {
        return fields;
    }

    public void setFields(List<SqlViewFieldDTO> fields) {
        this.fields = fields;
    }
}
