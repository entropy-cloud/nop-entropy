/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.eql.meta;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.objects.PropPath;
import io.nop.orm.model.IEntityPropModel;
import io.nop.orm.model.IOrmDataType;

import java.util.Map;

import static io.nop.orm.eql.OrmEqlErrors.ARG_FIELD_NAME;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_FIELD_NOT_PROP;

/**
 * 统一描述实体表和子查询的结果集的字段结构
 */
public interface ISqlSelectionMeta {

    Map<String, ISqlExprMeta> getFieldExprMetas();

    ISqlExprMeta getFieldExprMeta(String name);

    ISqlExprMeta requireFieldExprMeta(String name);

    default IEntityPropModel requirePropMeta(String name) {
        IOrmDataType dataType = requireFieldExprMeta(name).getOrmDataType();
        if (!(dataType instanceof IEntityPropModel))
            throw new NopException(ERR_EQL_FIELD_NOT_PROP)
                    .param(ARG_FIELD_NAME, name);
        return (IEntityPropModel) dataType;
    }

    PropPath getAliasPropPath(String name);
}
