/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.meta;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.objects.PropPath;

import java.util.Map;

import static io.nop.orm.eql.OrmEqlErrors.ARG_PROP_NAME;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_UNKNOWN_FIELD_IN_SELECTION;

public class SelectResultTableMeta implements ISqlSelectionMeta {
    private final Map<String, ISqlExprMeta> fieldExprMetas;

    public SelectResultTableMeta(Map<String, ISqlExprMeta> fieldExprMetas) {
        this.fieldExprMetas = fieldExprMetas;
    }

    @Override
    public Map<String, ISqlExprMeta> getFieldExprMetas() {
        return fieldExprMetas;
    }

    @Override
    public ISqlExprMeta getFieldExprMeta(String name, boolean allowUnderscoreName) {
        return fieldExprMetas.get(name);
    }

    @Override
    public ISqlExprMeta requireFieldExprMeta(String name, boolean allowUnderscoreName) {
        ISqlExprMeta exprMeta = getFieldExprMeta(name, allowUnderscoreName);
        if (exprMeta == null)
            throw new NopException(ERR_EQL_UNKNOWN_FIELD_IN_SELECTION)
                    .param(ARG_PROP_NAME, name);
        return exprMeta;
    }

    @Override
    public PropPath getAliasPropPath(String name) {
        return null;
    }
}
