/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.excel.imp.model;

import io.nop.core.lang.eval.IEvalAction;

import java.util.Map;

public interface IFieldContainer {
    String getKeyProp();

    boolean isList();

    default boolean isMultiple() {
        return false;
    }

    String getFieldName();

    default String getPropOrName() {
        return getFieldName();
    }

    IEvalAction getNormalizeFieldsExpr();

    String getFieldLabel();

    Map<String, ImportFieldModel> getFieldNameMap();

    default ImportFieldModel getFieldModel(String name) {
        Map<String, ImportFieldModel> fields = getFieldNameMap();
        if (fields == null)
            return null;
        return fields.get(name);
    }
}