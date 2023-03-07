/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.excel.imp.model;

import io.nop.api.core.util.INeedInit;
import io.nop.core.type.IGenericType;
import io.nop.excel.imp.model._gen._ImportFieldModel;
import io.nop.xlang.xmeta.ISchema;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ImportFieldModel extends _ImportFieldModel implements INeedInit, IFieldContainer {
    private Map<String, ImportFieldModel> fieldNameMap;

    public ImportFieldModel() {

    }

    public void initStripText(boolean defaultStripText) {
        if (getStripText() == null) {
            setStripText(defaultStripText);
        }
        getFields().forEach(field -> field.initStripText(defaultStripText));
    }

    @Override
    public String getFieldName() {
        return getName();
    }

    @Override
    public String getFieldLabel() {
        return getDisplayName();
    }

    public Map<String, ImportFieldModel> getFieldNameMap() {
        return fieldNameMap;
    }

    public IGenericType getResultType() {
        ISchema schema = getSchema();
        return schema == null ? null : schema.getType();
    }

    public IGenericType getResultComponentType() {
        IGenericType type = getResultType();
        return type == null ? null : type.getComponentType();
    }

    @Override
    public void init() {
        this.fieldNameMap = toNameMap(getFields(), getUnknownField());
    }

    public static Map<String, ImportFieldModel> toNameMap(List<ImportFieldModel> fields,
                                                          ImportFieldModel unknownField) {
        if (fields == null) {
            if (unknownField == null)
                return Collections.emptyMap();
            return Collections.singletonMap("*", unknownField);
        }

        Map<String, ImportFieldModel> fieldNameMap = new HashMap<>();
        for (ImportFieldModel field : fields) {
            field.init();
            String name = field.getDisplayName();
            if (name == null)
                name = field.getName();
            fieldNameMap.put(name, field);

            if (field.getAlias() != null) {
                for (String alias : field.getAlias()) {
                    fieldNameMap.put(alias, field);
                }
            }
        }
        if (unknownField != null)
            fieldNameMap.put("*", unknownField);
        return fieldNameMap;
    }
}
