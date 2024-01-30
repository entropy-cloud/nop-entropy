/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.sql_lib;

import io.nop.api.core.beans.LongRangeBean;
import io.nop.app.SimsClass;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.sql.SQL;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestOrmSqlLib extends AbstractOrmTestCase {

    @Test
    public void testSqlFilterTag() {
        orm().runInSession(() -> {
            IEvalScope scope = XLang.newEvalScope();
            scope.setLocalValue(null, "ids", Arrays.asList(11, 1112));
            scope.setLocalValue(null, "unknownParam", null);
            List<SimsClass> list = (List<SimsClass>) sqlLibManager.invoke("test.findBySqlFilter",
                    LongRangeBean.of(0, 1), scope);
            assertEquals(1, list.size());
            SimsClass entity = list.get(0);
            assertEquals("11", entity.getClassId());
        });
    }

    @Test
    public void checkDialectCondition() {
        sqlLibManager.invoke("test2.findWithDialect",
                LongRangeBean.of(0, 1), XLang.newEvalScope());
    }

    @Test
    public void testFragment() {
        SQL sql = sqlLibManager.buildSql("test2.generateWithFragment", XLang.newEvalScope());
        assertEquals("select a, b, c from MyTable o", sql.getText().trim());
    }
}
