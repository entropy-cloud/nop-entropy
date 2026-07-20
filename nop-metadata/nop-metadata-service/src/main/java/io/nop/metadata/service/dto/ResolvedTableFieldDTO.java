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
 * 解析后字段 DTO（来源：{@code NopMetaTableBizModel.resolveTableFields}）。
 */
@DataBean
public class ResolvedTableFieldDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private String sourceType;
    private String type;

    public ResolvedTableFieldDTO() {
    }

    public ResolvedTableFieldDTO(String name, String sourceType, String type) {
        this.name = name;
        this.sourceType = sourceType;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
