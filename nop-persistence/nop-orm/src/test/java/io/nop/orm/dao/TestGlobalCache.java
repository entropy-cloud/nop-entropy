/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.dao;

import io.nop.app.SimsClass;
import io.nop.app.SimsCollege;
import io.nop.app.SimsLesson;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmEntitySet;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    /**
     * 多实体批量加载时数据库访问失败，异常必须随batchLoadAsync返回的future传播给调用方，
     * 不允许被丢弃后假装加载成功
     */
    @Test
    public void testBatchLoadErrorPropagates() {
        IEntityDao<SimsLesson> dao = daoProvider().daoFor(SimsLesson.class);
        orm().runInSession(() -> {
            SimsLesson l1 = dao.newEntity();
            l1.setLessonId("33");
            l1.setIntro("a");
            dao.saveEntity(l1);
            SimsLesson l2 = dao.newEntity();
            l2.setLessonId("34");
            l2.setIntro("b");
            dao.saveEntity(l2);
        });

        // 让批量加载必然失败
        jdbc().executeUpdate(new SQL("drop table sims_lesson"));

        orm().runInSession(session -> {
            IOrmEntity e1 = session.load(SimsLesson.class.getName(), "33");
            IOrmEntity e2 = session.load(SimsLesson.class.getName(), "34");
            assertThrows(Exception.class,
                    () -> orm().batchLoadProps(Arrays.asList(e1, e2), Collections.singletonList("intro")),
                    "batch load failure must propagate to the caller");
            return null;
        });
    }

    /**
     * 多租户实体的全局缓存key带有租户前缀，批量加载回查缓存时必须使用相同的key，
     * 否则租户缓存永远miss
     */
    @Test
    public void testTenantBatchLoadGlobalCacheHit() {
        IEntityDao<SimsLesson> dao = daoProvider().daoFor(SimsLesson.class);
        orm().runInSession(() -> {
            SimsLesson l1 = dao.newEntity();
            l1.setLessonId("33");
            l1.setIntro("a");
            dao.saveEntity(l1);
            SimsLesson l2 = dao.newEntity();
            l2.setLessonId("34");
            l2.setIntro("b");
            dao.saveEntity(l2);
        });

        // 通过单个实体的加载填充全局缓存（单个加载路径的key是正确的）
        orm().runInSession(() -> assertEquals("a", dao.getEntityById("33").getIntro()));
        orm().runInSession(() -> assertEquals("b", dao.getEntityById("34").getIntro()));

        // 绕过缓存直接修改数据库
        jdbc().executeUpdate(new SQL("update sims_lesson set intro='x'"));

        // 批量加载必须命中全局缓存，读取到缓存中的旧值
        orm().runInSession(session -> {
            IOrmEntity e1 = session.load(SimsLesson.class.getName(), "33");
            IOrmEntity e2 = session.load(SimsLesson.class.getName(), "34");
            orm().batchLoadProps(Arrays.asList(e1, e2), Collections.singletonList("intro"));
            assertEquals("a", e1.orm_propValueByName("intro"));
            assertEquals("b", e2.orm_propValueByName("intro"));
            return null;
        });
    }

    /**
     * useGlobalCache的to-many集合发生增删并成功提交后，owner的全局缓存必须被失效，
     * 后续加载不能读到陈旧的elementIds
     */
    @Test
    public void testCollectionGlobalCacheEvictedAfterChange() {
        // 第一批：加载集合并填充全局缓存
        orm().runInSession(session -> {
            SimsCollege college = (SimsCollege) session.get(SimsCollege.class.getName(), "1");
            IOrmEntitySet<SimsClass> coll = (IOrmEntitySet<SimsClass>) college.prop_get("cachedClasses");
            assertEquals(1, coll.size());

            // 新增元素并提交，成功后必须失效集合全局缓存
            SimsClass newClass = (SimsClass) session.newEntity(SimsClass.class.getName());
            newClass.orm_propValueByName("classId", "901");
            newClass.orm_propValueByName("collegeId", "1");
            newClass.orm_propValueByName("className", "cache-evict");
            coll.add(newClass);
            session.flush();
            return null;
        });

        // 第二批：重新加载必须回源数据库，看到新增的元素（修复前命中陈旧缓存，只能看到旧元素）
        orm().runInSession(session -> {
            SimsCollege college = (SimsCollege) session.get(SimsCollege.class.getName(), "1");
            IOrmEntitySet<SimsClass> coll = (IOrmEntitySet<SimsClass>) college.prop_get("cachedClasses");
            assertEquals(2, coll.size());
            return null;
        });
    }
}
