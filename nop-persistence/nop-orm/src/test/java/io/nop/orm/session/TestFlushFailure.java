/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.session;

import io.nop.api.core.exceptions.NopException;
import io.nop.app.SimsClass;
import io.nop.app.SimsCollege;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmSession;
import io.nop.orm.ddl.DdlSqlCreator;
import io.nop.orm.model.IEntityModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestFlushFailure extends AbstractOrmTestCase {

    /**
     * flush中途失败(例如装载待处理实体时SQL出错)后，已标记flushVisiting的实体必须被复位。
     * 历史版本只在第二遍遍历中复位，异常中断后标记残留，同一session上重试flush时级联处理被静默跳过
     */
    @Test
    public void testFlushFailureClearsFlushVisiting() {
        IEntityModel classModel = sessionFactory.getOrmModel().requireEntityModel(SimsClass.class.getName());
        String createTableSql = new DdlSqlCreator(jdbcTemplate.getDialectForQuerySpace(null))
                .createTable(classModel, false);

        orm().runInSession((IOrmSession session) -> {
            IEntityDao<SimsCollege> dao = daoProvider().daoFor(SimsCollege.class);
            SimsCollege college = dao.loadEntityById("1");
            college.setCollegeName("changed");

            // 挂起一个待装载的proxy实体，flush的第一遍级联处理后、装载队列执行时会访问被删除的表
            IOrmEntity proxy = session.load(SimsClass.class.getName(), "11");
            assertTrue(proxy.orm_proxy());
            session.getBatchLoadQueue().enqueue(proxy);

            jdbcTemplate.executeUpdate(SQL.begin().sql("drop table " + classModel.getTableName()).end());

            Throwable error = null;
            try {
                session.flush();
            } catch (Throwable e) {
                error = e;
            }
            assertNotNull(error);

            // flush失败后flushVisiting标记必须被复位
            assertFalse(college.orm_flushVisiting());

            // 恢复表后同一session上重试flush应成功
            jdbcTemplate.executeUpdate(SQL.begin().sql(createTableSql).end());
            session.flush();

            // 验证重试flush确实生效
            return null;
        });

        orm().runInSession((IOrmSession session) -> {
            SimsCollege college = daoProvider().daoFor(SimsCollege.class).loadEntityById("1");
            assertEquals("changed", college.getCollegeName());
            return null;
        });
    }

    /**
     * 正常flush不抛异常时行为保持不变
     */
    @Test
    public void testFlushSuccess() {
        orm().runInSession((IOrmSession session) -> {
            IEntityDao<SimsCollege> dao = daoProvider().daoFor(SimsCollege.class);
            SimsCollege college = dao.loadEntityById("1");
            college.setCollegeName("changed2");
            session.flush();
            assertFalse(college.orm_flushVisiting());
            return null;
        });

        orm().runInSession((IOrmSession session) -> {
            SimsCollege college = daoProvider().daoFor(SimsCollege.class).loadEntityById("1");
            assertEquals("changed2", college.getCollegeName());
            return null;
        });
    }

    /**
     * 批量动作执行失败时，flushDelayTasks不应继续执行。
     * 历史版本无论是否出错都会执行延迟任务，失败路径上可能产生多余的副作用
     */
    @Test
    public void testFlushFailureSkipsDelayTasks() {
        IEntityModel collegeModel = sessionFactory.getOrmModel().requireEntityModel(SimsCollege.class.getName());

        orm().runInSession((IOrmSession session) -> {
            IEntityDao<SimsCollege> dao = daoProvider().daoFor(SimsCollege.class);
            SimsCollege college = dao.loadEntityById("1");
            college.setCollegeName("changed3");

            boolean[] delayTaskRun = new boolean[1];
            ((IOrmSessionImplementor) session).getBatchActionQueue(null)
                    .addDelayTask("test-delay-key", () -> delayTaskRun[0] = true);

            jdbcTemplate.executeUpdate(SQL.begin().sql("drop table " + collegeModel.getTableName()).end());

            Throwable error = null;
            try {
                session.flush();
            } catch (Throwable e) {
                error = e;
            }
            assertNotNull(error);
            // 批量动作执行失败，延迟任务不应被执行
            assertFalse(delayTaskRun[0]);
            return null;
        });

        // 正常路径：新session中保存SimsClass（表未被破坏），flush成功后延迟任务被执行
        orm().runInSession((IOrmSession session) -> {
            SimsClass extra = daoProvider().daoFor(SimsClass.class).newEntity();
            extra.setClassId("99");
            extra.setCollegeId("1");
            extra.setClassName("classX");
            orm().save(extra);

            boolean[] delayTaskRun = new boolean[1];
            ((IOrmSessionImplementor) session).getBatchActionQueue(null)
                    .addDelayTask("test-delay-key2", () -> delayTaskRun[0] = true);
            session.flush();
            assertTrue(delayTaskRun[0]);
            return null;
        });
    }
}
