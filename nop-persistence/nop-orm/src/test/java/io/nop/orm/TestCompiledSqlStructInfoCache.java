package io.nop.orm;

import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.eql.SqlOrderByOrdinal;
import io.nop.orm.persister.IPersistEnv;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2321 Phase 1 缓存交互测试：编译期结构信息随编译产物进 QueryPlanCache。
 *
 * <p>同一 EQL 文本两次编译（第二次命中 QueryPlanCache，同一实例）后，
 * 构造标志与 orderBy→投影序数映射保持正确——结构信息是编译产物静态属性，
 * 缓存安全模型不变（设计 §3.1 理由 b，对齐 2318 缓存交互测试先例）。</p>
 */
public class TestCompiledSqlStructInfoCache extends AbstractOrmTestCase {

    static final String EQL = "select o.strValue from test.entity.TestOrmTable o "
            + "where o.sid = ? order by o.strValue desc";

    @Test
    public void testStructFlagsSurviveQueryPlanCacheHit() {
        IPersistEnv env = (IPersistEnv) sessionFactory;
        ICompiledSql first = env.compileSql("struct-cache-test", EQL, false, false, false);
        ICompiledSql second = env.compileSql("struct-cache-test", EQL, false, false, false);

        // 第二次编译命中 QueryPlanCache：同一编译产物实例
        assertSame(first, second);

        // 缓存命中返回的编译产物上，结构信息保持正确
        assertFalse(first.hasForUpdate());
        assertFalse(first.hasGroupBy());
        assertFalse(first.hasDistinct());
        assertFalse(first.isBareCount());

        List<SqlOrderByOrdinal> ordinals = first.getOrderByOrdinals();
        assertEquals(1, ordinals.size());
        assertEquals(0, ordinals.get(0).getOrdinal());
        assertFalse(ordinals.get(0).isAsc());
    }

    @Test
    public void testBareCountFlagSurvivesQueryPlanCacheHit() {
        IPersistEnv env = (IPersistEnv) sessionFactory;
        String eql = "select count(1) from test.entity.TestOrmTable o";
        ICompiledSql first = env.compileSql("struct-cache-count-test", eql, false, false, false);
        ICompiledSql second = env.compileSql("struct-cache-count-test", eql, false, false, false);

        assertSame(first, second);
        assertTrue(first.isBareCount());
    }
}
