/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.jdbc;

import io.nop.dao.txn.ITransactionTemplate;

import javax.sql.DataSource;

public interface IJdbcTemplateFactory {

    default IJdbcTemplate newJdbcTemplate(DataSource dataSource) {
        return newJdbcTemplate(dataSource, null);
    }

    default IJdbcTemplate newJdbcTemplate(DataSource dataSource, String dialectName) {
        return newJdbcTemplate(newTransactionTemplate(dataSource, dialectName));
    }

    default ITransactionTemplate newTransactionTemplate(DataSource dataSource) {
        return newTransactionTemplate(dataSource, null);
    }

    ITransactionTemplate newTransactionTemplate(DataSource dataSource, String dialectName);

    IJdbcTemplate newJdbcTemplate(ITransactionTemplate transactionTemplate);
}
