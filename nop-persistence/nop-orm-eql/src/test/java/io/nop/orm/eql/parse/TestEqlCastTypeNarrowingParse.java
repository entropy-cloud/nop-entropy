/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.orm.eql.parse;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdSqlType;
import io.nop.core.unittest.BaseTestCase;
import io.nop.orm.eql.ast.SqlCastExpr;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.SqlQuerySelect;
import io.nop.orm.eql.ast.SqlTypeExpr;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_INVALID_SQL_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * WI9a BUG-11：`dataTypeName_` 必拒项收窄（删除 14 个"解析可过、`SqlTypeExpr_name` 必拒"的死文法备选）。
 *
 * <p>裁定依据：`ai-dev/design/eql-cross-db-compat/2026-10-04-wi9a-phase0-adjudications.md`
 * §五——收窄边界=候选 A（保留 `sqlIdentifier_` 单词兜底，未知拼写仍走语义拒绝）。
 *
 * <p>错误形态迁移（EC ③ 对照表口径）：收窄前 = 解析通过 + `nop.err.eql.invalid-sql-type`；
 * 收窄后 = 解析失败，`nop.err.antlr.*` 前缀（{@code nop.err.antlr.common.parse-fail} /
 * {@code nop.err.antlr.not-end-properly}，见 `AntlrErrors`）。失败形态变化但同为响亮失败。
 *
 * <p>WI9b 边界：本收窄删除的 {@code TIME/TIMESTAMP WITH TIME ZONE}（非 LOCAL）与 WI9b 将引入的
 * {@code … WITH LOCAL TIME ZONE} 是两个名字集，互不替；本次改动未引入任何时区类型拼写/枚举变化。
 */
public class TestEqlCastTypeNarrowingParse extends BaseTestCase {

    /**
     * 被删除的 14 个死备选（裁定文档 §五对照表行序）。
     */
    static final String[] DEAD_ALTERNATIVES = {
            "character", "character varying", "national character", "national character varying",
            "nchar", "national char", "national char varying", "bit", "bit varying", "dec",
            "double precision", "interval", "time with time zone", "timestamp with time zone"};

    /**
     * 已知不可拼写组：关键字 token 且无显式备选（收窄前即无拼写入口，非本次收窄引入）。
     */
    static final Set<String> UNSPELLABLE = new HashSet<>(Arrays.asList("DOUBLE", "ARRAY", "ANY"));

    static SqlTypeExpr parseCastType(String sql) {
        SqlProgram program = new EqlASTParser().parseFromText(null, sql);
        SqlQuerySelect select = (SqlQuerySelect) program.getStatements().get(0);
        io.nop.orm.eql.ast.SqlExprProjection proj = (io.nop.orm.eql.ast.SqlExprProjection) select.getProjections()
                .get(0);
        SqlCastExpr cast = (SqlCastExpr) proj.getExpr();
        return cast.getDataType();
    }

    // ---- 红→绿锚：死备选从"解析过 + 语义拒绝"迁移为"解析失败" ----

    @Test
    public void testDeadAlternativesFailAtParseWithAntlrError() {
        // 汇总全部违规再断言：红态下一次性暴露 14 项的收窄前行为（解析过 / nop.err.eql.invalid-sql-type）
        java.util.List<String> violations = new java.util.ArrayList<>();
        for (String spelling : DEAD_ALTERNATIVES) {
            try {
                parseCastType("select cast(a.id as " + spelling + ") from t a");
                violations.add(spelling + ": parsed OK (expected parse failure)");
            } catch (NopException e) {
                if (!e.getErrorCode().startsWith("nop.err.antlr"))
                    violations.add(spelling + ": semantic rejection " + e.getErrorCode() + " (expected parse failure)");
            }
        }
        assertTrue(violations.isEmpty(),
                () -> "dead alternatives must fail at parse with nop.err.antlr.*: " + violations);
    }

    @Test
    public void testDeadAlternativesUppercaseAlsoFailAtParse() {
        for (String spelling : Arrays.asList("DEC", "BIT", "DOUBLE PRECISION", "TIME WITH TIME ZONE")) {
            assertThrows(NopException.class,
                    () -> parseCastType("select cast(a.id as " + spelling + ") from t a"),
                    spelling + " must fail at parse in any case");
        }
    }

    // ---- 不变组：候选 A 下兜底与活备选行为不变 ----

    @Test
    public void testAliveExplicitAlternativesUnchanged() {
        // 12 个活显式备选照常解析（BOOLEAN 的 WI2 底线由 TestEqlCastBooleanParse 守护）
        for (String spelling : Arrays.asList("char", "varchar", "numeric", "decimal", "integer", "smallint",
                "float", "real", "date", "time", "timestamp", "boolean")) {
            assertEquals(spelling.toUpperCase(Locale.ROOT),
                    parseCastType("select cast(a.id as " + spelling + ") from t a").getName(), spelling);
        }
    }

    @Test
    public void testUnknownSpellingStillSemanticRejection() {
        // 兜底保留（候选 A）：未知拼写经 sqlIdentifier_ 进入闸门 A，仍响亮语义拒绝（非解析失败）
        try {
            parseCastType("select cast(a.id as nosuchtype) from t a");
            fail("nosuchtype must be rejected");
        } catch (NopException e) {
            assertEquals(ERR_EQL_INVALID_SQL_TYPE.getErrorCode(), e.getErrorCode());
        }
    }

    @Test
    public void testIdentifierFallbackSpellingsStillParse() {
        // 词法化为 IDENTIFIER_ 的枚举名 + 别名经兜底照常解析（文法放行 ⇒ 闸门 A 必过）
        for (String spelling : Arrays.asList("tinyint", "bigint", "datetime", "binary", "varbinary",
                "json", "clob", "blob", "geometry", "text", "number", "bool", "uuid")) {
            parseCastType("select cast(a.id as " + spelling + ") from t a");
        }
    }

    @Test
    public void testEveryRegisteredStdNameGrammarAcceptableOrExplicitlyUnspellable() {
        // 穷举式证明（Phase 2 EC1）：stdNameMap 每个键的小写拼写，要么解析通过（闸门 A 必过，
        // 不存在"文法放行 + 语义拒绝"的注册键），要么解析失败（nop.err.antlr.*，仅限已知不可拼写组）
        for (String name : StdSqlType.getNames()) {
            try {
                SqlTypeExpr t = parseCastType("select cast(a.id as " + name.toLowerCase(Locale.ROOT) + ") from t a");
                assertEquals(name, t.getName(), name);
            } catch (NopException e) {
                assertTrue(UNSPELLABLE.contains(name),
                        "registered std name must be grammar-acceptable: " + name + " got " + e.getErrorCode());
                assertTrue(e.getErrorCode().startsWith("nop.err.antlr"),
                        name + " unspellable must fail at parse, got: " + e.getErrorCode());
            }
        }
    }
}
