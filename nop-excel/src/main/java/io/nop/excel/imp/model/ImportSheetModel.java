/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.imp.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.INeedInit;
import io.nop.core.type.IGenericType;
import io.nop.excel.imp.model._gen._ImportSheetModel;

import java.util.Map;
import java.util.regex.Pattern;

import static io.nop.excel.ExcelErrors.ARG_SHEET_NAME;
import static io.nop.excel.ExcelErrors.ERR_IMPORT_LIST_SHEET_MODEL_MUST_HAS_FIELD_ATTR;
import static io.nop.excel.imp.util.ImportDataHelper.normalizeFieldLabel;

public class ImportSheetModel extends _ImportSheetModel implements INeedInit, IFieldContainer {
    private Pattern compiledNamePattern;

    private Map<String, ImportFieldModel> fieldNameMap;

    public ImportSheetModel() {

    }

    public void initStripText(boolean defaultStripText) {
        getFields().forEach(field -> field.initStripText(defaultStripText));
    }

    public boolean matchName(String name) {
        if (getNamePattern() != null)
            return matchNamePattern(name);

        if (getName().equals(name))
            return true;

        if (getAlias() != null)
            return getAlias().contains(name);

        return false;
    }

    @Override
    public String getFieldLabel() {
        return getName();
    }

    public String getFieldName() {
        return getField();
    }

    private Pattern getCompiledNamePattern() {
        if (compiledNamePattern != null)
            return compiledNamePattern;

        String pattern = getNamePattern();
        if (pattern == null)
            return null;

        compiledNamePattern = Pattern.compile(pattern);
        return compiledNamePattern;
    }

    public IGenericType getResultComponentType() {
        IGenericType type = getResultType();
        return type == null ? null : type.getComponentType();
    }

    private boolean matchNamePattern(String name) {
        Pattern pattern = getCompiledNamePattern();
        return pattern.matcher(name).matches();
    }

    @Override
    public ImportFieldModel getFieldModel(String name) {
        ImportFieldModel field = getField(name);
        if (field == null) {
            if (getFieldNameMap() == null)
                return null;
            field = getFieldNameMap().get(name);
            if (field == null)
                field = getFieldNameMap().get(normalizeFieldLabel(name));
        }
        return field;
    }

    public Map<String, ImportFieldModel> getFieldNameMap() {
        return fieldNameMap;
    }

    @Override
    public void init() {
        this.fieldNameMap = ImportFieldModel.toNameMap(getFields(), getUnknownField());
        if (isList()) {
            if (getField() == null)
                throw new NopException(ERR_IMPORT_LIST_SHEET_MODEL_MUST_HAS_FIELD_ATTR).source(this)
                        .param(ARG_SHEET_NAME, getName());
        }


        if (getFields() != null) {
            for (ImportFieldModel field : getFields()) {
                field.setFieldContainer(this);
            }
        }

        if (getUnknownField() != null) {
            getUnknownField().setFieldContainer(this);
        }
    }
}
