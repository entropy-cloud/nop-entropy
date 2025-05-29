/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.ast;

import io.nop.commons.type.StdSqlType;
import io.nop.orm.eql.ast._gen._SqlHexadecimalLiteral;
import io.nop.xlang.ast.Literal;

public class SqlHexadecimalLiteral extends _SqlHexadecimalLiteral {
    @Override
    public StdSqlType getSqlType() {
        return StdSqlType.BIGINT;
    }

    @Override
    public Object getLiteralValue() {
        String str = getValue();
        if (str.startsWith("0x")) {
            str = str.substring(2);
        } else if (str.startsWith("X")) {
            str = str.substring(1);
        } else {
            throw new IllegalArgumentException("invalid hex value；" + getValue());
        }
        int value = Integer.decode(str);
        return Literal.numberValue(getLocation(), value);
    }
}
