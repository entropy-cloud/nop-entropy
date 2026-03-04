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

        new CollectionOperatorTransformer(new TestAliasGenerator()).transform(where);

        String expected = "where exists (select 1 from o.roles as t1 where t1.status = 1)";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testSingleAllCondition() {
        String eql = "select o from User o where o.roles._all.status = 1";
        SqlWhere where = parseWhere(eql);
        assertNotNull(where);

        new CollectionOperatorTransformer(new TestAliasGenerator()).transform(where);

        String expected = "where not exists (select 1 from o.roles as t1 where t1.status <> 1)";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testMultipleSomeAndConditionsMerge() {
        String eql = "select o from User o where o.roles._some.name = 'admin' and o.roles._some.status = 1";
        SqlWhere where = parseWhere(eql);
        assertNotNull(where);

        new CollectionOperatorTransformer(new TestAliasGenerator()).transform(where);

        String expected = "where exists (select 1 from o.roles as t1 where (t1.name = 'admin') and (t1.status = 1))";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testMultipleSomeOrConditionsMerge() {
        String eql = "select o from User o where o.roles._some.name = 'admin' or o.roles._some.name = 'reviewer'";
        SqlWhere where = parseWhere(eql);
        assertNotNull(where);

        new CollectionOperatorTransformer(new TestAliasGenerator()).transform(where);

        String expected = "where exists (select 1 from o.roles as t1 where ((t1.name = 'admin') or (t1.name = 'reviewer')))";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testMultipleAllAndConditionsMerge() {
        String eql = "select o from User o where o.roles._all.status = 1 and o.roles._all.deleted = false";
        SqlWhere where = parseWhere(eql);
        assertNotNull(where);

        new CollectionOperatorTransformer(new TestAliasGenerator()).transform(where);

        String expected = "where not exists (select 1 from o.roles as t1 where ((t1.status <> 1) or (t1.deleted <> 0)))";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testMultipleAllOrConditionsNoMerge() {
        String eql = "select o from User o where o.roles._all.status = 1 or o.roles._all.deleted = false";
        SqlWhere where = parseWhere(eql);
        assertNotNull(where);

        new CollectionOperatorTransformer(new TestAliasGenerator()).transform(where);

        String expected = "where (not exists (select 1 from o.roles as t1 where t1.status <> 1) or not exists (select 1 from o.roles as t2 where t2.deleted <> 0))";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testNestedCollectionOperators() {
        String eql = "select o from User o where o.roles._some.depts._all.status = 1";
        SqlWhere where = parseWhere(eql);
        assertNotNull(where);

        new CollectionOperatorTransformer(new TestAliasGenerator()).transform(where);

        String expected = "where exists (select 1 from o.roles as t1 where not exists (select 1 from t1.depts as t2 where t2.status <> 1))";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testNoCollectionOperator() {
        String eql = "select o from User o where o.status = 1 and o.name = 'test'";
        SqlWhere where = parseWhere(eql);
        assertNotNull(where);

        String before = where.toSqlString();

        new CollectionOperatorTransformer(new TestAliasGenerator()).transform(where);

        assertEquals(normalize(before), normalize(where.toSqlString()));
    }

    @Test
    public void testMixedSomeAndAll() {
        String eql = "select o from User o where o.roles._some.name = 'admin' and o.roles._all.deleted = false";
        SqlWhere where = parseWhere(eql);
        assertNotNull(where);

        new CollectionOperatorTransformer(new TestAliasGenerator()).transform(where);

        String expected = "where exists (select 1 from o.roles as t1 where t1.name = 'admin') and not exists (select 1 from o.roles as t2 where t2.deleted <> 0)";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testDebugScopes() {
        String eql = "select o from User o where o.roles._all.status = 1 and o.roles._all.deleted = false";
        SqlWhere where = parseWhere(eql);
        assertNotNull(where);

        new CollectionOperatorTransformer(new TestAliasGenerator()).transform(where);
    }

    @Test
    public void testAllOrSomeMixed() {
        String eql = "select o from User o where o.roles._all.status = 1 or o.roles._some.permission = 'special'";
        SqlWhere where = parseWhere(eql);
        assertNotNull(where);

        new CollectionOperatorTransformer(new TestAliasGenerator()).transform(where);

        String expected = "where (not exists (select 1 from o.roles as t1 where t1.status <> 1) or exists (select 1 from o.roles as t2 where t2.permission = 'special'))";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testMixedAndOrWithParenthesesForSome() {
        String eql = "select o from User o where (o.roles._some.name = 'admin' or o.roles._some.name = 'reviewer') and o.roles._some.status = 1";
        SqlWhere where = parseWhere(eql);
        assertNotNull(where);

        new CollectionOperatorTransformer(new TestAliasGenerator()).transform(where);

        String expected = "where exists (select 1 from o.roles as t1 where ((t1.name = 'admin') or (t1.name = 'reviewer')) and (t1.status = 1))";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testThreeLevelNestedOperators() {
        String eql = "select o from User o where o.roles._some.depts._all.teams._some.status = 1";
        SqlWhere where = parseWhere(eql);
        assertNotNull(where);

        new CollectionOperatorTransformer(new TestAliasGenerator()).transform(where);

        String expected = "where exists (select 1 from o.roles as t1 where exists (select 1 from t1.depts as t2 where not exists (select 1 from t2.teams as t3 where t3.status <> 1)))";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }

    @Test
    public void testNestedConditionsWithMerge() {
        String eql = "select o from User o where o.roles._some.depts._all.status = 1 and o.roles._some.depts._all.active = true";
        SqlWhere where = parseWhere(eql);
        assertNotNull(where);

        new CollectionOperatorTransformer(new TestAliasGenerator()).transform(where);

        String expected = "where exists (select 1 from o.roles as t1 where not exists (select 1 from t1.depts as t2 where ((t2.status <> 1) or (t2.active <> 1))))";
        assertEquals(normalize(expected), normalize(where.toSqlString()));
    }
}
