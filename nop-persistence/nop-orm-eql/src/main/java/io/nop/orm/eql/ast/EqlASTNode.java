/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.ast;

import io.nop.core.lang.ast.ASTNode;
import io.nop.core.lang.sql.ISqlExpr;
import io.nop.core.lang.sql.SQL;
import io.nop.orm.eql.sql.AstToEqlGenerator;

public abstract class EqlASTNode extends ASTNode<EqlASTNode> implements ISqlExpr {

    public abstract EqlASTKind getASTKind();

    public String getASTType() {
        return getASTKind().toString();
    }

    public abstract EqlASTNode deepClone();

    @Override
    public void appendTo(SQL.SqlBuilder sb) {
        AstToEqlGenerator visitor = new AstToEqlGenerator(sb);
        visitor.visit(this);
    }

    public String toSqlString(){
        SQL.SqlBuilder sb = SQL.begin();
        appendTo(sb);
        return sb.getText();
    }

    public SQL toSQL() {
        SQL.SqlBuilder sb = SQL.begin();
        appendTo(sb);
        return sb.end();
    }
}
