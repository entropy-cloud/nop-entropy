/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical_entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdDataType;
import io.nop.commons.type.StdSqlType;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.impl.DialectImpl;
import io.nop.dao.dialect.model.DialectFeatures;
import io.nop.dao.dialect.model.DialectModel;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.eql.compile.EqlCompiler;
import io.nop.orm.eql.compile.ISqlCompileContext;
import io.nop.orm.eql.meta.ISqlTableMeta;
import io.nop.orm.eql.sql.IAliasGenerator;
import io.nop.orm.eql.sql.SeqAliasGenerator;
import io.nop.orm.eql.meta.EntityTableMeta;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmEntityModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Plan 2308 Phase 4：EQL {@code ORDER BY ... NULLS FIRST/LAST} 的**行序**验证。
 *
 * <p>本类的核心价值在于断言<b>数据库真实返回的行序</b>，而非"SQL 文本里是否含有 nulls 子句"。
 * 字符串断言无法区分"排序正确"与"恰好含有该子句但方向相反"——后者恰是本特性最容易出错之处。
 *
 * <p>与 {@code TestEqlNullsOrderCompile}（字符串断言）的分工：本类负责正确性，编译类负责覆盖面。
 *
 * <p><b>为何不用 ORM 实体：</b>放入默认加载的 {@code app.orm.xml} 会强制要求生成 Java 实体类
 * （SessionFactory 对每个实体建 persist driver），而 {@code sql-defect.orm.xml} 不被默认加载、
 * 其表不会被 {@code createTables()} 建出。故此处使用<b>内存构造的实体元数据</b>驱动 EQL 编译，
 * 再把编译产物 SQL 直接在真实 H2 上执行——既走通完整 EQL 编译链路，又免去代码生成。
 *
 * <p>数据（score 可空）：
 * <pre>
 *   sid=1 score=10   seq=1
 *   sid=2 score=NULL seq=2
 *   sid=3 score=20   seq=3
 * </pre>
 */
public class TestEqlNullsOrderEndToEnd extends AbstractOrmTestCase {

    static IDialect dialect;
    /** supportNullsFirst=false -> 强制走 CASE WHEN 改写分支 */
    static IDialect caseDialect;
    static OrmEntityModel entityModel;
    static Map<String, OrmEntityModel> entities;

    @BeforeAll
    public static void initDialect() {
        DialectModel model = new DialectModel();
        model.setFeatures(new DialectFeatures());
        model.setReservedKeywords(Collections.emptySet());
        model.setSqls(new io.nop.dao.dialect.model.DialectSqls());
        model.setSqlDataTypes(Collections.emptyList());
        // DialectImpl 构造即调用 buildFunctionSql("current_timestamp")，缺省会 NPE
        io.nop.dao.dialect.model.SqlNativeFunctionModel currentTimestamp =
                new io.nop.dao.dialect.model.SqlNativeFunctionModel();
        currentTimestamp.setName("current_timestamp");
        currentTimestamp.setHasParenthesis(false);
        currentTimestamp.setReturnType(StdSqlType.TIMESTAMP);
        io.nop.dao.dialect.model.SqlNativeFunctionModel rowNumber =
                new io.nop.dao.dialect.model.SqlNativeFunctionModel();
        rowNumber.setName("row_number");
        rowNumber.setHasParenthesis(true);
        rowNumber.setReturnType(StdSqlType.BIGINT);
        rowNumber.setOnlyForWindowExpr(true);
        model.setFunctions(Arrays.asList(currentTimestamp, rowNumber));
        // 测试库 H2 的 supportNullsFirst=true（Phase 0 实测）
        model.getFeatures().setSupportNullsFirst(true);
        dialect = new DialectImpl(model);

        DialectModel caseModel = new DialectModel();
        caseModel.setFeatures(new DialectFeatures());
        caseModel.setReservedKeywords(Collections.emptySet());
        caseModel.setSqls(new io.nop.dao.dialect.model.DialectSqls());
        caseModel.setSqlDataTypes(Collections.emptyList());
        io.nop.dao.dialect.model.SqlNativeFunctionModel ct =
                new io.nop.dao.dialect.model.SqlNativeFunctionModel();
        ct.setName("current_timestamp");
        ct.setHasParenthesis(false);
        ct.setReturnType(StdSqlType.TIMESTAMP);
        caseModel.setFunctions(Collections.singletonList(ct));
        io.nop.dao.dialect.model.SqlNativeFunctionModel rn =
                new io.nop.dao.dialect.model.SqlNativeFunctionModel();
        rn.setName("row_number");
        rn.setHasParenthesis(true);
        rn.setReturnType(StdSqlType.BIGINT);
        rn.setOnlyForWindowExpr(true);
        caseModel.setFunctions(Arrays.asList(ct, rn));
        caseModel.getFeatures().setSupportNullsFirst(false);
        caseDialect = new DialectImpl(caseModel);

        entityModel = new OrmEntityModel();
        entityModel.setName("TestNullsOrderEntity");
        entityModel.setTableName("TEST_NULLS_ORDER");
        entityModel.setQuerySpace("spaceA");
        entityModel.setColumns(new ArrayList<>(Arrays.asList(
                col("sid", "SID", 1, true), col("score", "SCORE", 2, false),
                col("seq", "SEQ_COL", 3, false))));
        entityModel.init();
        entities = new HashMap<>();
        entities.put(entityModel.getName(), entityModel);
    }

    static OrmColumnModel col(String name, String code, int propId, boolean primary) {
        OrmColumnModel c = new OrmColumnModel();
        c.setName(name);
        c.setCode(code);
        c.setPropId(propId);
        c.setStdSqlType(StdSqlType.INTEGER);
        c.setStdDataType(StdDataType.INT); // 结果集绑定需要它，缺则 NPE
        c.setPrimary(primary);
        return c;
    }

    static class Ctx implements ISqlCompileContext {
        final IDialect dialect;

        Ctx(IDialect dialect) {
            this.dialect = dialect;
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
            OrmEntityModel m = entities.get(entityName);
            return m == null ? null : new EntityTableMeta(m, null, dialect);
        }

        @Override
        public IAliasGenerator getAliasGenerator() {
            return new SeqAliasGenerator();
        }
    }

    @BeforeEach
    public void prepareTableAndData() {
        // 实体元数据是内存构造的，不在默认 ORM 模型内，故表需自行建出。
        // 每次重建以保证测试自洽，不依赖执行顺序。
        jdbcTemplate.executeUpdate(SQL.begin()
                .sql("drop table if exists TEST_NULLS_ORDER").end());
        jdbcTemplate.executeUpdate(SQL.begin()
                .sql("create table TEST_NULLS_ORDER (SID integer not null primary key, SCORE integer, SEQ_COL integer)").end());
        jdbcTemplate.executeUpdate(SQL.begin()
                .sql("insert into TEST_NULLS_ORDER(SID, SCORE, SEQ_COL) values (1, 10, 1)").end());
        jdbcTemplate.executeUpdate(SQL.begin()
                .sql("insert into TEST_NULLS_ORDER(SID, SCORE, SEQ_COL) values (2, null, 2)").end());
        jdbcTemplate.executeUpdate(SQL.begin()
                .sql("insert into TEST_NULLS_ORDER(SID, SCORE, SEQ_COL) values (3, 20, 3)").end());
    }

    /** 编译 EQL 并在真实库上执行，返回按序的 sid 列表。 */
    List<Integer> sidsByEql(String orderByClause) {
        String eql = "select o.sid from TestNullsOrderEntity o " + orderByClause;
        return execSids(new EqlCompiler().compile("test", eql, new Ctx(dialect)).getSql().getText());
    }

    /** 同上，但方言标记为不支持原生 NULLS 语法，强制走 CASE WHEN 改写分支。 */
    List<Integer> sidsByEqlCaseRewrite(String orderByClause) {
        String eql = "select o.sid from TestNullsOrderEntity o " + orderByClause;
        return execSids(new EqlCompiler().compile("test", eql, new Ctx(caseDialect)).getSql().getText());
    }

    /** 在真实库上执行给定 SQL，返回按序的 sid 列表。 */
    List<Integer> execSids(String sql) {
        List<Integer> out = new ArrayList<>();
        jdbcTemplate.executeQuery(SQL.begin().sql(sql).end(), ds -> {
            for (io.nop.dataset.IDataRow row : ds.readAll())
                // 投影仅一列，按位置取，避免依赖结果集列名大小写/别名规则
                out.add(((Number) row.toMap().values().iterator().next()).intValue());
            return null;
        });
        return out;
    }

    // ---- 四种方向组合的真实行序 ----

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

    // ---- D5：多键场景 ----

    @Test
    public void testMultiKeyNullsOrderKeepsKeyPrecedence() {
        assertEquals(List.of(1, 2, 3), sidsByEql("order by o.seq asc, o.score asc nulls last"),
                "唯一首键 seq 已定序，空序键不得改变整体行序");
    }

    @Test
    public void testMultiKeyWithDuplicatesRespectsPrecedence() {
        jdbcTemplate.executeUpdate(SQL.begin()
                .sql("delete from TEST_NULLS_ORDER").end());
        jdbcTemplate.executeUpdate(SQL.begin()
                .sql("insert into TEST_NULLS_ORDER(SID, SCORE, SEQ_COL) values (1, 10, 1)").end());
        jdbcTemplate.executeUpdate(SQL.begin()
                .sql("insert into TEST_NULLS_ORDER(SID, SCORE, SEQ_COL) values (2, null, 1)").end());
        jdbcTemplate.executeUpdate(SQL.begin()
                .sql("insert into TEST_NULLS_ORDER(SID, SCORE, SEQ_COL) values (3, 10, 2)").end());
        jdbcTemplate.executeUpdate(SQL.begin()
                .sql("insert into TEST_NULLS_ORDER(SID, SCORE, SEQ_COL) values (4, null, 2)").end());

        // seq 分组，组内 score 空序 NULLS FIRST：空序键只能在自己那一组内生效
        assertEquals(List.of(2, 1, 4, 3), sidsByEql("order by o.seq asc, o.score asc nulls first"),
                "空序键不得被提升到首键之前");
    }

    @Test
    public void testSecondKeyNullsFirstWithDuplicates() {
        jdbcTemplate.executeUpdate(SQL.begin()
                .sql("delete from TEST_NULLS_ORDER").end());
        jdbcTemplate.executeUpdate(SQL.begin()
                .sql("insert into TEST_NULLS_ORDER(SID, SCORE, SEQ_COL) values (1, 10, 1)").end());
        jdbcTemplate.executeUpdate(SQL.begin()
                .sql("insert into TEST_NULLS_ORDER(SID, SCORE, SEQ_COL) values (2, null, 1)").end());
        // 第二键改写时，CASE 键不得 prepend 到 ORDER BY 列表头
        assertEquals(List.of(2, 1), sidsByEql("order by o.seq asc, o.score asc nulls first"));
    }

    // ---- LIMIT 与空序的组合 ----

    @Test
    public void testNullsClauseCoexistsWithLimit() {
        assertEquals(List.of(1, 3), sidsByEql("order by o.score asc nulls last limit 2"),
                "LIMIT 应取空序后的前两行");
    }

    // ---- 防误伤：未写 NULLS 时行为不变 ----

    @Test
    public void testQueryWithoutNullsUnaffected() {
        List<Integer> got = sidsByEql("order by o.score desc");
        assertEquals(3, got.size());
        // H2 缺省 DESC 为 NULLS LAST，故 NULL 行应在最后
        assertEquals(Integer.valueOf(2), got.get(2));
    }

    @Test
    public void testNonOrderByQueryUnaffected() {
        assertEquals(3, sidsByEql("").size());
    }

    // ================= CASE WHEN 改写分支的真实行序 =================
    //
    // 上面的用例走原生直出分支（无 CASE）。本段用 supportNullsFirst=false 的方言
    // 强制走 CASE 改写，并在真实 H2 上执行——H2 能执行 CASE，故无需 docker 即可
    // 验证"改写后的 SQL 排序结果是否真的等价于空序语义"。
    //
    // 这是整个特性最容易出错的地方：CASE 键方向或 then/else 取值一旦搞反，
    // SQL 依然可执行、依然含 case when，只有行序断言才能发现。

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
     * 核心等价性断言：同一查询在原生分支与 CASE 分支下必须返回<b>完全相同</b>的行序。
     * 这比各自独立断言期望值更强——它直接证明改写没有改变语义。
     */
    @Test
    public void testCaseRewriteIsEquivalentToNativeForAllFourCombinations() {
        String[] clauses = {
                "order by o.score asc nulls first",
                "order by o.score asc nulls last",
                "order by o.score desc nulls first",
                "order by o.score desc nulls last"
        };
        for (String clause : clauses) {
            assertEquals(sidsByEql(clause), sidsByEqlCaseRewrite(clause),
                    "CASE 改写分支与原生分支行序必须一致: " + clause);
        }
    }

    @Test
    public void testCaseRewriteMultiKeyRespectsPrecedence() {
        jdbcTemplate.executeUpdate(SQL.begin().sql("delete from TEST_NULLS_ORDER").end());
        jdbcTemplate.executeUpdate(SQL.begin()
                .sql("insert into TEST_NULLS_ORDER(SID, SCORE, SEQ_COL) values (1, 10, 1)").end());
        jdbcTemplate.executeUpdate(SQL.begin()
                .sql("insert into TEST_NULLS_ORDER(SID, SCORE, SEQ_COL) values (2, null, 1)").end());
        jdbcTemplate.executeUpdate(SQL.begin()
                .sql("insert into TEST_NULLS_ORDER(SID, SCORE, SEQ_COL) values (3, 10, 2)").end());
        jdbcTemplate.executeUpdate(SQL.begin()
                .sql("insert into TEST_NULLS_ORDER(SID, SCORE, SEQ_COL) values (4, null, 2)").end());

        // D5：CASE 键必须紧邻其排序项，不得被 prepend 到列表头而颠倒优先级
        assertEquals(List.of(2, 1, 4, 3),
                sidsByEqlCaseRewrite("order by o.seq asc, o.score asc nulls first"));
    }

    @Test
    public void testCaseRewriteSecondKeyOnlyKeepsPrecedence() {
        jdbcTemplate.executeUpdate(SQL.begin().sql("delete from TEST_NULLS_ORDER").end());
        jdbcTemplate.executeUpdate(SQL.begin()
                .sql("insert into TEST_NULLS_ORDER(SID, SCORE, SEQ_COL) values (1, 10, 1)").end());
        jdbcTemplate.executeUpdate(SQL.begin()
                .sql("insert into TEST_NULLS_ORDER(SID, SCORE, SEQ_COL) values (2, null, 1)").end());
        // 首键 seq 不改写、第二键 score 改写：CASE 键不得跑到列表头
        assertEquals(List.of(2, 1), sidsByEqlCaseRewrite("order by o.seq asc, o.score asc nulls first"));
    }

    @Test
    public void testCaseRewriteCoexistsWithLimit() {
        assertEquals(List.of(1, 3), sidsByEqlCaseRewrite("order by o.score asc nulls last limit 2"),
                "LIMIT 应取改写后行序的前两行");
    }

    // ================= D4 边界：原生方言下窗口内的 NULLS =================
    //
    // D4 原文写的是「窗口上下文无条件 fail-fast」。此处实测原生方言是否真的不行：
    // 若数据库原生接受窗口 ORDER BY 中的 NULLS 子句，则无条件拒绝是对用户的无谓限制。

    @Test
    public void testWindowNullsOrderOnNativeDialect() {
        String sql = new EqlCompiler().compile("t",
                "select row_number() over (order by o.score nulls last) as rn from TestNullsOrderEntity o",
                new Ctx(dialect)).getSql().getText();
        jdbcTemplate.executeQuery(SQL.begin().sql(sql).end(), ds -> {
            List<Integer> sids = new ArrayList<>();
            for (io.nop.dataset.IDataRow row : ds.readAll())
                sids.add(((Number) row.toMap().values().iterator().next()).intValue());
            // NULLS LAST：1(10), 3(20), 2(NULL)
            assertEquals(List.of(1, 3, 2), sids, "原生方言下窗口内 NULLS LAST 行序");
            return null;
        });
    }

    @Test
    public void testWindowNullsOrderOnCaseDialectFailsFast() {
        NopException e = assertThrows(NopException.class, () ->
                new EqlCompiler().compile("t",
                        "select row_number() over (order by o.score nulls last) from TestNullsOrderEntity o",
                        new Ctx(caseDialect)));
        assertEquals("nop.err.eql.nulls-order-not-supported", e.getErrorCode());
        assertEquals("window-not-supported", e.getParam("reason"));
    }
}
