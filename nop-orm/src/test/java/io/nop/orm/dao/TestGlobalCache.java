/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.dao;

import io.nop.app.SimsLesson;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.AbstractOrmTestCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestGlobalCache extends AbstractOrmTestCase {

    @Test
    public void testGlobalCache() {
        IEntityDao<SimsLesson> dao = daoProvider().daoFor(SimsLesson.class);

        SimsLesson lesson = new SimsLesson();
        lesson.setLessonId("33");
        lesson.setIntro("lesson");
        lesson.setScore(33);
        dao.saveEntity(lesson);

        assertEquals("lesson", dao.getEntityById("33").getIntro());

        jdbc().executeUpdate(SQL.begin().sql("update sims_lesson set intro='x' ").end());

        lesson = dao.getEntityById("33");
        assertEquals("lesson", lesson.getIntro());

        dao.clearEntityGlobalCache();

        orm().runInSession(() -> {
            SimsLesson entity = dao.getEntityById("33");
            assertEquals("x", entity.getIntro());

            dao.deleteEntity(entity);
            assertNull(dao.getEntityById("33"));
        });
    }
}
