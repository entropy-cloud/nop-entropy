/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.enc;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.OrmErrors;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.eql.IEqlAstTransformer;
import io.nop.app.SimsExam;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2318 Phase 3：EQL 编译期可检索加密改写。
 *
 * <p>覆盖：等值/!=/IN 改写到 assisted 列（参数经 marker builder 执行期摘要、字面量编译期替换）、
 * LIKE/NOT LIKE/ILIKE 改写到 like 列（pattern 契约变换）、不可检索谓词显式失败
 * （ERR_ORM_ENC_COLUMN_NOT_SEARCHABLE）、端到端命中与解密回归、查询计划缓存交互、
 * QueryBean（编译为 EQL）路径继承改写、非 enc 列零影响、摘要契约形状。</p>
 *
 * <p>测试模型：SimsExam.examName（tagSet=enc）→ examNameAssisted / examNameLike
 * （声明与校验见 plan 2318 Phase 1；写路径派生见 Phase 2）。经生产接线验证：
 * {@link AbstractOrmTestCase} 的 session factory 未显式配置 transformer，默认 transformer 即
 * {@link EncAssistedEqlAstTransformer} 组合实例（SessionFactoryImpl.setDefaultAstTransformer
 * 统一包装），全部断言经真实编译链消费。</p>
 *
 * <p>注意：实体投影（select o）生成的列清单包含 enc 主列 EXAM_NAME（行加载解密所需），
 * 因此"主列不出现在改写后 SQL"的断言只针对 WHERE 子句片段。</p>
 */
public class TestEncAssistedEqlRewrite extends AbstractOrmTestCase {

    private static final String EXAM = "io.nop.app.SimsExam";

    private int stmtSeq;

    private ICompiledSql compile(String eql) {
        stmtSeq++;
        // 3 参入口走 SessionFactoryImpl.defaultAstTransformer（enc 组合 transformer，生产接线）
        return sessionFactory.compileSql("enc-rewrite-" + stmtSeq, eql, true);
    }

    private String compiledSqlText(String eql) {
        return compile(eql).getSql().getText();
    }

    /** 实体投影列清单包含 enc 主列（含辅助列），改写正确性断言只看 WHERE 片段 */
    private String whereClauseOf(String sqlText) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\bwhere\\b",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(sqlText);
        assertTrue(m.find(), "sql must contain where clause: " + sqlText);
        return sqlText.substring(m.end());
    }

    private void assertNotSearchable(String eql) {
        NopException e = assertThrows(NopException.class, () -> compile(eql));
        assertEquals(OrmErrors.ERR_ORM_ENC_COLUMN_NOT_SEARCHABLE.getErrorCode(), e.getErrorCode(),
                "expect ERR_ORM_ENC_COLUMN_NOT_SEARCHABLE for: " + eql);
    }

    // ---- 接线验证（Minimum Rules #23）----

    @Test
    public void testDefaultAstTransformerIsEncComposite() {
        IEqlAstTransformer transformer = ((io.nop.orm.persister.IPersistEnv) sessionFactory).getDefaultAstTransformer();
        EncAssistedEqlAstTransformer composite = assertInstanceOf(EncAssistedEqlAstTransformer.class, transformer);
        // AbstractOrmTestCase 未配置用户 transformer：delegate 为 null，enc 即完整默认实现
        assertNull(composite.getDelegate());
    }

    // ---- 摘要契约形状 ----

    @Test
    public void testLikeDigestContract() {
        EncAssistedHelper helper = EncAssistedHelper.instance();
        // likeDigest = 逐字符 likeDigestChar 拼接（小写折叠）
        assertEquals(helper.likeDigest("AbC"),
                helper.likeDigestChar('a') + helper.likeDigestChar('b') + helper.likeDigestChar('c'));
        // chunk 定宽 hex（缺省 width=8）
        assertEquals(8, helper.likeDigestChar('x').length());
        assertTrue(helper.likeDigestChar('x').matches("[0-9a-f]+"));
        // pattern 契约：% / _ 精确保留，字面字符摘要
        assertEquals("%" + helper.likeDigestChar('a') + "_", helper.likePatternDigest("%A_"));
    }

    // ---- 等值/IN 编译级改写 ----

    @Test
    public void testEqParamRewrittenToAssistedColumn() {
        ICompiledSql compiled = compile("select o from " + EXAM + " o where o.examName = ?");
        String text = compiled.getSql().getText();

        assertTrue(text.contains("EXAM_NAME_ASSISTED"), "eq must rewrite to assisted column: " + text);
        assertFalse(whereClauseOf(text).matches("(?s).*\\bEXAM_NAME\\b.*"),
                "master column must not appear in rewritten predicate: " + text);

        // 参数为执行期摘要：marker builder 通道（SqlParamTypeResolver marker-first 分流）；
        // 末位是自动追加的租户条件参数
        List<Object> params = compiled.buildParams(Arrays.asList("hello"));
        assertFalse(params.isEmpty());
        assertEquals(EncAssistedHelper.instance().assistedDigest("hello"), params.get(0));
    }

    @Test
    public void testEqLiteralRewrittenToDigestLiteral() {
        String text = compiledSqlText("select o from " + EXAM + " o where o.examName = 'abc'");
        assertTrue(text.contains("EXAM_NAME_ASSISTED"), "literal eq must rewrite column: " + text);
        assertTrue(text.contains(EncAssistedHelper.instance().assistedDigest("abc")),
                "literal must be replaced by digest literal: " + text);
        assertFalse(text.contains("'abc'"), "plaintext literal must not remain: " + text);
    }

    @Test
    public void testNePredicateRewrittenSymmetrically() {
        ICompiledSql compiled = compile("select o from " + EXAM + " o where o.examName != ?");
        String text = compiled.getSql().getText();
        assertTrue(text.contains("EXAM_NAME_ASSISTED"), "ne must rewrite to assisted column: " + text);
        assertEquals(EncAssistedHelper.instance().assistedDigest("x"),
                compiled.buildParams(Arrays.asList("x")).get(0));
    }

    @Test
    public void testInValuesRewrittenItemWise() {
        ICompiledSql compiled = compile("select o from " + EXAM + " o where o.examName in ('a','b')");
        String text = compiled.getSql().getText();
        assertTrue(text.contains("EXAM_NAME_ASSISTED"), "in must rewrite to assisted column: " + text);
        EncAssistedHelper helper = EncAssistedHelper.instance();
        assertTrue(text.contains(helper.assistedDigest("a")), "in literal must be digested: " + text);
        assertTrue(text.contains(helper.assistedDigest("b")), "in literal must be digested: " + text);

        ICompiledSql paramCompiled = compile("select o from " + EXAM + " o where o.examName in (?,?)");
        List<Object> params = paramCompiled.buildParams(Arrays.asList("a", "b"));
        // 前两个参数对应 IN 列表逐项摘要；末位参数是自动追加的租户条件参数
        assertTrue(params.size() >= 2, "in params must be collected: " + params);
        assertEquals(Arrays.asList(helper.assistedDigest("a"), helper.assistedDigest("b")),
                params.subList(0, 2));
    }

    @Test
    public void testInSubqueryExplicitlyRejected() {
        assertNotSearchable("select o from " + EXAM
                + " o where o.examName in (select c.examName from " + EXAM + " c)");
    }

    @Test
    public void testEqAgainstColumnRejected() {
        // 等值另一侧为列引用：无法静态摘要，显式失败
        assertNotSearchable("select o from " + EXAM + " o where o.examName = o.examScoreScale");
    }

    // ---- LIKE 编译级改写 ----

    @Test
    public void testLikeParamRewrittenToLikeColumn() {
        ICompiledSql compiled = compile("select o from " + EXAM + " o where o.examName like ?");
        String text = compiled.getSql().getText();
        assertTrue(text.contains("EXAM_NAME_LIKE"), "like must rewrite to like column: " + text);
        assertFalse(whereClauseOf(text).matches("(?s).*\\bEXAM_NAME\\b.*"),
                "master column must not appear in rewritten predicate: " + text);

        List<Object> params = compiled.buildParams(Arrays.asList("%abc%"));
        assertEquals(EncAssistedHelper.instance().likePatternDigest("%abc%"), params.get(0));
    }

    @Test
    public void testLikeLiteralPatternDigested() {
        String text = compiledSqlText("select o from " + EXAM + " o where o.examName like '%ab%'");
        assertTrue(text.contains("EXAM_NAME_LIKE"), "like literal must rewrite column: " + text);
        assertTrue(text.contains(EncAssistedHelper.instance().likePatternDigest("%ab%")),
                "pattern must be digested: " + text);
        assertFalse(text.contains("'%ab%'"), "plaintext pattern must not remain: " + text);
    }

    @Test
    public void testNotLikePreservesNotFlag() {
        String text = compiledSqlText("select o from " + EXAM + " o where o.examName not like '%x%'");
        String where = whereClauseOf(text);
        assertTrue(where.contains("EXAM_NAME_LIKE"), "not like must rewrite to like column: " + text);
        // 生成器把 NOT 输出为谓词前缀 `not <col> like <pattern>`，语义等价于 `not like`
        assertTrue(where.toLowerCase(Locale.ROOT).matches("(?s).*not\\s+o\\.exam_name_like\\s+like.*"),
                "not flag must be preserved: " + text);
        assertEquals(EncAssistedHelper.instance().likePatternDigest("%x%"),
                compile("select o from " + EXAM + " o where o.examName not like ?")
                        .buildParams(Arrays.asList("%x%")).get(0));
    }

    @Test
    public void testIlikeCompilesToPlainLikeWithLowercaseDigest() {
        // H2 无 ILIKE 函数注册：改写后必须不触发 ERR_EQL_NOT_SUPPORT_ILIKE
        ICompiledSql compiled = compile("select o from " + EXAM + " o where o.examName ilike ?");
        String text = compiled.getSql().getText();
        assertTrue(text.contains("EXAM_NAME_LIKE"), "ilike must rewrite to like column: " + text);

        List<Object> params = compiled.buildParams(Arrays.asList("%ABC%"));
        // ignoreCase 语义折叠进摘要：与对应小写 pattern 的摘要一致
        assertEquals(EncAssistedHelper.instance().likePatternDigest("%abc%"), params.get(0));
    }

    @Test
    public void testLikeWithEscapeRejected() {
        assertNotSearchable("select o from " + EXAM + " o where o.examName like 'a!%' escape '!'");
    }

    // ---- 不可检索谓词显式失败 ----

    @Test
    public void testRangePredicatesRejected() {
        assertNotSearchable("select o from " + EXAM + " o where o.examName > ?");
        assertNotSearchable("select o from " + EXAM + " o where o.examName between ? and ?");
    }

    @Test
    public void testOrderByGroupByRejected() {
        assertNotSearchable("select o from " + EXAM + " o order by o.examName");
        assertNotSearchable("select o.examName, count(*) from " + EXAM + " o group by o.examName");
    }

    @Test
    public void testFunctionArgAndIsNullRejected() {
        assertNotSearchable("select o from " + EXAM + " o where upper(o.examName) = ?");
        assertNotSearchable("select o from " + EXAM + " o where o.examName is null");
    }

    @Test
    public void testJoinOnRejected() {
        assertNotSearchable("select o from " + EXAM
                + " o inner join io.nop.app.SimsClass c on o.examName = c.className");
    }

    // ---- 端到端（H2）：保存 → 查询命中 → 解密回归 ----

    private void saveExam(String examId, String examName) {
        IEntityDao<SimsExam> dao = daoProvider().daoFor(SimsExam.class);
        SimsExam entity = dao.newEntity();
        entity.setExamId(examId);
        entity.setExamName(examName);
        dao.saveEntity(entity);
    }

    @Test
    public void testEndToEndEqAndLikeHitWithDecrypt() {
        saveExam("301", "HelloWorld");
        saveExam("302", "OtherExam");

        // 等值命中 + 实体加载解密回归（transformRow 仍返回明文）
        List<SimsExam> rows = orm().findAll(SQL.begin()
                .sql("select o from " + EXAM + " o where o.examName = ?", "HelloWorld").end());
        assertEquals(1, rows.size(), "eq via assisted column must hit the saved entity");
        assertEquals("301", rows.get(0).getExamId());
        assertEquals("HelloWorld", rows.get(0).getExamName(), "entity load must decrypt master column");

        // 不等值：排除命中行
        List<SimsExam> neRows = orm().findAll(SQL.begin()
                .sql("select o from " + EXAM + " o where o.examName != ?", "HelloWorld").end());
        assertEquals(1, neRows.size());
        assertEquals("302", neRows.get(0).getExamId());

        // LIKE 子串命中；大小写不敏感契约下两种大小写均命中
        List<SimsExam> likeRows = orm().findAll(SQL.begin()
                .sql("select o from " + EXAM + " o where o.examName like ?", "%World%").end());
        assertEquals(1, likeRows.size(), "like via like column must hit the saved entity");
        assertEquals("301", likeRows.get(0).getExamId());

        List<SimsExam> likeRowsLower = orm().findAll(SQL.begin()
                .sql("select o from " + EXAM + " o where o.examName like ?", "%world%").end());
        assertEquals(1, likeRowsLower.size(), "like digest is case-insensitive by contract");

        List<SimsExam> missRows = orm().findAll(SQL.begin()
                .sql("select o from " + EXAM + " o where o.examName like ?", "%NotFound%").end());
        assertTrue(missRows.isEmpty(), "non-matching pattern must not hit");
    }

    @Test
    public void testEndToEndLiteralEqAndUnsupportedPredicate() {
        saveExam("311", "LiteralHit");

        List<SimsExam> rows = orm().findAll(SQL.begin()
                .sql("select o from " + EXAM + " o where o.examName = 'LiteralHit'").end());
        assertEquals(1, rows.size(), "literal eq digest must hit saved entity");

        NopException e = assertThrows(NopException.class, () -> orm().findAll(SQL.begin()
                .sql("select o from " + EXAM + " o where o.examName > ?", "A").end()));
        assertEquals(OrmErrors.ERR_ORM_ENC_COLUMN_NOT_SEARCHABLE.getErrorCode(), e.getErrorCode());
        assertEquals(EXAM, e.getParam("entityName"));
        assertEquals("examName", e.getParam("propName"));
    }

    @Test
    public void testQueryBeanPathInheritsRewrite() {
        saveExam("321", "QueryBeanHit");

        // QueryBean 过滤路径编译为 EQL（DaoQueryHelper），应自动继承改写
        QueryBean query = new QueryBean();
        query.setSourceName(EXAM);
        query.setFilter(FilterBeans.eq("examName", "QueryBeanHit"));
        List<SimsExam> rows = daoProvider().daoFor(SimsExam.class).findPageByQuery(query);
        assertEquals(1, rows.size(), "querybean eq filter must be rewritten and hit");
        assertEquals("321", rows.get(0).getExamId());
    }

    // ---- 查询计划缓存交互（Phase 3 Decision）----

    @Test
    public void testCachedPlanDigestsDistinctParamsCorrectly() {
        String eql = "select o from " + EXAM + " o where o.examName = ?";

        ICompiledSql first = sessionFactory.compileSql("enc-cache-eq", eql, true);
        List<Object> firstParams = first.buildParams(Arrays.asList("CacheA"));
        assertEquals(EncAssistedHelper.instance().assistedDigest("CacheA"), firstParams.get(0));

        // 同名同文本第二次编译命中查询计划缓存：缓存的 builder 对不同参数各自正确摘要
        ICompiledSql second = sessionFactory.compileSql("enc-cache-eq", eql, true);
        List<Object> secondParams = second.buildParams(Arrays.asList("CacheB"));
        assertEquals(EncAssistedHelper.instance().assistedDigest("CacheB"), secondParams.get(0));
        assertFalse(secondParams.get(0).equals(firstParams.get(0)), "distinct inputs must digest distinctly");

        // LIKE pattern 缓存路径同样按输入摘要
        String likeEql = "select o from " + EXAM + " o where o.examName like ?";
        sessionFactory.compileSql("enc-cache-like", likeEql, true);
        ICompiledSql cachedLike = sessionFactory.compileSql("enc-cache-like", likeEql, true);
        assertEquals(EncAssistedHelper.instance().likePatternDigest("%CacheX%"),
                cachedLike.buildParams(Arrays.asList("%CacheX%")).get(0));
    }

    @Test
    public void testCachedPlanEndToEndDistinctHits() {
        saveExam("331", "CacheRowA");
        saveExam("332", "CacheRowB");

        List<SimsExam> rowsA = orm().findAll(SQL.begin()
                .sql("select o from " + EXAM + " o where o.examName = ?", "CacheRowA").end());
        assertEquals(1, rowsA.size());
        assertEquals("331", rowsA.get(0).getExamId());

        // 第二次执行同一 EQL 文本（运行期命中查询计划缓存），不同参数摘要各自正确
        List<SimsExam> rowsB = orm().findAll(SQL.begin()
                .sql("select o from " + EXAM + " o where o.examName = ?", "CacheRowB").end());
        assertEquals(1, rowsB.size());
        assertEquals("332", rowsB.get(0).getExamId());
    }

    // ---- 非 enc 列零影响 ----

    @Test
    public void testNonEncColumnUnaffected() {
        // enc 实体上的非 enc 列：不改写、参数保持原值（SimpleParamBuilder 回落路径）。
        // 断言限定在 WHERE 片段——实体投影列清单本身包含辅助列（行加载需要）。
        ICompiledSql compiled = sessionFactory.compileSql("enc-non-enc",
                "select o from " + EXAM + " o where o.examId = ?", true);
        String where = whereClauseOf(compiled.getSql().getText());
        assertFalse(where.contains("ASSISTED"), "non-enc column must not be rewritten: " + where);
        assertFalse(where.contains("EXAM_NAME_LIKE"), "non-enc column must not be rewritten: " + where);
        assertTrue(where.contains("EXAM_ID"), "non-enc column stays untouched: " + where);
        assertEquals("x1", compiled.buildParams(Arrays.asList("x1")).get(0));

        // 非 enc 实体上的普通等值查询同样零影响
        String plain = compiledSqlText("select o from io.nop.app.SimsClass o where o.className = ?");
        assertFalse(whereClauseOf(plain).contains("ASSISTED"),
                "non-enc entity must not be rewritten: " + plain);
    }

    // ---- 更新/删除语句 WHERE 的改写 ----

    @Test
    public void testDeleteWhereRewritten() {
        saveExam("341", "ToDelete");
        String text = compiledSqlText("delete from " + EXAM + " o where o.examName = ?");
        assertTrue(text.contains("EXAM_NAME_ASSISTED"), "delete where must rewrite: " + text);

        orm().executeUpdate(SQL.begin()
                .sql("delete from " + EXAM + " o where o.examName = ?", "ToDelete").end());
        List<SimsExam> remain = orm().findAll(SQL.begin()
                .sql("select o from " + EXAM + " o where o.examId = ?", "341").end());
        assertTrue(remain.isEmpty(), "delete via assisted column must remove the entity");
    }

    @Test
    public void testUpdateSetNotRewrittenBulkBoundary() {
        // bulk UPDATE SET 涉及 enc 列：框架不维护辅助列（2026-10-05 用户裁定），SET 部分不报错不改写；
        // WHERE 条件在 examId（非 enc 列）上，整条语句无任何改写发生
        saveExam("351", "BeforeUpdate");
        String text = compiledSqlText("update " + EXAM + " o set o.examName = ? where o.examId = ?");
        assertFalse(text.contains("EXAM_NAME_ASSISTED"), "set clause must not be rewritten: " + text);
        assertTrue(text.contains("EXAM_NAME"), "set clause keeps master column (write path binder): " + text);
    }
}
