package io.nop.orm.eql.parse;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.unittest.BaseTestCase;
import io.nop.orm.eql.ast.EqlASTNode;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.SqlRegularFunction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI5 Phase 3：SQL-92 标准函数语法 {@code EXTRACT(f FROM d)} 的解析层归一化
 * （默认形态已钉死为逐字段函数：EXTRACT(YEAR FROM d) → year(d)，裁定 §1.3/§5）。
 *
 * <p>修复前：文法无 FROM 形态（{@code extractFunction} 注释块从未生效），{@code extract(year
 * from d)} 解析失败。修复后归一化为 {@link SqlRegularFunction}（name=字段名小写，单参）。
 * 字段规则 = 独立 extractField_ 规则（YEAR/MONTH/DAY/HOUR/MINUTE/SECOND/QUARTER 七个），
 * WEEK/MICROSECOND 等无 lowering 目标的字段在文法层即拒绝（响亮）。
 */
public class TestEqlExtractFunctionParse extends BaseTestCase {

    static SqlRegularFunction findRegularFunction(EqlASTNode node) {
        if (node instanceof SqlRegularFunction)
            return (SqlRegularFunction) node;
        SqlRegularFunction[] found = new SqlRegularFunction[1];
        node.forEachChild(child -> {
            if (found[0] == null)
                found[0] = findRegularFunction(child);
        });
        return found[0];
    }

    SqlProgram parse(String sql) {
        return new EqlASTParser().parseFromText(null, sql);
    }

    /**
     * 修复前红：extract(year from d) 解析失败；修复后归一化为 year(d)。
     */
    @Test
    public void testExtractYearNormalizedToYearFunction() {
        SqlProgram program = parse("select o.id from AppUser o where extract(year from o.createTime) = 2026");
        SqlRegularFunction fn = findRegularFunction(program);
        assertNotNull(fn, "extract(year from d) must parse and normalize");
        assertEquals("year", fn.getName());
        assertEquals(1, fn.getArgs().size());
        assertEquals("o.createTime", fn.getArgs().get(0).toSqlString());
        assertTrue(program.toSqlString().replaceAll("\\s+", "").contains("year(o.createTime)"),
                program.toSqlString());
    }

    /**
     * 字段大小写不敏感：keyword token 词法层大小写不敏感。
     */
    @Test
    public void testFieldCaseInsensitive() {
        SqlProgram program = parse("select o.id from AppUser o where extract(YEAR from o.createTime) = 2026");
        SqlRegularFunction fn = findRegularFunction(program);
        assertNotNull(fn);
        assertEquals("year", fn.getName());
    }

    /**
     * 全部七个注册字段各一条归一化用例（month/day/hour/minute/second/quarter 与
     * 方言函数表一一同名）。
     */
    @Test
    public void testAllSevenRegisteredFields() {
        String[] fields = {"month", "day", "hour", "minute", "second", "quarter"};
        for (String field : fields) {
            SqlProgram program = parse(
                    "select o.id from AppUser o where extract(" + field + " from o.createTime) = 1");
            SqlRegularFunction fn = findRegularFunction(program);
            assertNotNull(fn, field + " must parse");
            assertEquals(field, fn.getName());
            assertEquals(1, fn.getArgs().size());
        }
    }

    /**
     * 未注册字段（week）文法层拒绝：extractField_ 只放行七个有 lowering 目标的字段，
     * 解析失败走 antlr 错误码（响亮，早于 unknown-function）。
     */
    @Test
    public void testUnregisteredFieldRejectedAtGrammarLevel() {
        NopException e = assertThrows(NopException.class,
                () -> parse("select o.id from AppUser o where extract(week from o.createTime) = 1"));
        assertTrue(e.getErrorCode().startsWith("nop.err.antlr"),
                "unregistered field must fail at grammar level, got: " + e.getErrorCode());
    }

    /**
     * 既有逗号形态行为不变：extract(f, d) 仍解析为普通 SqlRegularFunction（name=extract）。
     */
    @Test
    public void testLegacyCommaFormUnchanged() {
        SqlProgram program = parse("select o.id from AppUser o where extract(o.createTime, 'year') = 1");
        SqlRegularFunction fn = findRegularFunction(program);
        assertNotNull(fn);
        assertEquals("extract", fn.getName());
        assertEquals(2, fn.getArgs().size());
        assertTrue(program.toSqlString().replaceAll("\\s+", "").contains("extract(o.createTime,'year')"),
                program.toSqlString());
    }
}
