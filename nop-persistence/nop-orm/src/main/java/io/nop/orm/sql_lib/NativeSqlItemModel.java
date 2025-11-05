/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.sql_lib;

import io.nop.commons.text.marker.IMarkedString;
import io.nop.core.context.IEvalContext;
import io.nop.orm.sql_lib._gen._NativeSqlItemModel;

public class NativeSqlItemModel extends _NativeSqlItemModel {
    public NativeSqlItemModel() {

    }

    @Override
    protected IMarkedString generateSql(IEvalContext context) {
        return getSource().generateSql(context);
    }
}
