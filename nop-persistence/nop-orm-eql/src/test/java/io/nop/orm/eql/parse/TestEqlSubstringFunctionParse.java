package io.nop.orm.eql.parse;

import io.nop.core.unittest.BaseTestCase;
import io.nop.orm.eql.ast.EqlASTNode;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.SqlRegularFunction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI5 Phase 2：SQL-92 标准函数语法 {@code SUBSTRING(x FROM a [FOR b])} 的解析层归一化。
 *
 * <p>修复前：文法无 FROM 三段式（comma 形态经 sqlIdentifier_func_ 的 SUBSTRING 可解析），
 * {@code substring(x from 2)} 解析失败（antlr parse error）。
 *
 * <p>修复后（裁定 = `ai-dev/design/eql-cross-db-compat/2026-10-04-wi5-phase0-adjudications.md`
 * §1.2/§3）：归一化为 {@link SqlRegularFunction}（name=substr，2 或 3 参，复用
 * default.dialect.xml 的 minArgCount=2/maxArgCount=3 多态），不新增 AST 节点。
 */
public class TestEqlSubstringFunctionParse extends BaseTestCase {

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
     * 修复前红：substring(x from 2) 解析失败；修复后归一化为 2 参 substr。
     */
    @Test
    public void testFromFormNormalizedToTwoArgSubstr() {
        SqlProgram program = parse("select o.id from AppUser o where substring(o.name from 2) = 'b'");
        SqlRegularFunction fn = findRegularFunction(program);
        assertNotNull(fn, "substring(x from 2) must parse and normalize");
        assertEquals("substr", fn.getName());
        assertEquals(2, fn.getArgs().size());
        assertEquals("o.name", fn.getArgs().get(0).toSqlString());
        assertEquals("2", fn.getArgs().get(1).toSqlString().trim());
        // 回显输出归一化后的形态
        assertTrue(program.toSqlString().replaceAll("\\s+", "").contains("substr(o.name,2)"),
                program.toSqlString());
    }

    /**
     * 修复前红：substring(x from a for b) 解析失败；修复后归一化为 3 参 substr。
     */
    @Test
    public void testFromForFormNormalizedToThreeArgSubstr() {
        SqlProgram program = parse("select o.id from AppUser o where substring(o.name from 2 for 3) = 'b'");
        SqlRegularFunction fn = findRegularFunction(program);
        assertNotNull(fn, "substring(x from 2 for 3) must parse and normalize");
        assertEquals("substr", fn.getName());
        assertEquals(3, fn.getArgs().size());
        assertEquals("o.name", fn.getArgs().get(0).toSqlString());
        assertEquals("2", fn.getArgs().get(1).toSqlString().trim());
        assertEquals("3", fn.getArgs().get(2).toSqlString().trim());
    }

    /**
     * 表达式参数：FROM/FOR 位置不限于字面量。
     */
    @Test
    public void testExpressionArguments() {
        SqlProgram program = parse(
                "select o.id from AppUser o where substring(o.name from o.id + 1 for length(o.name)) = 'b'");
        SqlRegularFunction fn = findRegularFunction(program);
        assertNotNull(fn);
        assertEquals("substr", fn.getName());
        assertEquals(3, fn.getArgs().size());
    }

    /**
     * 既有逗号形态行为不变：substring(x,a,b) 仍解析为普通 SqlRegularFunction（name=substring
     * ——注册名是 substr，故编译期未注册响亮报错，由 compile 层守护）。
     */
    @Test
    public void testLegacyCommaFormUnchanged() {
        SqlProgram program = parse("select o.id from AppUser o where substring(o.name, 2, 3) = 'b'");
        SqlRegularFunction fn = findRegularFunction(program);
        assertNotNull(fn);
        assertEquals("substring", fn.getName());
        assertEquals(3, fn.getArgs().size());
        assertTrue(program.toSqlString().replaceAll("\\s+", "").contains("substring(o.name,2,3)"),
                program.toSqlString());
    }
}
