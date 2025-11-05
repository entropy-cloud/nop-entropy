/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.dao;

import io.nop.app.SimsExam;
import io.nop.commons.CommonConstants;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.AbstractOrmTestCase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestColumnEnhancer extends AbstractOrmTestCase {
    @Test
    public void testEncryptedColumn() {
        IEntityDao<SimsExam> dao = daoProvider().daoFor(SimsExam.class);
        SimsExam entity = new SimsExam();
        entity.setExamId("101");
        entity.setExamName("testExam");
        dao.saveEntity(entity);

        entity = dao.getEntityById("101");
        assertEquals("testExam", entity.getExamName());

        SimsExam example = new SimsExam();
        example.setExamName("testExam");

        List<SimsExam> list = dao.findAllByExample(example);
        assertEquals(1, list.size());

        Map<String, Object> row = jdbc().findFirst(new SQL("select * from sims_exam"));
        System.out.println(row);
        String examName = (String) row.get("EXAM_NAME");
        assertTrue(examName.startsWith(CommonConstants.ENC_VALUE_PREFIX));
    }
}
