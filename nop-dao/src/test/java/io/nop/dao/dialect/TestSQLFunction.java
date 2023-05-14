/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.dialect;

import io.nop.core.lang.sql.SqlExprList;
import io.nop.commons.type.StdSqlType;
import io.nop.dao.dialect.function.TemplateSQLFunction;
import io.nop.dao.dialect.model.SqlTemplateModel;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static io.nop.core.lang.sql.StringSqlExpr.makeExpr;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestSQLFunction {
    @Test
    public void testTemplate() {
        SqlTemplateModel model = new SqlTemplateModel();
        model.setName("test");
        model.setSource("func({0},{1},{a:1})");
        model.setArgTypes(Arrays.asList(StdSqlType.ANY, StdSqlType.ANY));
        TemplateSQLFunction fn = new TemplateSQLFunction(model);
        SqlExprList expr = fn.buildFunctionExpr(null, asList(makeExpr("a"), makeExpr("b")), null);
        assertEquals("func(a,b,{a:1})", expr.getSqlString());
    }
}
