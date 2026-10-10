/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical_entropy
 * Gitee:  https://gitee.com/nop-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.parse;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.unittest.BaseTestCase;
import io.nop.orm.eql.ast.SqlAssignment;
import io.nop.orm.eql.ast.SqlColumnName;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.SqlStatementKind;
import io.nop.orm.eql.ast.SqlUpsert;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2380 Phase 3：{@code upsert} 语句的文法解析矩阵。
 *
 * <p>权威设计：{@code ai-dev/design/eql-cross-db-compat/2026-10-10-eql-upsert-and-returning-design.md} §3.1。
 *
 * <p>本类只断言 parse 成败与 AST 字段，<b>不做 toSQL() 往返断言</b>——方言 lowering 与回显打印器
 * 在 Phase 4 落地，沿用 {@code TestEqlTumbleGrammarParse} 的同款纪律（round-trip 不对称在案）。
 */
public class TestEqlUpsertGrammarParse extends BaseTestCase {

    SqlProgram parse(String sql) {
        return new EqlASTParser().parseFromText(null, sql);
    }

    SqlUpsert parseUpsert(String sql) {
        SqlProgram program = parse(sql);
        assertNotNull(program, "parse must succeed: " + sql);
        assertEquals(1, program.getStatements().size(), "exactly one statement: " + sql);
        assertTrue(program.getStatements().get(0) instanceof SqlUpsert,
                "statement must be SqlUpsert: " + sql);
        return (SqlUpsert) program.getStatements().get(0);
    }

    static List<String> names(List<SqlColumnName> columns) {
        return columns.stream().map(SqlColumnName::getName).collect(Collectors.toList());
    }

    // ---- success: parse 矩阵 ----

    @Test
    public void testBasicForm() {
        SqlUpsert node = parseUpsert(
                "upsert into AppUser (id, name, age) values (?, ?, ?) "
                        + "on conflict (id) do update set name = ?, age = ?");
        assertEquals(SqlStatementKind.UPSERT, node.getStatementKind());
        assertEquals("AppUser", node.getTableName().getName());
        assertEquals(List.of("id", "name", "age"), names(node.getColumns()));
        assertNotNull(node.getValues(), "values clause must be present");
        assertEquals(List.of("id"), names(node.getConflictColumns()));
        assertEquals(2, node.getAssignments().size());
    }

    @Test
    public void testLowerCaseKeywords() {
        assertNotNull(parse(
                "upsert into T (a, b) values (?, ?) on conflict (a) do update set b = ?"),
                "keywords are case-insensitive (Alphabet.g4 fragments)");
    }

    @Test
    public void testUpperCaseKeywords() {
        assertNotNull(parse(
                "UPSERT INTO T (a, b) VALUES (?, ?) ON CONFLICT (a) DO UPDATE SET b = ?"));
    }

    @Test
    public void testCompositeConflictKey() {
        SqlUpsert node = parseUpsert(
                "upsert into T (a, b, c) values (?, ?, ?) "
                        + "on conflict (a, b) do update set c = ?");
        assertEquals(List.of("a", "b"), names(node.getConflictColumns()));
    }

    @Test
    public void testAssignmentTargetsAndExpressions() {
        SqlUpsert node = parseUpsert(
                "upsert into T (a, b, c) values (1, 2, 3) "
                        + "on conflict (a) do update set b = b + 1, c = 'x'");
        List<SqlAssignment> assignments = node.getAssignments();
        assertEquals(2, assignments.size());
        assertEquals("b", assignments.get(0).getColumnName().getName());
        assertNotNull(assignments.get(0).getExpr(), "RHS expression must be parsed");
        assertEquals("c", assignments.get(1).getColumnName().getName());
    }

    /**
     * 更新集覆盖全部列清单（含冲突键列本身）——这是 H2 分支的两道必要条件之一
     * （design §3.6），语法层必须能表达这种形态。
     */
    @Test
    public void testUpdateSetCoversAllColumnsIncludingConflictKey() {
        SqlUpsert node = parseUpsert(
                "upsert into T (a, b) values (1, 2) on conflict (a) do update set a = ?, b = ?");
        assertEquals(List.of("a", "b"),
                node.getAssignments().stream().map(x -> x.getColumnName().getName()).collect(Collectors.toList()));
    }

    @Test
    public void testQuotedIdentifiers() {
        SqlUpsert node = parseUpsert(
                "upsert into \"T\" (\"a\", \"select\") values (?, ?) "
                        + "on conflict (\"a\") do update set \"select\" = ?");
        assertEquals(List.of("\"a\""), names(node.getConflictColumns()));
    }

    // ---- AST field assertions ----

    @Test
    public void testValuesArityMatchesColumns() {
        SqlUpsert node = parseUpsert(
                "upsert into T (a, b, c) values (1, 2, 3) on conflict (a) do update set b = 2, c = 3");
        assertEquals(node.getColumns().size(), node.getValues().getValues().size(),
                "VALUES arity must equal column count");
    }

    @Test
    public void testDecoratorsSupported() {
        SqlUpsert node = parseUpsert(
                "@cache(1000) upsert into T (a) values (1) on conflict (a) do update set a = 1");
        assertNotNull(node.getDecorators());
    }

    // ---- failure: required parts missing ----

    @Test
    public void testMissingOnConflictFails() {
        assertThrows(NopException.class, () ->
                parse("upsert into T (a, b) values (1, 2) do update set b = 2"),
                "conflict clause is mandatory");
    }

    @Test
    public void testMissingDoUpdateFails() {
        assertThrows(NopException.class, () ->
                parse("upsert into T (a, b) values (1, 2) on conflict (a)"),
                "DO UPDATE clause is mandatory");
    }

    @Test
    public void testMissingSetFails() {
        assertThrows(NopException.class, () ->
                parse("upsert into T (a, b) values (1, 2) on conflict (a) do update"),
                "SET clause is mandatory");
    }

    @Test
    public void testEmptyConflictColumnsFails() {
        assertThrows(NopException.class, () ->
                parse("upsert into T (a) values (1) on conflict () do update set a = 1"),
                "conflict column list must not be empty");
    }

    @Test
    public void testEmptyUpdateSetFails() {
        assertThrows(NopException.class, () ->
                parse("upsert into T (a) values (1) on conflict (a) do update set"),
                "update assignment list must not be empty");
    }

    @Test
    public void testSelectSourceUpsertFails() {
        assertThrows(NopException.class, () ->
                parse("upsert into T (a, b) select a, b from S on conflict (a) do update set b = 2"),
                "v1 supports only the VALUES form (design 3.1)");
    }

    @Test
    public void testDoNothingFormFails() {
        assertThrows(NopException.class, () ->
                parse("upsert into T (a) values (1) on conflict (a) do nothing"),
                "DO NOTHING is not part of the EQL upsert surface; use a self-assigning update set");
    }

    // ---- identifier compatibility (upsert / conflict are unreserved keywords) ----

    @Test
    public void testUpsertRemainsUsableAsIdentifier() {
        assertNotNull(parse("select upsert from t"), "column name");
        assertNotNull(parse("select a from upsert"), "table name");
        assertNotNull(parse("select a from t where upsert > 1"), "predicate left side");
        assertNotNull(parse("select a from t upsert"), "alias");
    }

    @Test
    public void testConflictRemainsUsableAsIdentifier() {
        assertNotNull(parse("select conflict from t"), "column name");
        assertNotNull(parse("select a from conflict"), "table name");
        assertNotNull(parse("select a from t where conflict > 1"), "predicate left side");
        assertNotNull(parse("select a from t conflict"), "alias");
    }

    /** upsert 语句与 delete 的别名形态不产生语句起始歧义（首 token 不同）。 */
    @Test
    public void testUpsertKeywordInOtherStatementPositions() {
        assertNotNull(parse("delete from T upsert"));
        assertNotNull(parse("select upsert.conflict from T upsert"));
    }
}