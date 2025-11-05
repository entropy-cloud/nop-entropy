/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.sql;

import io.nop.commons.text.marker.IMarkedString;
import io.nop.core.lang.sql.SQL;
import io.nop.core.lang.sql.SyntaxMarker;

public interface IEntityFilterProvider {
    IMarkedString getEntityFilter(SyntaxMarker marker, SQL sql, ISqlCompileTool compiler);
}
