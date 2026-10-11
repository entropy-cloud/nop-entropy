package io.nop.orm.eql.compile;

import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.SqlQuerySelect;
import io.nop.orm.eql.ast.SqlWhere;
import io.nop.orm.eql.parse.EqlASTParser;
import io.nop.orm.eql.sql.IAliasGenerator;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestCollectionOperatorTransformer {

    private final EqlASTParser parser = new EqlASTParser();

    private static class TestAliasGenerator implements IAliasGenerator {
        private int tableCounter = 0;

        @Override
        public String genTableAlias() {
            return "t" + (++tableCounter);
        }

        @Override
        public String genColumnAlias() {
            return "c" + tableCounter;
        }
    }

    private SqlWhere parseWhere(String eql) {
        SqlProgram program = parser.parseFromText(null, eql);
        assertNotNull(program);
        assertFalse(program.getStatements().isEmpty());

        SqlQuerySelect select = (SqlQuerySelect) program.getStatements().get(0);
        return select.getWhere();
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").replaceAll("\\( ", "(").replaceAll(" \\)", ")").trim();
    }

    @Test
    public void testSingleSomeCondition() {
        String eql = "select o from User o where o.roles._some.status = 1";
        SqlWhere where = parseWhere(eql);
        assertNotNull(where);

        new CollectionOperatorTransformer(new TestAliasGenerator(), new java.util.HashSet<>()).transform(where);

        String expected = "where exists (select 1 from o.roles as t1 where t1.status = 1)";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testSingleAllCondition() {
        String eql = "select o from User o where o.roles._all.status = 1";
        SqlWhere where = parseWhere(eql);
        assertNotNull(where);

        new CollectionOperatorTransformer(new TestAliasGenerator(), new java.util.HashSet<>()).transform(where);

        String expected = "where not exists (select 1 from o.roles as t1 where t1.status <> 1)";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testMultipleSomeAndConditionsMerge() {
        String eql = "select o from User o where o.roles._some.name = 'admin' and o.roles._some.status = 1";
        SqlWhere where = parseWhere(eql);
        assertNotNull(where);

        new CollectionOperatorTransformer(new TestAliasGenerator(), new java.util.HashSet<>()).transform(where);

        String expected = "where exists (select 1 from o.roles as t1 where (t1.name = 'admin') and (t1.status = 1))";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testMultipleSomeOrConditionsMerge() {
        String eql = "select o from User o where o.roles._some.name = 'admin' or o.roles._some.name = 'reviewer'";
        SqlWhere where = parseWhere(eql);
        assertNotNull(where);

        new CollectionOperatorTransformer(new TestAliasGenerator(), new java.util.HashSet<>()).transform(where);

        String expected = "where exists (select 1 from o.roles as t1 where ((t1.name = 'admin') or (t1.name = 'reviewer')))";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testMultipleAllAndConditionsMerge() {
        String eql = "select o from User o where o.roles._all.status = 1 and o.roles._all.deleted = false";
        SqlWhere where = parseWhere(eql);
        assertNotNull(where);

        new CollectionOperatorTransformer(new TestAliasGenerator(), new java.util.HashSet<>()).transform(where);

        String expected = "where not exists (select 1 from o.roles as t1 where ((t1.status <> 1) or (t1.deleted <> false)))";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testMultipleAllOrConditionsNoMerge() {
        String eql = "select o from User o where o.roles._all.status = 1 or o.roles._all.deleted = false";
        SqlWhere where = parseWhere(eql);
        assertNotNull(where);

        new CollectionOperatorTransformer(new TestAliasGenerator(), new java.util.HashSet<>()).transform(where);

        String expected = "where (not exists (select 1 from o.roles as t1 where t1.status <> 1) or not exists (select 1 from o.roles as t2 where t2.deleted <> false))";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testNestedCollectionOperators() {
        String eql = "select o from User o where o.roles._some.depts._all.status = 1";
        SqlWhere where = parseWhere(eql);
        assertNotNull(where);

        new CollectionOperatorTransformer(new TestAliasGenerator(), new java.util.HashSet<>()).transform(where);

        String expected = "where exists (select 1 from o.roles as t1 where not exists (select 1 from t1.depts as t2 where t2.status <> 1))";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testNoCollectionOperator() {
        String eql = "select o from User o where o.status = 1 and o.name = 'test'";
        SqlWhere where = parseWhere(eql);
        assertNotNull(where);

        String before = where.toSqlString();

        new CollectionOperatorTransformer(new TestAliasGenerator(), new java.util.HashSet<>()).transform(where);

        assertEquals(normalize(before), normalize(where.toSqlString()));
    }

    @Test
    public void testMixedSomeAndAll() {
        String eql = "select o from User o where o.roles._some.name = 'admin' and o.roles._all.deleted = false";
        SqlWhere where = parseWhere(eql);
        assertNotNull(where);

        new CollectionOperatorTransformer(new TestAliasGenerator(), new java.util.HashSet<>()).transform(where);

        String expected = "where exists (select 1 from o.roles as t1 where t1.name = 'admin') and not exists (select 1 from o.roles as t2 where t2.deleted <> false)";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testDebugScopes() {
        String eql = "select o from User o where o.roles._all.status = 1 and o.roles._all.deleted = false";
        SqlWhere where = parseWhere(eql);
        assertNotNull(where);

        new CollectionOperatorTransformer(new TestAliasGenerator(), new java.util.HashSet<>()).transform(where);
    }

    @Test
    public void testAllOrSomeMixed() {
        String eql = "select o from User o where o.roles._all.status = 1 or o.roles._some.permission = 'special'";
        SqlWhere where = parseWhere(eql);
        assertNotNull(where);

        new CollectionOperatorTransformer(new TestAliasGenerator(), new java.util.HashSet<>()).transform(where);

        String expected = "where (not exists (select 1 from o.roles as t1 where t1.status <> 1) or exists (select 1 from o.roles as t2 where t2.permission = 'special'))";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testMixedAndOrWithParenthesesForSome() {
        String eql = "select o from User o where (o.roles._some.name = 'admin' or o.roles._some.name = 'reviewer') and o.roles._some.status = 1";
        SqlWhere where = parseWhere(eql);
        assertNotNull(where);

        new CollectionOperatorTransformer(new TestAliasGenerator(), new java.util.HashSet<>()).transform(where);

        String expected = "where exists (select 1 from o.roles as t1 where ((t1.name = 'admin') or (t1.name = 'reviewer')) and (t1.status = 1))";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testThreeLevelNestedOperators() {
        String eql = "select o from User o where o.roles._some.depts._all.teams._some.status = 1";
        SqlWhere where = parseWhere(eql);
        assertNotNull(where);

        new CollectionOperatorTransformer(new TestAliasGenerator(), new java.util.HashSet<>()).transform(where);

        // 期望修订依据：审计 P0-2.2-002（plan 2320）——旧期望 ∃r∃d∀t 是错误语义；
        // _some._all._some 的契约语义为 ∃r∀d∃tP，正确编码为 NOT EXISTS 逐层 XOR
        String expected = "where exists (select 1 from o.roles as t1 where not exists (select 1 from t1.depts as t2 where not exists (select 1 from t2.teams as t3 where t3.status = 1)))";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testNestedConditionsWithMerge() {
        String eql = "select o from User o where o.roles._some.depts._all.status = 1 and o.roles._some.depts._all.active = true";
        SqlWhere where = parseWhere(eql);
        assertNotNull(where);

        new CollectionOperatorTransformer(new TestAliasGenerator(), new java.util.HashSet<>()).transform(where);

        String expected = "where exists (select 1 from o.roles as t1 where not exists (select 1 from t1.depts as t2 where ((t2.status <> 1) or (t2.active <> true))))";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testAllWithBetween() {
        // _all + between 必须取反为 not between，否则语义从"所有都在区间内"反转为"没有任何在区间内"
        String eql = "select o from User o where o.roles._all.status between 1 and 3";
        SqlWhere where = parseWhere(eql);
        assertNotNull(where);

        new CollectionOperatorTransformer(new TestAliasGenerator(), new java.util.HashSet<>()).transform(where);

        String expected = "where not exists (select 1 from o.roles as t1 where t1.status not between 1 and 3)";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testAllWithIn() {
        String eql = "select o from User o where o.roles._all.status in (1, 2)";
        SqlWhere where = parseWhere(eql);
        assertNotNull(where);

        new CollectionOperatorTransformer(new TestAliasGenerator(), new java.util.HashSet<>()).transform(where);

        String expected = "where not exists (select 1 from o.roles as t1 where t1.status not in ( 1 , 2 ))";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testAllWithIsNull() {
        String eql = "select o from User o where o.roles._all.status is null";
        SqlWhere where = parseWhere(eql);
        assertNotNull(where);

        new CollectionOperatorTransformer(new TestAliasGenerator(), new java.util.HashSet<>()).transform(where);

        String expected = "where not exists (select 1 from o.roles as t1 where t1.status is not null)";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testAllWithLike() {
        String eql = "select o from User o where o.roles._all.name ilike 'a%'";
        SqlWhere where = parseWhere(eql);
        assertNotNull(where);

        new CollectionOperatorTransformer(new TestAliasGenerator(), new java.util.HashSet<>()).transform(where);

        String expected = "where not exists (select 1 from o.roles as t1 where not t1.name ilike 'a%')";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testAllWithBooleanColumnCondition() {
        // 裸布尔列条件无法直接改写，取反时必须包装为NOT表达式，不能静默丢失取反语义
        String eql = "select o from User o where o.roles._all.deleted";
        SqlWhere where = parseWhere(eql);
        assertNotNull(where);

        new CollectionOperatorTransformer(new TestAliasGenerator(), new java.util.HashSet<>()).transform(where);

        String expected = "where not exists (select 1 from o.roles as t1 where not ( t1.deleted ))";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    // ========== 以下回归测试对应 plan 2320（审计 P0-2.2-001/002/003 + P1-2.2-003 否定编码簇）==========

    @Test
    public void testNotSomeCondition() {
        // P0-2.2-001：not(col._some P) 必须是 NOT EXISTS(P)，而非 EXISTS(not P)
        String eql = "select o from User o where not (o.roles._some.status = 1)";
        SqlWhere where = parseWhere(eql);
        new CollectionOperatorTransformer(new TestAliasGenerator(), new java.util.HashSet<>()).transform(where);
        String expected = "where not exists (select 1 from o.roles as t1 where t1.status = 1)";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testNotSomeWithoutParentheses() {
        // 契约文档 §5 原文形态（无括号）
        String eql = "select o from User o where not o.roles._some.status = 1";
        SqlWhere where = parseWhere(eql);
        new CollectionOperatorTransformer(new TestAliasGenerator(), new java.util.HashSet<>()).transform(where);
        String expected = "where not exists (select 1 from o.roles as t1 where t1.status = 1)";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testNotAllCondition() {
        // P1-2.2-003：not(col._all P) 旧版编译崩溃；正确语义 ¬∀P ≡ ∃(¬P)
        String eql = "select o from User o where not (o.roles._all.status = 1)";
        SqlWhere where = parseWhere(eql);
        new CollectionOperatorTransformer(new TestAliasGenerator(), new java.util.HashSet<>()).transform(where);
        String expected = "where exists (select 1 from o.roles as t1 where t1.status <> 1)";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testNotAllWithoutParentheses() {
        String eql = "select o from User o where not o.roles._all.status = 1";
        SqlWhere where = parseWhere(eql);
        new CollectionOperatorTransformer(new TestAliasGenerator(), new java.util.HashSet<>()).transform(where);
        String expected = "where exists (select 1 from o.roles as t1 where t1.status <> 1)";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testDoubleNotSomeCondition() {
        // ¬¬∃P ≡ ∃P
        String eql = "select o from User o where not (not (o.roles._some.status = 1))";
        SqlWhere where = parseWhere(eql);
        new CollectionOperatorTransformer(new TestAliasGenerator(), new java.util.HashSet<>()).transform(where);
        String expected = "where exists (select 1 from o.roles as t1 where t1.status = 1)";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testNotNestedSomeSome() {
        // P0-2.2-003：¬∃∃P，否定不得错位到最内层
        String eql = "select o from User o where not (o.roles._some.depts._some.status = 1)";
        SqlWhere where = parseWhere(eql);
        new CollectionOperatorTransformer(new TestAliasGenerator(), new java.util.HashSet<>()).transform(where);
        String expected = "where not exists (select 1 from o.roles as t1 where exists (select 1 from t1.depts as t2 where t2.status = 1))";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testNotNestedSomeAll() {
        // 旧版在嵌套 _all 上更深触发崩溃；¬∃r∀dP ≡ NOT EXISTS(t1: NOT EXISTS(t2: ¬P))
        String eql = "select o from User o where not (o.roles._some.depts._all.status = 1)";
        SqlWhere where = parseWhere(eql);
        new CollectionOperatorTransformer(new TestAliasGenerator(), new java.util.HashSet<>()).transform(where);
        String expected = "where not exists (select 1 from o.roles as t1 where not exists (select 1 from t1.depts as t2 where t2.status <> 1))";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testNotNestedAllSome() {
        // ¬∀r∃dP ≡ ∃r∀d¬P ≡ EXISTS(t1: NOT EXISTS(t2: P))
        String eql = "select o from User o where not (o.roles._all.depts._some.status = 1)";
        SqlWhere where = parseWhere(eql);
        new CollectionOperatorTransformer(new TestAliasGenerator(), new java.util.HashSet<>()).transform(where);
        String expected = "where exists (select 1 from o.roles as t1 where not exists (select 1 from t1.depts as t2 where t2.status = 1))";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testNestedAllSomeQuantifierOrder() {
        // P0-2.2-002：_all._some 与 _some._all 不得编译出相同 SQL；∀r∃dP ≡ NOT EXISTS(t1: NOT EXISTS(t2: P))
        String eql = "select o from User o where o.roles._all.depts._some.status = 1";
        SqlWhere where = parseWhere(eql);
        new CollectionOperatorTransformer(new TestAliasGenerator(), new java.util.HashSet<>()).transform(where);
        String expected = "where not exists (select 1 from o.roles as t1 where not exists (select 1 from t1.depts as t2 where t2.status = 1))";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testNestedAllAllQuantifierOrder() {
        // ∀r∀dP ≡ NOT EXISTS(t1: EXISTS(t2: ¬P))
        String eql = "select o from User o where o.roles._all.depts._all.status = 1";
        SqlWhere where = parseWhere(eql);
        new CollectionOperatorTransformer(new TestAliasGenerator(), new java.util.HashSet<>()).transform(where);
        String expected = "where not exists (select 1 from o.roles as t1 where exists (select 1 from t1.depts as t2 where t2.status <> 1))";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testThreeLevelAllSomeSome() {
        // ∀r∃d∃tP ≡ NOT EXISTS(t1: NOT EXISTS(t2: EXISTS(t3: P)))
        String eql = "select o from User o where o.roles._all.depts._some.teams._some.status = 1";
        SqlWhere where = parseWhere(eql);
        new CollectionOperatorTransformer(new TestAliasGenerator(), new java.util.HashSet<>()).transform(where);
        String expected = "where not exists (select 1 from o.roles as t1 where not exists (select 1 from t1.depts as t2 where exists (select 1 from t2.teams as t3 where t3.status = 1)))";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testNotSomeAndConditionMergedUnderNot() {
        // merge 上提到 NOT 后：¬∃r(P∧Q) ≡ NOT EXISTS(t1: P∧Q)
        String eql = "select o from User o where not (o.roles._some.status = 1 and o.roles._some.deleted = 0)";
        SqlWhere where = parseWhere(eql);
        new CollectionOperatorTransformer(new TestAliasGenerator(), new java.util.HashSet<>()).transform(where);
        String expected = "where not exists (select 1 from o.roles as t1 where (t1.status = 1) and (t1.deleted = 0))";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testThreeLevelSomeSomeAll() {
        // closure audit Blocking#1 补齐：∃∃r∀dP ≡ EXISTS(t1: EXISTS(t2: NOT EXISTS(t3: ¬P)))（旧 parity 恰也对，回归守卫）
        String eql = "select o from User o where o.roles._some.depts._some.teams._all.status = 1";
        SqlWhere where = parseWhere(eql);
        new CollectionOperatorTransformer(new TestAliasGenerator(), new java.util.HashSet<>()).transform(where);
        String expected = "where exists (select 1 from o.roles as t1 where exists (select 1 from t1.depts as t2 where not exists (select 1 from t2.teams as t3 where t3.status <> 1)))";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }
}
