/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dataset.impl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.data.ImmutableBean;
import io.nop.commons.type.StdDataType;
import io.nop.commons.type.StdSqlType;
import io.nop.dataset.IDataFieldMeta;

import java.util.ArrayList;
import java.util.List;

@DataBean
@ImmutableBean
public class BaseDataFieldMeta implements IDataFieldMeta {
    private static final long serialVersionUID = 2172047251843605201L;

    private final String fieldName;
    private final String sourceFieldName;
    private final String fieldOwnerEntityName;
    private final StdDataType stdDataType;
    private final StdSqlType stdSqlType;
    private final boolean computed;

    public BaseDataFieldMeta(@JsonProperty("fieldName") String fieldName,
                             @JsonProperty("sourceFieldName") String sourceFieldName,
                             @JsonProperty("fieldOwnerEntityName") String fieldOwnerEntityName,
                             @JsonProperty("stdDataType") StdDataType stdDataType,
                             @JsonProperty("stdSqlType") StdSqlType stdSqlType,
                             @JsonProperty("computed") boolean computed) {
        this.fieldName = fieldName;
        this.sourceFieldName = sourceFieldName;
        this.fieldOwnerEntityName = fieldOwnerEntityName;
        this.stdDataType = stdDataType;
        this.stdSqlType = stdSqlType;
        this.computed = computed;
    }

    public static BaseDataFieldMeta build(String fieldName, StdDataType fieldStdType) {
        return new BaseDataFieldMeta(fieldName, null, null, fieldStdType, StdSqlType.fromStdDataTYpe(fieldStdType), false);
    }

    public static BaseDataFieldMeta fromColumnMeta(IDataFieldMeta columnMeta) {
        if (columnMeta instanceof BaseDataFieldMeta)
            return (BaseDataFieldMeta) columnMeta;

        return new BaseDataFieldMeta(columnMeta.getFieldName(), columnMeta.getSourceFieldName(),
                columnMeta.getFieldOwnerEntityName(), columnMeta.getStdDataType(), columnMeta.getStdSqlType(), columnMeta.isComputed());
    }

    public static List<BaseDataFieldMeta> fromColumnMetas(List<? extends IDataFieldMeta> metas) {
        List<BaseDataFieldMeta> result = new ArrayList<>(metas.size());
        for (IDataFieldMeta meta : metas) {
            result.add(fromColumnMeta(meta));
        }
        return result;
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
    public StdDataType getStdDataType() {
        return stdDataType;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Override
    public String getSourceFieldName() {
        return sourceFieldName;
    }

    @Override
    public StdSqlType getStdSqlType() {
        return stdSqlType;
    }

    public BaseDataFieldMeta renameTo(String newName) {
        return new BaseDataFieldMeta(newName, sourceFieldName, fieldOwnerEntityName, stdDataType, stdSqlType, computed);
    }
}