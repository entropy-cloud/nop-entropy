/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.imp.model;

import io.nop.api.core.util.INeedInit;
import io.nop.commons.util.StringHelper;
import io.nop.core.type.IGenericType;
import io.nop.excel.imp.model._gen._ImportFieldModel;
import io.nop.xlang.xmeta.ISchema;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.excel.imp.util.ImportDataHelper.normalizeFieldLabel;

public class ImportFieldModel extends _ImportFieldModel implements INeedInit, IFieldContainer {
    private Map<String, ImportFieldModel> fieldNameMap;
    private IFieldContainer fieldContainer;

    public ImportFieldModel() {

    }

    public IFieldContainer getFieldContainer() {
        return fieldContainer;
    }

    public void setFieldContainer(IFieldContainer fieldContainer) {
        this.fieldContainer = fieldContainer;
    }

    public String getContainerObjName() {
        IFieldContainer container = this.fieldContainer;
        if (container == null)
            return null;
        return container.getBizObjName();
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

    public ImportFieldModel getFieldModel(String name) {
        ImportFieldModel field = getField(name);
        if (field != null)
            return field;

        Map<String, ImportFieldModel> fields = getFieldNameMap();
        if (fields == null)
            return null;
        field = fields.get(name);
        if (field == null)
            field = fields.get(normalizeFieldLabel(name));
        return field;
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

        if (getFields() != null) {
            for (ImportFieldModel field : getFields()) {
                field.setFieldContainer(this);
            }
        }

        if (getUnknownField() != null) {
            getUnknownField().setFieldContainer(this);
        }
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

    public String getDisplayNameOrName() {
        String displayName = getDisplayName();
        if (displayName == null)
            return getName();
        return displayName;
    }

    @Override
    public String getPropOrName() {
        String prop = getProp();
        if (!StringHelper.isEmpty(prop))
            return prop;
        return getName();
    }
}
