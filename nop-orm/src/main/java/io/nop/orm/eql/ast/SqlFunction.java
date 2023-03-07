/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.eql.ast;

import io.nop.dao.dialect.function.ISQLFunction;
import io.nop.orm.eql.ast._gen._SqlFunction;

public abstract class SqlFunction extends _SqlFunction {
    private ISQLFunction resolvedFunction;

    public ISQLFunction getResolvedFunction() {
        return resolvedFunction;
    }

    public void setResolvedFunction(ISQLFunction resolvedFunction) {
        this.resolvedFunction = resolvedFunction;
    }
}
