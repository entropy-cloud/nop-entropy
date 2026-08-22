/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.meta;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.PropPath;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static io.nop.orm.eql.OrmEqlErrors.ARG_PROP_NAME;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_UNKNOWN_FIELD_IN_SELECTION;

public class SelectResultTableMeta implements ISqlSelectionMeta {
    private final Map<String, ISqlExprMeta> fieldExprMetas;

    /**
     * 按下划线形式索引子查询结果字段，与实体字段的allowUnderscoreName访问语义保持一致
     */
    private final Map<String, ISqlExprMeta> underscoreFieldExprMetas;

    public SelectResultTableMeta(Map<String, ISqlExprMeta> fieldExprMetas) {
        this.fieldExprMetas = fieldExprMetas;
        this.underscoreFieldExprMetas = buildUnderscoreIndex(fieldExprMetas);
    }

    private static Map<String, ISqlExprMeta> buildUnderscoreIndex(Map<String, ISqlExprMeta> fieldExprMetas) {
        Map<String, ISqlExprMeta> index = new HashMap<>(fieldExprMetas.size());
        for (Map.Entry<String, ISqlExprMeta> entry : fieldExprMetas.entrySet()) {
            index.putIfAbsent(StringHelper.camelCaseToUnderscore(entry.getKey(), true), entry.getValue());
        }
        return index;
    }

    @Override
    public Map<String, ISqlExprMeta> getFieldExprMetas() {
        return fieldExprMetas;
    }

    @Override
    public ISqlExprMeta getFieldExprMeta(String name, boolean allowUnderscoreName) {
        ISqlExprMeta exprMeta = fieldExprMetas.get(name);
        if (exprMeta == null && allowUnderscoreName)
            exprMeta = underscoreFieldExprMetas.get(name.toLowerCase(Locale.ROOT));
        return exprMeta;
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
