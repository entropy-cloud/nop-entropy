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
 * WI5 Phase 4：TRIM 标准三段式的解析层归一化（裁定 §1.4/§4，Major-2）。
 *
 * <p>两档设计：无 chars 形态（{@code TRIM(BOTH FROM y)} → trim(y)、LEADING → ltrim(y)、
 * TRAILING → rtrim(y)，目标函数单参已注册）；带 chars 形态（{@code TRIM(BOTH 'x' FROM y)}
 * → trimChars(y,'x') 三件套，逐方言模板注册）。**单参 {@code trim(x)} 留在既有
 * sqlRegularFunction 路径，不新增第二个可解析入口**（守护用例锁定）。
 */
public class TestEqlTrimFunctionParse extends BaseTestCase {

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
     * 修复前红：无 chars 三方向形态各归一化为单参目标函数。
     */
    @Test
    public void testNoCharsFormsNormalizeToSingleArgTargets() {
        assertEquals("trim", parseName("select o.id from AppUser o where trim(both from o.name) = 'a'"));
        assertEquals("ltrim", parseName("select o.id from AppUser o where trim(leading from o.name) = 'a'"));
        assertEquals("rtrim", parseName("select o.id from AppUser o where trim(trailing from o.name) = 'a'"));
    }

    /**
     * 修复前红：带 chars 形态归一化为三件套（BOTH 与省略方向同 trimChars）。
     */
    @Test
    public void testCharsFormsNormalizeToCharsTargets() {
        assertEquals("trimChars", parseName("select o.id from AppUser o where trim(both 'x' from o.name) = 'a'"));
        assertEquals("trimChars", parseName("select o.id from AppUser o where trim('x' from o.name) = 'a'"));
        assertEquals("ltrimChars", parseName("select o.id from AppUser o where trim(leading ' ' from o.name) = 'a'"));
        assertEquals("rtrimChars", parseName("select o.id from AppUser o where trim(trailing ' ' from o.name) = 'a'"));
    }

    /**
     * 带 chars 归一化的参数序：args=[str, chars]（主串在前，instr/replace/lpad 同惯例）。
     */
    @Test
    public void testCharsFormArgumentOrder() {
        SqlProgram program = parse("select o.id from AppUser o where trim(both 'x' from o.name) = 'a'");
        SqlRegularFunction fn = findRegularFunction(program);
        assertNotNull(fn);
        assertEquals(2, fn.getArgs().size());
        assertEquals("o.name", fn.getArgs().get(0).toSqlString());
        assertEquals("'x'", fn.getArgs().get(1).toSqlString().trim());
        assertTrue(program.toSqlString().replaceAll("\\s+", "").contains("trimChars(o.name,'x')"),
                program.toSqlString());
    }

    /**
     * 单参 trim(x) 不新增第二个可解析入口：仍走 sqlRegularFunction（name=trim），回显逐字不变。
     */
    @Test
    public void testSingleArgFormStaysOnRegularFunctionPath() {
        SqlProgram program = parse("select o.id from AppUser o where trim(o.name) = 'a'");
        SqlRegularFunction fn = findRegularFunction(program);
        assertNotNull(fn);
        assertEquals("trim", fn.getName());
        assertEquals(1, fn.getArgs().size());
        // 回显逐字不变：name(args) 原样，无方向语法痕迹
        assertTrue(program.toSqlString().replaceAll("\\s+", "").contains("trim(o.name)"),
                program.toSqlString());
    }

    /**
     * 非法组合响亮失败：方向关键字后再跟 FROM 再跟 chars——文法层拒绝（antlr 错误码）。
     */
    @Test
    public void testIllegalCombinationFailsAtGrammarLevel() {
        NopException e = assertThrows(NopException.class,
                () -> parse("select o.id from AppUser o where trim(leading from 'x' from o.name) = 'a'"));
        assertTrue(e.getErrorCode().startsWith("nop.err.antlr"),
                "illegal combination must fail at grammar level, got: " + e.getErrorCode());
    }

    private String parseName(String sql) {
        SqlRegularFunction fn = findRegularFunction(parse(sql));
        assertNotNull(fn, sql);
        return fn.getName();
    }
}
