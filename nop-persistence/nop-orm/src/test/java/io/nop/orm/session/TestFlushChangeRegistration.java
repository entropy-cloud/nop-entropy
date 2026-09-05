/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.session;

import io.nop.api.core.util.ProcessResult;
import io.nop.app.SimsCollege;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmInterceptor;
import io.nop.orm.IOrmSession;
import io.nop.orm.factory.SessionFactoryImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 回归覆盖审查报告 ORM-01/ORM-06：flush执行过程中（preUpdate/preSave回调）对其他已托管实体的修改
 * 必须通过changedDuringFlush重放刷新到数据库，不能因flush结束时统一清除dirty而被静默丢弃。
 * <p>
 * 场景：实体B先被flush循环访问，随后flush实体A时preUpdate回调修改B——修复前B的修改会丢失。
 */
public class TestFlushChangeRegistration extends AbstractOrmTestCase {

    private final HookInterceptor interceptor = new HookInterceptor();

    /**
     * flush实体A("2")的过程中，回调将id为"1"的college名称修改为该值
     */
    private static final String MODIFIED_DURING_FLUSH = "B1-during-flush";

    interface IOrmEntityHook {
        void apply(IOrmEntity entity);
    }

    static class HookInterceptor implements IOrmInterceptor {
        IOrmEntityHook hook;
        SimsCollege college1;

        @Override
        public ProcessResult preUpdate(IOrmEntity entity) {
            applyHook(entity);
            return ProcessResult.CONTINUE;
        }

        @Override
        public ProcessResult preSave(IOrmEntity entity) {
            applyHook(entity);
            return ProcessResult.CONTINUE;
        }

        private void applyHook(IOrmEntity entity) {
            if (hook != null)
                hook.apply(entity);
        }
    }

    @Override
    protected void prepareData() {
        super.prepareData();
        IEntityDao<SimsCollege> dao = daoProvider().daoFor(SimsCollege.class);
        SimsCollege c2 = dao.newEntity();
        c2.setCollegeId("2");
        c2.setCollegeName("CollegeC");
        c2.setIntro("intro2");
        c2.setShortName("C");
        dao.saveEntity(c2);
    }

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();
        ((SessionFactoryImpl) sessionFactory).addInterceptor(interceptor);
    }

    @AfterEach
    @Override
    public void tearDown() {
        ((SessionFactoryImpl) sessionFactory).removeInterceptor(interceptor);
        super.tearDown();
    }

    /**
     * ORM-01: 有状态session的flush路径。B("1")先入缓存先被访问，flush A("2")时回调修改B。
     * runInNewSession结束时的session.flush()即被测flush。
     */
    @Test
    public void testManagedEntityModifiedDuringFlushIsFlushed() {
        interceptor.hook = entity -> {
            if (entity instanceof SimsCollege && "2".equals(entity.get_id())) {
                if (interceptor.college1 != null)
                    interceptor.college1.setCollegeName(MODIFIED_DURING_FLUSH);
            }
        };

        orm().runInSession((Runnable) () -> {
            IEntityDao<SimsCollege> dao = daoProvider().daoFor(SimsCollege.class);
            interceptor.college1 = dao.getEntityById("1");
            SimsCollege a = dao.getEntityById("2");

            a.setCollegeName("A2-modified");
        });

        // 新会话重新从数据库读取，验证B的修改确实被持久化
        SimsCollege reloaded = daoProvider().daoFor(SimsCollege.class).getEntityById("1");
        assertEquals(MODIFIED_DURING_FLUSH, reloaded.getCollegeName(),
                "flush过程中对已托管实体B的修改不应被丢弃");
    }

    /**
     * ORM-06: 无状态session的saveOrUpdate路径（flushImmediately），修改发生在A的flush过程中。
     */
    @Test
    public void testStatelessSessionMidFlushModificationFlushed() {
        interceptor.hook = entity -> {
            if (entity instanceof SimsCollege && "2".equals(entity.get_id())) {
                if (interceptor.college1 != null)
                    interceptor.college1.setCollegeName("B1-during-flush-stateless");
            }
        };

        try (IOrmSession session = ((SessionFactoryImpl) sessionFactory).openSession(true)) {
            IEntityDao<SimsCollege> dao = daoProvider().daoFor(SimsCollege.class);
            interceptor.college1 = (SimsCollege) session.get(dao.getEntityName(), "1");
            SimsCollege a = (SimsCollege) session.get(dao.getEntityName(), "2");

            a.setCollegeName("A2-modified-stateless");
            session.saveOrUpdate(a);
            session.flush();
        }

        SimsCollege reloaded = daoProvider().daoFor(SimsCollege.class).getEntityById("1");
        assertEquals("B1-during-flush-stateless", reloaded.getCollegeName(),
                "无状态session下flush过程中对B的修改不应被丢弃");
    }

}
