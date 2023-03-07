/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.eql.utils;

import io.nop.core.lang.sql.SQL;
import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.orm.eql.ast.SqlAlias;
import io.nop.orm.eql.ast.SqlColumnName;
import io.nop.orm.eql.ast.SqlExpr;
import io.nop.orm.eql.ast.SqlExprProjection;
import io.nop.orm.eql.sql.AstToEqlGenerator;

public class EqlHelper {
    public static SQL.SqlBuilder getExprSql(SqlExpr expr) {
        AstToEqlGenerator gen = new AstToEqlGenerator();
        gen.setPretty(false);
        gen.visit(expr);
        return gen.getSql();
    }

    public static String getFieldName(SqlExprProjection exprProj) {
        String fieldName = exprProj.getFieldName();
        if (fieldName == null) {
            SqlAlias alias = exprProj.getAlias();

            if (alias == null || alias.isGenerated()) {
                SqlExpr expr = exprProj.getExpr();
                if (expr.getASTKind() == EqlASTKind.SqlColumnName) {
                    fieldName = ((SqlColumnName) expr).getName();
                } else {
                    fieldName = EqlHelper.getExprSql(expr).getText();
                }
            } else {
                fieldName = alias.getAlias();
            }
            exprProj.setFieldName(fieldName);
        }
        return fieldName;
    }

    public static String getAlias(String alias, int index, int count) {
        if (alias == null)
            return null;
        if (count == 1)
            return alias;
        return alias + '_' + index;
    }
}
