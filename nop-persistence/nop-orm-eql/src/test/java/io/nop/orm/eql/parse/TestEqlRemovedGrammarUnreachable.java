/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical_entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.parse;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.unittest.BaseTestCase;
import io.nop.orm.eql.ast.SqlCommit;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.SqlRollback;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2307 Phase 4：V3（集合聚合访问）与 V4（事务控制语句）的终态断言。
 *
 * <p>两者按 Phase 1 裁定落在 plan 允许的两种终态上：
 * <ul>
 *   <li><b>V3 = 删除</b>：{@code coll[where].func(args)} 从 grammar 与 AST 中移除，
 *       该写法必须是带错误码的解析错误；</li>
 *   <li><b>V4 = 显式失败</b>：{@code commit}/{@code rollback} 的 AST 节点保留
 *       （删除会因 xgen list 元素类型推导而被迫收窄 {@code SqlProgram.statements}
 *       的公开签名并波及 nop-metadata），但必须①编译期带错误码失败、
 *       ②EQL 通道 round-trip 无损、不再产出空字符串。</li>
 * </ul>
 *
 * <p>删除前基线（可复核）：{@code commit} → {@code toEql=[]}（空字符串）；
 * {@code select 1 from E where coll[x > 1].count() > 2} → 解析成功并打印为
 * {@code where collx > 1 > 2}（{@code [...]}、{@code .count()} 与主节点全丢）。
 *
 * <p>注意：集合算子 {@code _some} / {@code _all} 是<strong>不同的语法</strong>，
 * {@link #testCollectionOperatorsStillWork()} 单独守护它们未被误伤。
 */
public class TestEqlRemovedGrammarUnreachable extends BaseTestCase {

    SqlProgram parse(String eql) {
        return new EqlASTParser().parseFromText(null, eql);
    }

    void assertParseFails(String eql) {
        NopException e = assertThrows(NopException.class,
                () -> new EqlASTParser().parseFromText(null, eql),
                "该语法已被裁定移除，必须解析失败而不是静默接受: " + eql);
        assertNotNull(e.getErrorCode(), "解析失败必须带错误码: " + eql);
    }

    // ================= V3：集合聚合访问已删除 =================

    @Test
    public void testCollectionAccessInWhereUnreachable() {
        assertParseFails("select 1 from E where coll[x > 1].count() > 2");
    }

    @Test
    public void testCollectionAccessInProjectionUnreachable() {
        assertParseFails("select coll[x > 1].count() from E");
    }

    @Test
    public void testCollectionAccessInOrderByUnreachable() {
        assertParseFails("select 1 from E order by coll[x > 1].count()");
    }

    @Test
    public void testCollectionAccessWithoutWhereUnreachable() {
        assertParseFails("select coll[].count() from E");
    }

    @Test
    public void testCollectionAccessWithOrderByUnreachable() {
        assertParseFails("select 1 from E where coll[x > 1 order by y].sum(z) > 2");
    }

    @Test
    public void testCollectionAccessWithOtherFuncUnreachable() {
        assertParseFails("select 1 from E where coll[x > 1].max(y) > 2");
    }

    @Test
    public void testCollectionAccessIsNotSilentlyCorrupted() {
        // 回归钉：删除前该写法解析成功并被打印成 `where collx > 1 > 2`
        // （[...]、.count() 与主节点全部丢失）——不得回到那个状态
        assertParseFails("select 1 from E where coll[x > 1].count() > 2");
    }

    // ================= V4：事务控制语句显式失败 =================

    @Test
    public void testCommitStillParsesToSqlCommit() {
        // AST 节点保留（删除会触发公开签名收窄），但语义是「显式失败」
        assertInstanceOf(SqlCommit.class, parse("commit").getStatements().get(0));
    }

    @Test
    public void testRollbackStillParsesToSqlRollback() {
        assertInstanceOf(SqlRollback.class, parse("rollback").getStatements().get(0));
    }

    @Test
    public void testCommitRoundTripIsNotEmpty() {
        // 核心回归：删除前 toEql=[]（空字符串，节点被静默吞掉）
        String eql = parse("commit").toSqlString().trim();
        assertEquals("commit", eql);
        assertTrue(!eql.isEmpty(), "commit 的 round-trip 不得为空字符串");
    }

    @Test
    public void testRollbackRoundTripIsNotEmpty() {
        String eql = parse("rollback").toSqlString().trim();
        assertEquals("rollback", eql);
        assertTrue(!eql.isEmpty(), "rollback 的 round-trip 不得为空字符串");
    }

    @Test
    public void testCommitRoundTripIsCaseInsensitiveInGrammar() {
        assertInstanceOf(SqlCommit.class, parse("COMMIT").getStatements().get(0));
        assertInstanceOf(SqlRollback.class, parse("ROLLBACK").getStatements().get(0));
    }

    // ================= 防误伤：该保留的语法不受影响 =================

    @Test
    public void testCollectionOperatorsStillWork() {
        // _some/_all 是活语法（展开为 EXISTS / NOT EXISTS），与被删的 coll[...] 无关
        assertNotNull(parse("select o.name from AppUser o where o.roles._some.userId = 'x'"));
    }

    @Test
    public void testPlainSelectStillWorks() {
        assertNotNull(parse("select a from t"));
    }

    @Test
    public void testDmlStatementsStillWork() {
        assertNotNull(parse("select a from t where a = 1"));
        assertNotNull(parse("update t set a = 'x' where id = '1'"));
        assertNotNull(parse("delete from t where id = '1'"));
        assertNotNull(parse("insert into t(a) values ('x')"));
    }

    @Test
    public void testCastStillWorks() {
        // 被删的是 ORDER BY 项上的 collate 与集合访问；CAST 类型修饰上的 collate 是活语法
        assertNotNull(parse("select cast(a as varchar(10)) from t"));
    }
}