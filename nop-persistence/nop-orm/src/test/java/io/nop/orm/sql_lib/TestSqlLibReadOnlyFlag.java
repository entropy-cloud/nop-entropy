/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.sql_lib;

import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.sql.SQL;
import io.nop.orm.AbstractJdbcTestCase;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2319 Phase 3：sql-lib readOnly属性经生成链（sql-lib.xdef → _SqlItemModel regen →
 * SqlItemModel.buildSql传播）端到端可达。
 */
public class TestSqlLibReadOnlyFlag extends AbstractJdbcTestCase {

    @Test
    public void testReadOnlyAttrParsedFromXdefAndPropagatedToSql() {
        IEvalScope scope = XLang.newEvalScope();

        SQL readOnly = sqlLibManager.buildSql("readonly.findAllReadOnly", scope);
        assertTrue(readOnly.isReadOnly(), "readOnly=true in sql-lib must set SQL.readOnly");

        SQL plain = sqlLibManager.buildSql("readonly.findAllDefault", scope);
        assertFalse(plain.isReadOnly(), "sql item without readOnly must default to false (fail-safe primary)");
    }
}
