/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.ast;

import io.nop.commons.type.StdSqlType;
import io.nop.commons.util.StringHelper;
import io.nop.orm.eql.ast._gen._SqlNumberLiteral;

public class SqlNumberLiteral extends _SqlNumberLiteral {

    public Number getNumberValue() {
        return StringHelper.parseNumber(getValue());
    }

    @Override
    public StdSqlType getSqlType() {
        Number num = getNumberValue();
        StdSqlType sqlType = StdSqlType.fromJavaClass(num.getClass());
        return sqlType == null ? StdSqlType.DECIMAL : sqlType;
    }
}
