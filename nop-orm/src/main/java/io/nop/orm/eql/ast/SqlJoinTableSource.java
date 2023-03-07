/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.eql.ast;

import io.nop.orm.eql.ast._gen._SqlJoinTableSource;
import io.nop.orm.eql.meta.ISqlTableMeta;

public class SqlJoinTableSource extends _SqlJoinTableSource {
    @Override
    public ISqlTableMeta getResolvedTableMeta() {
        return getLeft().getResolvedTableMeta();
    }

    @Override
    public SqlSelect getSourceSelect() {
        return null;
    }
}
