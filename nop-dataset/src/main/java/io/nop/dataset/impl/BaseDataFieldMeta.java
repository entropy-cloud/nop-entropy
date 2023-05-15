/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dataset.impl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.data.ImmutableBean;
import io.nop.commons.type.StdDataType;
import io.nop.dataset.IDataFieldMeta;

@DataBean
@ImmutableBean
public class BaseDataFieldMeta implements IDataFieldMeta {
    private static final long serialVersionUID = 2172047251843605201L;

    private final String fieldName;
    private final String sourceFieldName;
    private final String fieldOwnerEntityName;
    private final StdDataType fieldStdType;
    private final boolean computed;

    public BaseDataFieldMeta(@JsonProperty("fieldName") String fieldName,
                             @JsonProperty("sourceFieldName") String sourceFieldName,
                             @JsonProperty("fieldOwnerEntityName") String fieldOwnerEntityName,
                             @JsonProperty("fieldStdType") StdDataType fieldStdType,
                             @JsonProperty("computed") boolean computed) {
        this.fieldName = fieldName;
        this.sourceFieldName = sourceFieldName;
        this.fieldOwnerEntityName = fieldOwnerEntityName;
        this.fieldStdType = fieldStdType;
        this.computed = computed;
    }

    public static BaseDataFieldMeta fromColumnMeta(IDataFieldMeta columnMeta) {
        if (columnMeta instanceof BaseDataFieldMeta)
            return (BaseDataFieldMeta) columnMeta;

        return new BaseDataFieldMeta(columnMeta.getFieldName(), columnMeta.getSourceFieldName(),
                columnMeta.getFieldOwnerEntityName(), columnMeta.getFieldStdType(), columnMeta.isComputed());
    }

    @Override
    public String getFieldName() {
        return fieldName;
    }

    @Override
    public boolean isComputed() {
        return computed;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Override
    public String getFieldOwnerEntityName() {
        return fieldOwnerEntityName;
    }

    @Override
    public StdDataType getFieldStdType() {
        return fieldStdType;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Override
    public String getSourceFieldName() {
        return sourceFieldName;
    }
}