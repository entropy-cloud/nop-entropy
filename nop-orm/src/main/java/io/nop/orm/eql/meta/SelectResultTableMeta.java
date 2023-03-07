/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.eql.meta;

import io.nop.commons.util.objects.PropPath;

import java.util.Map;

public class SelectResultTableMeta implements ISqlTableMeta {
    private final Map<String, ISqlExprMeta> fieldExprMetas;

    public SelectResultTableMeta(Map<String, ISqlExprMeta> fieldExprMetas) {
        this.fieldExprMetas = fieldExprMetas;
    }

    @Override
    public Map<String, ISqlExprMeta> getFieldExprMetas() {
        return fieldExprMetas;
    }

    @Override
    public ISqlExprMeta getFieldExprMeta(String name) {
        return fieldExprMetas.get(name);
    }

    @Override
    public PropPath getAliasPropPath(String name) {
        return null;
    }
}
