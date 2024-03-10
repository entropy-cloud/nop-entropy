/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect.pagination;

import io.nop.api.core.beans.LongRangeBean;
import io.nop.core.lang.sql.SQL;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public abstract class AbstractPaginationHandler implements IPaginationHandler {

    @Override
    public SQL getPagedSql(LongRangeBean bounds, SQL sql) {
        return sql;
    }

    @Override
    public void prepareResultSet(LongRangeBean bounds, ResultSet rs) throws SQLException {
    }

    @Override
    public void prepareStatement(LongRangeBean bounds, PreparedStatement ps) throws SQLException {
        if (bounds.getLimit() > 0) {
            long limit = bounds.getLimit();
            ps.setMaxRows((int) limit);
        }
    }
}