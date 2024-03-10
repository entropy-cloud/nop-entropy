/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.impl;

import io.nop.core.lang.sql.SQL;
import io.nop.orm.AbstractOrmTestCase;
import org.junit.jupiter.api.Test;

public class TestComplexSql extends AbstractOrmTestCase {
    @Test
    public void testRightJoin() {
        String sql = "SELECT\n" +
                "                o.id,\n" +
                "\n" +
                "                t.id modelId\n" +
                "\n" +
                "                FROM\n" +
                "            SimsClass o\n" +
                "            RIGHT JOIN (\n" +
                "            SELECT c.*,\n" +
                "            b.examId\n" +
                "\n" +
                "            FROM SimsCollege c\n" +
                "            INNER JOIN SimsExam b\n" +
                "            ON b.examId = c.collegeId\n" +
                "\n" +
                "\n" +
                "            ) t\n" +
                "            ON t.collegeId = o.collegeId\n" +
                "            AND t.id = o.classId";

        orm().findFirst(SQL.begin().sql(sql).end());
    }
}
