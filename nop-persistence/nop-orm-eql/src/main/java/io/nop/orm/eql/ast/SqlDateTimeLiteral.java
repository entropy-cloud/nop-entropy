/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.ast;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdSqlType;
import io.nop.orm.eql.ast._gen._SqlDateTimeLiteral;

public class SqlDateTimeLiteral extends _SqlDateTimeLiteral {
    @Override
    public StdSqlType getSqlType() {
        return StdSqlType.DATETIME;
    }

    @Override
    public Object getLiteralValue() {
        switch (getType()) {
            case DATE:
                return ConvertHelper.toLocalDate(getValue());
            case TIME:
                return ConvertHelper.toLocalTime(getValue(), NopException::new);
            case TIMESTAMP:
                return ConvertHelper.toTimestamp(getValue());
            default:
                throw new IllegalStateException("Unknown type: " + getType());
        }
    }
}
