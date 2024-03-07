/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.eql.compile;

import io.nop.dao.dialect.IDialect;
import io.nop.orm.eql.IEqlAstTransformer;
import io.nop.orm.eql.meta.ISqlTableMeta;
import io.nop.orm.eql.sql.IAliasGenerator;

public interface ISqlCompileContext {
    boolean isDisableLogicalDelete();

    boolean isAllowUnderscoreName();

    IEqlAstTransformer getAstTransformer();

    IDialect getDialectForQuerySpace(String querySpace);

    ISqlTableMeta resolveEntityTableMeta(String entityName);

    IAliasGenerator getAliasGenerator();
}