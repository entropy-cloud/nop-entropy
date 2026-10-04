/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical_entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdSqlType;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.impl.DialectImpl;
import io.nop.dao.dialect.model.DialectFeatures;
import io.nop.dao.dialect.model.DialectModel;
import io.nop.dao.dialect.model.DialectSqls;
import io.nop.dao.dialect.model.SqlNativeFunctionModel;
import io.nop.orm.AbstractOrmTestCase;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.eql.compile.EqlCompiler;
import io.nop.orm.eql.compile.ISqlCompileContext;
import io.nop.orm.eql.meta.EntityTableMeta;
import io.nop.orm.eql.meta.ISqlTableMeta;
import io.nop.orm.eql.sql.IAliasGenerator;
import io.nop.orm.eql.sql.SeqAliasGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Plan 2308 Phase 4：EQL {@code ORDER BY ... NULLS FIRST/LAST} 的<b>行序</b>验证。
 *
 * <p>核心价值在于断言<b>数据库真实返回的行序</b>，而非"SQL 文本里是否含有 nulls 子句"。
 * 字符串断言无法区分"排序正确"与"恰好含有该子句但方向相反"——后者恰是本特性最容易出错之处。
 *
 * <p>与 {@code TestEqlNullsOrderCompile}（字符串断言）的分工：本类负责正确性，编译类负责覆盖面。
 *
 * <p><b>走完整 ORM 链路且无需实体代码生成</b>：实体 {@code test.entity.TestNullsOrderEntity}
 * 在 {@code app.orm.xml} 中声明 {@code className="io.nop.orm.support.DynamicOrmEntity"}，
 * 三个列全部以动态属性存取（{@code DynamicOrmEntity.values}，按 propId 索引的 {@code MapOfInt}），
 * 没有任何生成的 getter/setter；表由 {@code createTables()} 自动建出。
 *
 * <p>两个执行通道：
 * <ul>
 *   <li><b>原生分支</b>——经 {@code orm().findAll(...)} 走完整 ORM 链路，方言为测试库 H2
 *       （{@code supportNullsFirst=true}）。</li>
 *   <li><b>CASE 改写分支</b>——复用 <b>ORM 模型自身的实体元数据</b>，但传入一个
 *       {@code supportNullsFirst=false} 的方言来强制改写，再执行编译产物。
 *       这样两个分支共用同一份实体定义，等价性断言才有意义。</li>
 * </ul>
 *
 * <p>数据（score 可空）：
 * <pre>
 *   sid=1 score=10   seq=1
 *   sid=2 score=NULL seq=2
 *   sid=3 score=20   seq=3
 * </pre>
 */
public class TestEqlNullsOrderEndToEnd extends AbstractOrmTestCase {

    /** 测试库 H2 的方言：原生支持 NULLS 子句 */
    static IDialect nativeDialect;
    /** 人工构造：supportNullsFirst=false，强制走 CASE WHEN 改写 */
    static IDialect caseDialect;

    @BeforeAll
    public static void initDialects() {
        nativeDialect = buildDialect(true);
        caseDialect = buildDialect(false);
    }

    static IDialect buildDialect(boolean supportNullsFirst) {
        DialectModel model = new DialectModel();
        model.setFeatures(new DialectFeatures());
        model.getFeatures().setSupportNullsFirst(supportNullsFirst);
        model.setReservedKeywords(Collections.emptySet());
        model.setSqls(new DialectSqls());
        model.setSqlDataTypes(Collections.emptyList());
        // DialectImpl 构造即调用 buildFunctionSql("current_timestamp")，缺省会 NPE
        SqlNativeFunctionModel currentTimestamp = new SqlNativeFunctionModel();
        currentTimestamp.setName("current_timestamp");
        currentTimestamp.setHasParenthesis(false);
        currentTimestamp.setReturnType(StdSqlType.TIMESTAMP);
        SqlNativeFunctionModel rowNumber = new SqlNativeFunctionModel();
        rowNumber.setName("row_number");
        rowNumber.setHasParenthesis(true);
        rowNumber.setReturnType(StdSqlType.BIGINT);
        rowNumber.setOnlyForWindowExpr(true);
        model.setFunctions(java.util.Arrays.asList(currentTimestamp, rowNumber));
        return new DialectImpl(model);
    }

    static class Ctx implements ISqlCompileContext {
        final IDialect dialect;
        final IEntityModel entityModel;

        Ctx(IDialect dialect, IEntityModel entityModel) {
            this.dialect = dialect;
            this.entityModel = entityModel;
        }

        @Override
        public boolean isDisableLogicalDelete() {
            return false;
        }

        @Override
        public boolean isAllowUnderscoreName() {
            return false;
        }

        @Override
        public boolean isEnableFilter() {
            return false;
        }

        @Override
        public IEqlAstTransformer getAstTransformer() {
            return null;
        }

        @Override
        public IDialect getDialectForQuerySpace(String querySpace) {
            return dialect;
        }

        @Override
        public ISqlTableMeta resolveEntityTableMeta(String entityName) {
            return entityModel.getName().equals(entityName)
                    ? new EntityTableMeta(entityModel, null, dialect) : null;
        }

        @Override
        public IAliasGenerator getAliasGenerator() {
            return new SeqAliasGenerator();
        }
    }

    @BeforeEach
    public void prepareDefaultData() {
        seed(10, 1, null, 2, 20, 3);
    }

    private void seed(Object... scoreSeqPairs) {
        jdbcTemplate.executeUpdate(SQL.begin().sql("delete from TEST_NULLS_ORDER").end());
        int sid = 1;
        for (int i = 0; i < scoreSeqPairs.length; i += 2) {
            Integer score = (Integer) scoreSeqPairs[i];
            Integer seq = (Integer) scoreSeqPairs[i + 1];
            jdbcTemplate.executeUpdate(SQL.begin().sql(
                    "insert into TEST_NULLS_ORDER(SID, SCORE, SEQ_COL) values (" + sid + ", "
                            + (score == null ? "null" : score) + ", " + seq + ")").end());
            sid++;
        }
    }

    /** 原生分支：完整 ORM 链路（EQL -> EqlCompiler -> SQL -> JDBC -> 行绑定） */
    private List<Integer> sidsByEql(String orderByClause) {
        List<?> rows = orm().findAll(SQL.begin()
                .sql("select o.sid from test.entity.TestNullsOrderEntity o " + orderByClause).end());
        List<Integer> out = new ArrayList<>();
        for (Object row : rows)
            out.add(toSid(row));
        return out;
    }

    /**
     * 默认行映射器的返回形态随投影而变：投影标量列时直接给值，投影多列时给 Map。
     * 这里两种都收敛成 sid，避免断言依赖映射器实现细节。
     */
    private static int toSid(Object row) {
        if (row instanceof Number)
            return ((Number) row).intValue();
        Object v = ((Map<?, ?>) row).values().iterator().next();
        return ((Number) v).intValue();
    }

    /** CASE 改写分支：同一份 ORM 实体元数据 + 不支持原生语法的方言 */
    private List<Integer> sidsByEqlCaseRewrite(String orderByClause) {
        IEntityModel model = sessionFactory.getOrmModel()
                .getEntityModel("test.entity.TestNullsOrderEntity");
        String eql = "select o.sid from test.entity.TestNullsOrderEntity o " + orderByClause;
        String sql = new EqlCompiler().compile("t", eql, new Ctx(caseDialect, model)).getSql().getText();
        List<Integer> out = new ArrayList<>();
        jdbcTemplate.executeQuery(SQL.begin().sql(sql).end(), ds -> {
            for (io.nop.dataset.IDataRow row : ds.readAll())
                out.add(toSid(row.toMap()));
            return null;
        });
        return out;
    }

    // ================= 原生分支：四种方向组合的真实行序 =================

    @Test
    public void testAscNullsFirst() {
        assertEquals(List.of(2, 1, 3), sidsByEql("order by o.score asc nulls first"),
                "ASC + NULLS FIRST：NULL 行应在最前");
    }

    @Test
    public void testAscNullsLast() {
        assertEquals(List.of(1, 3, 2), sidsByEql("order by o.score asc nulls last"),
                "ASC + NULLS LAST：NULL 行应在最后");
    }

    @Test
    public void testDescNullsFirst() {
        assertEquals(List.of(2, 3, 1), sidsByEql("order by o.score desc nulls first"),
                "DESC + NULLS FIRST：NULL 行应在最前");
    }

    @Test
    public void testDescNullsLast() {
        assertEquals(List.of(3, 1, 2), sidsByEql("order by o.score desc nulls last"),
                "DESC + NULLS LAST：NULL 行应在最后");
    }

    @Test
    public void testExplicitMatchesH2DefaultForAsc() {
        // 对照：H2 缺省 ASC 为 NULLS FIRST（Phase 0 实测 2;1;3）
        assertEquals(sidsByEql("order by o.score asc"),
                sidsByEql("order by o.score asc nulls first"));
    }

    // ================= CASE 改写分支：真实行序 =================

    @Test
    public void testCaseRewriteAscNullsFirst() {
        assertEquals(List.of(2, 1, 3), sidsByEqlCaseRewrite("order by o.score asc nulls first"));
    }

    @Test
    public void testCaseRewriteAscNullsLast() {
        assertEquals(List.of(1, 3, 2), sidsByEqlCaseRewrite("order by o.score asc nulls last"));
    }

    @Test
    public void testCaseRewriteDescNullsFirst() {
        assertEquals(List.of(2, 3, 1), sidsByEqlCaseRewrite("order by o.score desc nulls first"));
    }

    @Test
    public void testCaseRewriteDescNullsLast() {
        assertEquals(List.of(3, 1, 2), sidsByEqlCaseRewrite("order by o.score desc nulls last"));
    }

    /**
     * 核心等价性断言：同一实体、同一查询，原生分支与 CASE 分支必须返回<b>完全相同</b>的行序。
     * 比各自独立断言期望值更强——它直接证明改写没有改变语义。
     */
    @Test
    public void testCaseRewriteIsEquivalentToNativeForAllFourCombinations() {
        for (String clause : new String[]{
                "order by o.score asc nulls first", "order by o.score asc nulls last",
                "order by o.score desc nulls first", "order by o.score desc nulls last"}) {
            assertEquals(sidsByEql(clause), sidsByEqlCaseRewrite(clause),
                    "CASE 改写分支与原生分支行序必须一致: " + clause);
        }
    }

    @Test
    public void testCaseRewriteMultiKeyRespectsPrecedence() {
        seed(10, 1, null, 1, 10, 2, null, 2);
        // D5：CASE 键必须紧邻其排序项，不得被 prepend 到列表头而颠倒优先级
        assertEquals(List.of(2, 1, 4, 3),
                sidsByEqlCaseRewrite("order by o.seq asc, o.score asc nulls first"));
    }

    @Test
    public void testCaseRewriteSecondKeyOnlyKeepsPrecedence() {
        seed(10, 1, null, 1);
        assertEquals(List.of(2, 1), sidsByEqlCaseRewrite("order by o.seq asc, o.score asc nulls first"));
    }

    @Test
    public void testCaseRewriteCoexistsWithLimit() {
        assertEquals(List.of(1, 3), sidsByEqlCaseRewrite("order by o.score asc nulls last limit 2"),
                "LIMIT 应取改写后行序的前两行");
    }

    // ================= 多键（原生分支） =================

    @Test
    public void testMultiKeyNullsOrderKeepsKeyPrecedence() {
        assertEquals(List.of(1, 2, 3), sidsByEql("order by o.seq asc, o.score asc nulls last"),
                "唯一首键 seq 已定序，空序键不得改变整体行序");
    }

    @Test
    public void testMultiKeyWithDuplicatesRespectsPrecedence() {
        seed(10, 1, null, 1, 10, 2, null, 2);
        // seq 分组，组内 score 空序 NULLS FIRST：空序键只能在自己那一组内生效
        assertEquals(List.of(2, 1, 4, 3), sidsByEql("order by o.seq asc, o.score asc nulls first"),
                "空序键不得被提升到首键之前");
    }

    @Test
    public void testSecondKeyNullsFirstWithDuplicates() {
        seed(10, 1, null, 1);
        assertEquals(List.of(2, 1), sidsByEql("order by o.seq asc, o.score asc nulls first"));
    }

    @Test
    public void testNullsClauseCoexistsWithLimit() {
        assertEquals(List.of(1, 3), sidsByEql("order by o.score asc nulls last limit 2"),
                "LIMIT 应取空序后的前两行");
    }

    // ================= D4 边界：窗口上下文 =================
    //
    // 原裁定为「窗口无条件 fail-fast」。实测 H2 原生接受窗口 ORDER BY 中的 NULLS，
    // 故收敛为：原生方言放行，仅在需要改写时才因窗口上下文 fail-fast。

    @Test
    public void testWindowNullsOrderAllowedOnNativeDialect() {
        // 原生方言下窗口 ORDER BY 中的 NULLS 是数据库自身能力，不应被无谓拒绝
        List<?> rows = orm().findAll(SQL.begin().sql(
                "select row_number() over (order by o.score nulls last) as rn"
                        + " from test.entity.TestNullsOrderEntity o").end());
        assertEquals(3, rows.size());
    }

    @Test
    public void testWindowNullsOrderFailsFastOnCaseDialect() {
        IEntityModel model = sessionFactory.getOrmModel()
                .getEntityModel("test.entity.TestNullsOrderEntity");
        NopException e = assertThrows(NopException.class, () ->
                new EqlCompiler().compile("t",
                        "select row_number() over (order by o.score nulls last) from test.entity.TestNullsOrderEntity o",
                        new Ctx(caseDialect, model)));
        assertEquals("nop.err.eql.nulls-order-not-supported", e.getErrorCode());
        assertEquals("window-not-supported", e.getParam("reason"));
    }

    // ================= 防误伤 =================

    @Test
    public void testQueryWithoutNullsUnaffected() {
        List<Integer> got = sidsByEql("order by o.score desc");
        assertEquals(3, got.size());
        // H2 缺省 DESC 为 NULLS LAST
        assertEquals(Integer.valueOf(2), got.get(2));
    }

    @Test
    public void testNonOrderByQueryUnaffected() {
        assertEquals(3, sidsByEql("").size());
    }
}