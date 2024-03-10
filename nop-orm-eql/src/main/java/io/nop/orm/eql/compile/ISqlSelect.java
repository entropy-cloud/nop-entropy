/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.orm.eql.ast.SqlProjection;

/**
 * @author canonical_entropy@163.com
 */
public interface ISqlSelect extends ISqlTableScopeSupport {
    /**
     * 判断alias是自动生成的别名
     */
    boolean isGeneratedTableAlias(String alias);

    boolean isGeneratedProjectionAlias(String alias);

    SqlProjection getProjectionByAlias(String alias);
}