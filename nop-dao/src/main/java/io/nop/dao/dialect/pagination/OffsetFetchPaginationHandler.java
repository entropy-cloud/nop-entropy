/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect.pagination;

import io.nop.api.core.beans.LongRangeBean;
import io.nop.core.lang.sql.ISqlExpr;
import io.nop.core.lang.sql.SQL;
import io.nop.core.lang.sql.SqlExprList;

public class OffsetFetchPaginationHandler extends AbstractPaginationHandler {
    public static final OffsetFetchPaginationHandler INSTANCE = new OffsetFetchPaginationHandler();

    @Override
    public SQL getPagedSql(LongRangeBean bounds, SQL sql) {
        if (bounds.getLimit() > 0 || bounds.getOffset() > 0) {
            SQL.SqlBuilder sb = SQL.begin(sql);

            if (bounds.getOffset() > 0) {
                sb.sql(" OFFSET ? ROWS ", bounds.getOffset());
            } else {
                sb.append(" OFFSET 0 ROWS ");
            }

            if (bounds.getLimit() > 0)
                sb.sql(" FETCH NEXT ? ROWS ONLY", bounds.getLimit());

            return sb.end();
        }
        return sql;
    }

    @Override
    public SqlExprList buildPageExpr(ISqlExpr limitExpr, ISqlExpr offsetExpr, ISqlExpr sqlExpr) {
        SqlExprList exprs = new SqlExprList("page");
        exprs.add(sqlExpr);
        if (offsetExpr != null) {
            exprs.add(" OFFSET ").add(offsetExpr).add(" ROWS ");
        }
        if (limitExpr != null) {
            exprs.add(" FETCH ").add(offsetExpr == null ? " FIRST " : " NEXT ").add(limitExpr).add(" ROWS ONLY");
        }
        return exprs;
    }
}