/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical_entropy
 * Gitee:  https://gitee.com/nop-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm;

import io.nop.api.core.context.ContextProvider;
import io.nop.core.lang.sql.SQL;
import io.nop.orm.factory.SessionFactoryImpl;
import io.nop.orm.model.IEntityModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2380 Phase 6：EQL {@code upsert} 的 <b>ORM 层端到端 + 接线验证</b>。
 *
 * <p>与 {@code nop-persistence/nop-orm-eql/.../TestH2UpsertCompileEndToEnd}（编译链端到端）互补：
 * 那个测试走 {@code EqlCompiler.compile} + 裸 {@code PreparedStatement}，**不经过 ORM 模板与
 * {@code JdbcQueryExecutor}**；本类补上那一段——从 ORM 入口（{@code SQL.sql(eqlText)} 触发的
 * EQL 惰性编译）经 {@code JdbcQueryExecutor} 执行到 H2，并用<b>计数器</b>断言实体监听器的保存事件
 * 真的被触发。
 *
 * <p>这条接线断言不是形式主义：{@code SqlStatementKind.UPSERT} 在
 * {@code JdbcQueryExecutor.invokeListener} 里必须有一个 case，否则监听器**静默不上报**
 * （该风险正是 plan 文本自己写下的失败模式）。删掉那个 case，本测试必须转红。
 *
 * <p>upsert 的 H2 形态要求更新集覆盖全部列清单（含冲突键列本身）且右值不含列引用
 * （设计 §3.6 的两条必要条件），故样例按此书写。
 */
public class TestOrmUpsertWiring extends AbstractOrmTestCase {

    /**
     * 形态约束：更新集必须覆盖全部列清单（含冲突键列本身）、右值不得含列引用（设计 §3.6）。
     * 用字面量而非参数标记——本类的目的是打通"ORM 入口 → JdbcQueryExecutor → 监听上报"这条链路，
     * 参数绑定形态由 nop-orm-eql 侧的编译期测试承担。
     */
    static String upsertEql(int classId, String className, int studentNumber) {
        return "upsert into SimsClass (classId, className, studentNumber) values ("
                + classId + ", '" + className + "', " + studentNumber + ") "
                + "on conflict (classId) do update set classId = " + classId
                + ", className = '" + className + "', studentNumber = " + studentNumber;
    }

    static class CountingDaoListener implements IOrmDaoListener {
        final AtomicInteger save = new AtomicInteger();
        final AtomicInteger update = new AtomicInteger();
        final AtomicInteger delete = new AtomicInteger();
        final AtomicInteger read = new AtomicInteger();

        @Override
        public void onRead(IEntityModel entityModel) {
            read.incrementAndGet();
        }

        @Override
        public void onUpdate(IEntityModel entityModel) {
            update.incrementAndGet();
        }

        @Override
        public void onDelete(IEntityModel entityModel) {
            delete.incrementAndGet();
        }

        @Override
        public void onSave(IEntityModel entityModel) {
            save.incrementAndGet();
        }
    }

    @Test
    public void testUpsertGoesThroughDaoListenerAndExecutes() {
        // 表由 AbstractOrmTestCase.setUp() 统一建出，此处不得重复创建
        CountingDaoListener listener = new CountingDaoListener();
        ((SessionFactoryImpl) sessionFactory).addDaoListener(listener);

        // SimsClass 是租户模型，读回断言必须在租户上下文中进行
        String oldTenantId = ContextProvider.currentTenantId();
        ContextProvider.getOrCreateContext().setTenantId("-1");

        // ORM 入口：SQL.sql(eqlText) 触发 EQL 惰性编译 → JdbcQueryExecutor → H2
        long affected = orm().executeUpdate(upsertSql(1, "c1", 10));
        assertEquals(1, affected, "first upsert must insert one row");

        long affected2 = orm().executeUpdate(upsertSql(1, "c2", 20));
        assertEquals(1, affected2, "second upsert on same key must update one row");

        // 接线断言：upsert 语句种类在 JdbcQueryExecutor 里必须有 case 并上报保存事件。
        // 没有这个 case 时本断言为 0 —— 正是"静默不上报"的失败形态。
        assertEquals(2, listener.save.get(),
                "upsert must report a save event through IOrmDaoListener "
                        + "(SqlStatementKind.UPSERT case in JdbcQueryExecutor.invokeListener)");
        assertEquals(0, listener.delete.get(), "upsert must not report delete events");
        assertEquals(0, listener.update.get(), "upsert reports save, not update");

        // 结果断言：两腿都落到同一行，值为最后一次写入。
        // 走 jdbcTemplate 直查目标物理表，避开 ORM 读路径的实体级过滤/只读路由，
        // 让断言只钉"数据确实按预期落库"这一件事。
        List<Map<String, Object>> rows = jdbcTemplate.findAll(new SQL(
                "select CLASS_ID, CLASS_NAME, STUDENT_NUMBER from SIMS_CLASS order by CLASS_ID"));
        Map<String, Object> hit = null;
        int matched = 0;
        for (Map<String, Object> row : rows) {
            if ("1".equals(str(row, "CLASS_ID"))) {
                hit = row;
                matched++;
            }
        }
        assertEquals(1, matched,
                "both upsert legs must land on exactly one row with classId=1; content = " + rows);
        assertEquals("c2", str(hit, "CLASS_NAME"), "second leg must overwrite className");
        assertEquals("20", str(hit, "STUDENT_NUMBER"), "second leg must overwrite studentNumber");

        ContextProvider.getOrCreateContext().setTenantId(oldTenantId);
    }

    private static String str(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? null : v.toString();
    }

    private SQL upsertSql(int classId, String className, int studentNumber) {
        return SQL.begin().name("upsertWiring").sql(upsertEql(classId, className, studentNumber)).end();
    }
}