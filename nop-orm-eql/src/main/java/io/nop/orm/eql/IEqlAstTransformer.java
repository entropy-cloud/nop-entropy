/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql;

import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.compile.ISqlCompileContext;

public interface IEqlAstTransformer {
    void transformBeforeAnalyze(SqlProgram ast, String name, String sql, ISqlCompileContext context);

    void transformAfterAnalyze(SqlProgram ast, String name, String sql, ISqlCompileContext context);
}
