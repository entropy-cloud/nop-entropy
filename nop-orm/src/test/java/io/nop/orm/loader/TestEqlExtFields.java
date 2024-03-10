/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.loader;

import io.nop.app.SimsExam;
import io.nop.core.lang.sql.SQL;
import io.nop.orm.AbstractOrmTestCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestEqlExtFields extends AbstractOrmTestCase {

    @DisplayName("利用keyProp实现与一对多关联表的隐式关联")
    @Test
    public void testToManyQuery() {
        orm().runInSession(() -> {
            SimsExam exam = daoProvider().daoFor(SimsExam.class).newEntity();
            exam.setExamId("101");
            exam.setExamName("exam");
            exam.setExtFldA("x");
            exam.orm_propValueByName("extFldB", true);
            orm().save(exam);
        });

        orm().runInSession(() -> {
            String sql = "select o, o.extFldA, o.extFldB*2 +3 from io.nop.app.SimsExam o "
                    + "where  o.extFldB = 1 and o.extFldA = 'x' order by o.extFldA";

            Map<String, Object> map = orm().findFirst(SQL.begin().sql(sql).end());
            SimsExam exam = (SimsExam) map.get("o");
            String fldA = (String) map.get("extFldA");
            assertEquals("x", fldA);
            assertEquals("exam", exam.getExamName());
            assertEquals("x", exam.getExtFldA());
            assertEquals(true, exam.orm_propValueByName("extFldB"));
        });
    }
}