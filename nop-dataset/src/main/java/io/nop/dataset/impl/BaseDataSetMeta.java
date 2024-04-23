/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dataset.impl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.data.ImmutableBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdDataType;
import io.nop.commons.util.CollectionHelper;
import io.nop.dataset.IDataFieldMeta;
import io.nop.dataset.IDataSetMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.nop.dataset.DataSetErrors.ARG_COL_NAME;
import static io.nop.dataset.DataSetErrors.ERR_DATASET_UNKNOWN_COLUMN;

@DataBean
@ImmutableBean
public class BaseDataSetMeta implements IDataSetMeta {
    private static final long serialVersionUID = -7799138095831186561L;

    private final List<BaseDataFieldMeta> fieldMetas;

    private final Map<String, Integer> nameToIndex;

    private final Map<String, Object> headerMeta;

    private final Map<String, Object> trailerMeta;

    private final List<String> headers;

    private final boolean caseInsensitive;

    public BaseDataSetMeta(@JsonProperty("fieldMetas") List<BaseDataFieldMeta> fieldMetas,
                           @JsonProperty("caseInsensitive") boolean caseInsensitive,
                           @JsonProperty("headerMeta") Map<String, Object> headerMeta,
                           @JsonProperty("trailerMeta") Map<String, Object> trailerMeta) {
        this.caseInsensitive = caseInsensitive;
        this.nameToIndex = caseInsensitive ? CollectionHelper.newCaseInsensitiveMap(fieldMetas.size())
                : CollectionHelper.newHashMap(fieldMetas.size());
        for (int i = 0, n = fieldMetas.size(); i < n; i++) {
            IDataFieldMeta colMeta = fieldMetas.get(i);
            this.nameToIndex.putIfAbsent(colMeta.getFieldName(), i);
        }
        this.fieldMetas = CollectionHelper.toUnmodifiableList(fieldMetas);
        this.headerMeta = headerMeta == null ? Collections.emptyMap() : CollectionHelper.toUnmodifiableMap(headerMeta);

        List<String> headers = new ArrayList<>(fieldMetas.size());
        for (IDataFieldMeta columnMeta : fieldMetas) {
            headers.add(columnMeta.getFieldName());
        }
        this.headers = Collections.unmodifiableList(headers);
        this.trailerMeta = trailerMeta == null ? Collections.emptyMap()
                : CollectionHelper.toUnmodifiableMap(trailerMeta);
    }

    public BaseDataSetMeta(List<BaseDataFieldMeta> fieldMetas) {
        this(fieldMetas, false, null, null);
    }

    public BaseDataSetMeta(IDataSetMeta dataSetMeta) {
        this(BaseDataFieldMeta.fromColumnMetas(dataSetMeta.getFieldMetas()),
                dataSetMeta.isCaseInsensitive(), dataSetMeta.getHeaderMeta(), dataSetMeta.getTrailerMeta());
    }

    public static BaseDataSetMeta fromColNames(String[] columnNames) {
        List<BaseDataFieldMeta> colMetas = new ArrayList<>(columnNames.length);
        for (int i = 0, n = columnNames.length; i < n; i++) {
            BaseDataFieldMeta colMeta = new BaseDataFieldMeta(
                    columnNames[i], null, null, StdDataType.ANY, false);
            colMetas.add(colMeta);
        }
        return new BaseDataSetMeta(colMetas);
    }

    public static BaseDataSetMeta fromMeta(IDataSetMeta meta) {
        if (meta instanceof BaseDataSetMeta)
            return (BaseDataSetMeta) meta;

        List<BaseDataFieldMeta> columns = new ArrayList<>(meta.getFieldCount());
        for (IDataFieldMeta column : meta.getFieldMetas()) {
            columns.add(BaseDataFieldMeta.fromColumnMeta(column));
        }
        return new BaseDataSetMeta(columns, meta.isCaseInsensitive(), meta.getHeaderMeta(), meta.getTrailerMeta());
    }

    @Override
    public IDataSetMeta projectWithRename(Map<String, String> new2old) {
        List<BaseDataFieldMeta> newColumns = new ArrayList<>(new2old.size());
        new2old.forEach((newName, oldName) -> {
            BaseDataFieldMeta field = fieldMetas.get(getFieldIndex(oldName));
            if (field != null) {
                newColumns.add(field);
            }
        });
        return new BaseDataSetMeta(newColumns, caseInsensitive, headerMeta, trailerMeta);
    }

    @Override
    public boolean isCaseInsensitive() {
        return caseInsensitive;
    }

    @Override
    public int getFieldCount() {
        return fieldMetas.size();
    }

    @Override
    public String getFieldName(int index) {
        return fieldMetas.get(index).getFieldName();
    }

    @Override
    public String getSourceFieldName(int index) {
        return fieldMetas.get(index).getSourceFieldName();
    }

    @Override
    public String getFieldOwnerEntityName(int index) {
        return fieldMetas.get(index).getFieldOwnerEntityName();
    }

    @Override
    public int getFieldIndex(String colName) {
        Integer index = nameToIndex.get(colName);
        if (index == null)
            throw new NopException(ERR_DATASET_UNKNOWN_COLUMN).param(ARG_COL_NAME, colName);
        return index;
    }

    @Override
    public StdDataType getFieldStdType(int index) {
        return fieldMetas.get(index).getFieldStdType();
    }

    @Override
    public boolean hasField(String colName) {
        return nameToIndex.containsKey(colName);
    }

    @Override
    public BaseDataSetMeta toBaseDataSetMeta() {
        return this;
    }

    @JsonIgnore
    @Override
    public List<String> getHeaders() {
        return headers;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Override
    public Map<String, Object> getHeaderMeta() {
        return headerMeta;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Override
    public Map<String, Object> getTrailerMeta() {
        return trailerMeta;
    }

    @Override
    public List<BaseDataFieldMeta> getFieldMetas() {
        return fieldMetas;
    }
}