/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.api;

import io.nop.api.core.beans.LongRangeBean;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.dataset.IRowMapper;
import io.nop.dao.dataset.rowmapper.RowMapperFirstExtractor;

public abstract class AbstractSqlExecutor implements ISqlExecutor {
    /**
     * 实现findFirst方法时通过此函数标记只数据集的第一条数据，但是不使用分页语法
     */
    protected static LongRangeBean FIND_FIRST_RANGE = LongRangeBean.of(0, 1);

    /**
     * 判断数据是否存在
     */
    public boolean exists(SQL sql) {
        return executeQuery(sql, FIND_FIRST_RANGE, ds -> ds.hasNext());
    }

    public <T> T findFirst(SQL sql, IRowMapper<T> rowMapper) {
        return executeQuery(sql, FIND_FIRST_RANGE, new RowMapperFirstExtractor<>(rowMapper));
    }
}
