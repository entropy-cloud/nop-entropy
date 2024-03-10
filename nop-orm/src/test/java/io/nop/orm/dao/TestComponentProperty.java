/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.dao;

import io.nop.app.SimsExam;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.AbstractOrmTestCase;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestComponentProperty extends AbstractOrmTestCase {
    @Test
    public void testFloatingScaleComponent() {
        IEntityDao<SimsExam> dao = daoProvider().daoFor(SimsExam.class);
        SimsExam entity = new SimsExam();
        entity.setExamId("101");
        entity.getExamScoreDecimal().setNormalizedValue(new BigDecimal("3.000"));
        assertEquals("3.000", entity.getExamScore().toString());

        dao.saveEntity(entity);

        entity = dao.getEntityById("101");
        assertEquals(3, entity.getExamScoreScale().intValue());
        assertEquals("3.000", entity.getExamScoreDecimal().getNormalizedValue().toString());

        orm().runInSession(() -> {
            SimsExam exam = dao.getEntityById("101");
            exam.setExamScoreNormalized(new BigDecimal("2.01"));
        });

        entity = dao.getEntityById("101");
        assertEquals("2.01", entity.getExamScoreNormalized().toString());
    }
}
