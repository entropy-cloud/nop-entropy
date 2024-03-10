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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public interface IPaginationHandler {
    SQL getPagedSql(LongRangeBean range, SQL sql);

    SqlExprList buildPageExpr(ISqlExpr limitExpr, ISqlExpr offsetExpr, ISqlExpr sqlExpr);

    void prepareStatement(LongRangeBean range, PreparedStatement ps) throws SQLException;

    void prepareResultSet(LongRangeBean range, ResultSet rs) throws SQLException;
}