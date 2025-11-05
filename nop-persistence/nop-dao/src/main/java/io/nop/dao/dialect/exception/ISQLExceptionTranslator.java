/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect.exception;

import io.nop.core.lang.sql.SQL;
import io.nop.dao.exceptions.JdbcException;

import java.sql.SQLException;

public interface ISQLExceptionTranslator {
    JdbcException translate(SQL sql, SQLException ex);

    JdbcException translate(String action, SQLException ex);
}