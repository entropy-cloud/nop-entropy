/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.utils;

import io.nop.commons.mutable.MutableInt;
import io.nop.core.lang.sql.SQL;
import io.nop.core.lang.sql.SqlHelper;
import io.nop.dao.api.ISqlExecutor;

import java.util.List;

public class SqlExecHelper {
    public static long executeMultiSql(ISqlExecutor sqlExecutor, SQL sql) {
        if (sql.getText().indexOf(';') < 0)
            return sqlExecutor.executeUpdate(sql);

        MutableInt index = new MutableInt();
        List<SQL> sqls = SqlHelper.splitSql(sql, () -> SQL.begin().querySpace(sql.getQuerySpace())
                .timeout(sql.getTimeout()).name(createName(sql.getName(), index)));
        if (sqls.size() == 1) {
            return sqlExecutor.executeUpdate(sql);
        }

        long total = 0;
        boolean resultInvalid = false;
        for (SQL subSql : sqls) {
            long count = sqlExecutor.executeUpdate(subSql);
            if (count < 0) {
                resultInvalid = true;
            } else {
                total += count;
            }
        }
        if (total > 0)
            return total;
        return resultInvalid ? -1 : total;
    }

    private static String createName(String name, MutableInt index) {
        if (name == null)
            return null;
        return name + ":" + index.getAndIncrement();
    }
}
