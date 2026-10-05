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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static io.nop.orm.eql.OrmEqlErrors.ARG_ALLOWED_NAMES;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_INVALID_SQL_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * WI9b（plan 32 Phase 2）：`dataTypeName_` 新增 {@code TIME WITH LOCAL TIME ZONE} /
 * {@code TIMESTAMP WITH LOCAL TIME ZONE} 两多词备选 + 手写闸门 A 的多 token 名称归一化。
 *
 * <p>裁定依据：`ai-dev/design/eql-cross-db-compat/2026-10-05-wi9b-phase0-adjudications.md`
 * §五——① 标准拼写两多词名；② bare 形态（precision 位置限制登记为 Deferred watch-only）；
 * ③ 非 LOCAL 变体（{@code TIME/TIMESTAMP WITH TIME ZONE}）不恢复终裁；④ 闸门 A 适配：
 * `getText()` 无分隔符拼接会使多词备选以 "TIMEWITHLOCALTIMEZONE" 必拒（BUG-11 同型），
 * 多 token span 重建含空白原文后空白归一化为 `_`。
 *
 * <p>红→绿锚：修复前两多词 LOCAL 拼写解析失败（`nop.err.antlr.*` 族，WI9a 收窄后形态基线，
 * Phase 2 红证据 `_tmp/wi9b/red-phase2.log`）；修复后解析 → 闸门 A → AST 名为枚举名。
 */
public class TestEqlCastTimezoneTypeParse {

    static SqlTypeExpr parseCastType(String sql) {
        SqlProgram program = new EqlASTParser().parseFromText(null, sql);
        SqlQuerySelect select = (SqlQuerySelect) program.getStatements().get(0);
        io.nop.orm.eql.ast.SqlExprProjection proj = (io.nop.orm.eql.ast.SqlExprProjection) select.getProjections()
                .get(0);
        SqlCastExpr cast = (SqlCastExpr) proj.getExpr();
        return cast.getDataType();
    }

    // ---- 红→绿锚：多词 LOCAL 拼写从解析失败迁移为解析过 + 枚举名入库 ----

    @Test
    public void testLocalTimeSpellingParsesToEnumName() {
        assertEquals("TIME_WITH_LOCAL_TIME_ZONE",
                parseCastType("select cast(a.id as time with local time zone) from t a").getName());
    }

    @Test
    public void testLocalTimestampSpellingParsesToEnumName() {
        assertEquals("TIMESTAMP_WITH_LOCAL_TIME_ZONE",
                parseCastType("select cast(a.id as timestamp with local time zone) from t a").getName());
    }

    @Test
    public void testLocalSpellingsCaseAndWhitespaceInsensitive() {
        // 大小写不敏感 + 多空白/换行分隔（闸门 A 归一化：trim → 大写 → 空白串转 `_`）
        assertEquals("TIME_WITH_LOCAL_TIME_ZONE", parseCastType(
                "select cast(a.id as TIME  WITH\n\tLOCAL time zone) from t a").getName());
        assertEquals("TIMESTAMP_WITH_LOCAL_TIME_ZONE", parseCastType(
                "select cast(a.id as Timestamp With Local Time Zone) from t a").getName());
    }

    @Test
    public void testUnderscoreSpellingViaIdentifierFallback() {
        // 单 token 下划线拼写经 sqlIdentifier_ 兜底 + 闸门 A 直查（Phase 1 落活后即通，文法新增不改变它）
        assertEquals("TIME_WITH_LOCAL_TIME_ZONE",
                parseCastType("select cast(a.id as time_with_local_time_zone) from t a").getName());
        assertEquals("TIMESTAMP_WITH_LOCAL_TIME_ZONE",
                parseCastType("select cast(a.id as TIMESTAMP_WITH_LOCAL_TIME_ZONE) from t a").getName());
    }

    // ---- 非 LOCAL 变体维持删除态（§5.2 终裁；WI9a 收窄不回退） ----

    @Test
    public void testNonLocalSpellingsRemainDeleted() {
        List<String> violations = new ArrayList<>();
        for (String spelling : Arrays.asList("time with time zone", "timestamp with time zone",
                "TIME WITH TIME ZONE", "TIMESTAMP WITH TIME ZONE")) {
            try {
                parseCastType("select cast(a.id as " + spelling + ") from t a");
                violations.add(spelling + ": parsed OK (must remain deleted, BUG-11 revival)");
            } catch (NopException e) {
                if (!e.getErrorCode().startsWith("nop.err.antlr"))
                    violations.add(spelling + ": wrong failure " + e.getErrorCode());
            }
        }
        assertTrue(violations.isEmpty(), () -> "non-LOCAL variants must stay parse-failures: " + violations);
    }

    // ---- precision 位置限制（Deferred 段登记的已知能力边界，按实测行为分两形态） ----

    @Test
    public void testPrecisionBeforeSuffixFailsAtParse() {
        // `sqlTypeExpr` 的 (precision[,scale]) 备选消费掉 precision 后多词后缀无法续接 ⇒ 前置形态解析失败
        for (String spelling : Arrays.asList("timestamp(6) with local time zone", "time(3) with local time zone")) {
            try {
                parseCastType("select cast(a.id as " + spelling + ") from t a");
                fail(spelling + " must fail at parse (registered capability boundary)");
            } catch (NopException e) {
                assertTrue(e.getErrorCode().startsWith("nop.err.antlr"),
                        spelling + " expected parse failure, got: " + e.getErrorCode());
            }
        }
    }

    @Test
    public void testPrecisionAfterSuffixParsesWithPrecisionInAst() {
        // 实测行为（2026-10-05）：precision 后置形态 `... with local time zone(6)` 可解析——
        // (precision[,scale]) 组匹配多词备选之后的 (6)，AST SqlTypeExpr 携带 precision=6。
        // 生成侧按成员 (false,false) 裁定不渲染 precision（与既有 date(3)/boolean(6) 等非精度
        // std 类型同型行为，非本 plan 引入的回归）；AST 名仍为完整枚举名。
        SqlTypeExpr t = parseCastType("select cast(a.id as timestamp with local time zone(6)) from t a");
        assertEquals("TIMESTAMP_WITH_LOCAL_TIME_ZONE", t.getName());
        assertEquals(6, t.getPrecision());
    }

    // ---- 既有底线存活：nosuchtype 语义拒绝 + ARG_ALLOWED_NAMES 含新键 ----

    @Test
    public void testUnknownTypeSemanticRejectionListsNewKeys() {
        try {
            parseCastType("select cast(a.id as nosuchtype) from t a");
            fail("nosuchtype must be rejected");
        } catch (NopException e) {
            assertEquals(ERR_EQL_INVALID_SQL_TYPE.getErrorCode(), e.getErrorCode());
            @SuppressWarnings("unchecked")
            java.util.Set<String> allowed = (java.util.Set<String>) e.getParam(ARG_ALLOWED_NAMES);
            assertTrue(allowed.contains("TIME_WITH_LOCAL_TIME_ZONE"),
                    "ARG_ALLOWED_NAMES must contain new key after 30→32 expansion");
            assertTrue(allowed.contains("TIMESTAMP_WITH_LOCAL_TIME_ZONE"));
            assertEquals(32, allowed.size());
        }
    }

    @Test
    public void testAliveAlternativesUnchangedByGrammarAddition() {
        // 新增备选不改变既有单 word 备选与兜底（防备选顺序/歧义回归）
        for (String spelling : Arrays.asList("char", "varchar", "time", "timestamp", "boolean",
                "tinyint", "json", "uuid", "text", "number", "bool")) {
            assertEquals(spelling.toUpperCase(Locale.ROOT),
                    parseCastType("select cast(a.id as " + spelling + ") from t a").getName(), spelling);
        }
    }

    @Test
    public void testEveryRegisteredStdNameGrammarAcceptable() {
        // 穷举式证明随键集 32 扩展自动覆盖新成员：文法放行 ⇒ 闸门 A 必过（双拼写形态：
        // 下划线经兜底、多词经新备选；DOUBLE/ARRAY/ANY 仍为已知不可拼写组）
        for (String name : StdSqlType.getNames()) {
            try {
                SqlTypeExpr t = parseCastType("select cast(a.id as " + name.toLowerCase(Locale.ROOT) + ") from t a");
                assertEquals(name, t.getName(), name);
            } catch (NopException e) {
                // 仅关键字 token 且无显式备选的三个成员保持解析失败（WI9a §五已知缺口）
                assertTrue(Arrays.asList("DOUBLE", "ARRAY", "ANY").contains(name),
                        "registered std name must be grammar-acceptable: " + name + " got " + e.getErrorCode());
                assertTrue(e.getErrorCode().startsWith("nop.err.antlr"),
                        name + " unspellable must fail at parse, got: " + e.getErrorCode());
            }
        }
    }

    @Test
    public void testMultiwordSpellingResolvesToFullEnumNameNotBarePrefix() {
        // `time with local time zone` 不能被误解析为 bare TIME（防备选顺序/歧义回归）：
        // AST 名必须是完整枚举名
        assertEquals("TIME_WITH_LOCAL_TIME_ZONE",
                parseCastType("select cast(a.id as time with local time zone) from t a").getName());
        assertEquals("TIMESTAMP_WITH_LOCAL_TIME_ZONE",
                parseCastType("select cast(a.id as timestamp with local time zone) from t a").getName());
    }
}
