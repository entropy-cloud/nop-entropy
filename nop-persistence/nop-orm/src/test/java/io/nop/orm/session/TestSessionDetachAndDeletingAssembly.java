package io.nop.orm.session;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.app.SimsCollege;
import io.nop.app.SimsCollegeEx;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmSession;
import io.nop.orm.OrmEntityState;
import io.nop.orm.OrmErrors;
import io.nop.core.lang.sql.SQL;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.nop.api.core.beans.FilterBeans.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R1.5（plan 2368）：会话状态机两 P1 回归（P1-2.1-001 detach 契约 / P1-2.1-002 DELETING 装配重置）。
 */
public class TestSessionDetachAndDeletingAssembly extends AbstractOrmTestCase {

    private long countRow(String collegeId) {
        Object v = jdbc().findFirst(new SQL(
                "select count(*) as CNT from sims_college where COLLEGE_ID='" + collegeId + "'"));
        return ((Number) v).longValue();
    }

    private Object collegeName(String collegeId) {
        return jdbc().findFirst(new SQL(
                "select COLLEGE_NAME as V from sims_college where COLLEGE_ID='" + collegeId + "'"));
    }

    private SimsCollege newCollege(String id, String name) {
        SimsCollege c = new SimsCollege();
        c.setCollegeId(id);
        c.setCollegeName(name);
        c.setIntro("intro-" + id);
        // 显式设置 to-one 关联（TestCascadeFlush 先例）：未装载关联在 detach 级联读取时
        // 会触发懒加载初始化（预存行为，不在本 plan 范围）
        SimsCollegeEx ex = new SimsCollegeEx();
        ex.setExtInfo("ext-" + id);
        c.setCollegeEx(ex);
        return c;
    }

    /**
     * P1-2.1-002：delete（DELETING）后同会话 EQL 查询装配该实体，不得把状态重置为 MANAGED——
     * 否则 flush 静默取消 DELETE（行残留）或把删除意图反转为 UPDATE（改后删复活）。
     * 修复前形态：select 后状态变 MANAGED → flush 后行残留（count=1）。
     */
    @Test
    public void testDeletingEntityNotResetByQueryAssembly() {
        orm().runInSession(() -> {
            orm().save(newCollege("150001", "a"));
            orm().flushSession();

            IOrmEntity entity = orm().load(SimsCollege.class.getName(), "150001");
            orm().delete(entity);
            assertEquals(OrmEntityState.DELETING, entity.orm_state());

            // 同会话 EQL 查询：transformRow → internalMakeEntity → 缓存命中 → internalAssemble
            QueryBean query = new QueryBean();
            query.setFilter(eq(SimsCollege.PROP_NAME_collegeId, "150001"));
            List<SimsCollege> list = daoProvider().daoFor(SimsCollege.class).findPageByQuery(query);
            assertEquals(1, list.size(), "同事务内查询仍返回该行");

            // 守卫生效：装配不得重置 DELETING
            assertEquals(OrmEntityState.DELETING, entity.orm_state());

            orm().flushSession();
        });

        assertEquals(0L, countRow("150001"), "flush 必须仍发出 DELETE（修复前行残留）");
    }

    /**
     * P1-2.1-001：detach 与 evict 契约对齐——移出一级缓存、不再参与 flush。
     * 判别布景（审计复核补充）：detach 后保持同类型第二实体 dirty，使 forEachDirty 扫全类型缓存；
     * 修复前被 detach 实体的 pending oldValues 会被当作 UPDATE 落库（post-detach 修改反常落库）。
     * 修复前必须失败的断言=R15B1 的行值断言。
     */
    @Test
    public void testDetachRemovesFromSessionCacheAndFlush() {
        IOrmSession session = sessionFactory.openSession(false);
        try {
            SimsCollege a = newCollege("150002", "a1");
            SimsCollege b = newCollege("150003", "b1");
            session.save(a);
            session.save(b);
            session.flush();

            IOrmEntity ea = session.get(SimsCollege.class.getName(), "150002");
            // 空 selection：绕开预存的双向一对一关联级联递归缺陷（不在本 plan 范围，已登记 follow-up）
            session.detach(ea, new FieldSelectionBean());

            assertFalse(session.contains(ea), "detach 后 contains 不再命中（cache.remove）");

            // post-detach 修改：不得落库（enhancer 已清，修改只记 oldValues）
            ea.orm_propValueByName(SimsCollege.PROP_NAME_collegeName, "a1-changed");
            // 判别布景：保持同类型第二实体 dirty，flush 扫描该类型缓存
            b.setCollegeName("b1-changed");

            session.flush();
            session.close();

            assertEquals("a1", collegeName("150002"), "post-detach 修改不得落库（修复前反常落库 UPDATE）");
            assertEquals("b1-changed", collegeName("150003"), "同会话其他 dirty 实体正常落库");
        } finally {
            session.close();
        }
    }

    /** P1-2.1-001：closed session 上 detach 响亮失败（checkValid）。 */
    @Test
    public void testDetachOnClosedSessionFailsLoud() {
        IOrmSession session = sessionFactory.openSession(false);
        SimsCollege a = newCollege("150004", "c1");
        session.save(a);
        session.flush();
        IOrmEntity ea = session.get(SimsCollege.class.getName(), "150004");
        session.close();

        assertThrows(Exception.class, () -> session.detach(ea, new FieldSelectionBean()),
                "closed session 上 detach 必须 checkValid 响亮失败");
        // cleanup（独立会话删除）
        orm().runInSession(() -> {
            IOrmEntity e = orm().load(SimsCollege.class.getName(), "150004");
            orm().delete(e);
            orm().flushSession();
        });
    }
}
