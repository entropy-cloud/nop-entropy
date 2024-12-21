/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.imp.model;

import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalFunction;

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

    ImportFieldModel getUnknownField();

    ImportFieldModel getFieldModel(String name);

    IEvalFunction getFieldDecider();

    int getHeaderRowCount();

    boolean isNoSeqCol();
}