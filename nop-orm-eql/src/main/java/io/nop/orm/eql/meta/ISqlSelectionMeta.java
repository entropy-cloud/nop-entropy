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

/**
 * 统一描述实体表和子查询的结果集的字段结构
 */
public interface ISqlSelectionMeta {

    Map<String, ISqlExprMeta> getFieldExprMetas();

    ISqlExprMeta getFieldExprMeta(String name);

    PropPath getAliasPropPath(String name);
}
