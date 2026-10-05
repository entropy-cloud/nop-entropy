package io.nop.orm.eql.parse;

import io.nop.core.unittest.BaseTestCase;
import io.nop.orm.eql.ast.EqlASTNode;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.SqlRegularFunction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * WI5 Phase 1：SQL-92 标准函数语法 {@code POSITION(x IN y)} 的解析层归一化。
 *
 * <p>修复前：文法无该形态（{@code BaseRule.g4} 的 positionFunction 注释块自首次引入
 * 即注释态、从未生效），{@code position(x in y)} 解析失败（antlr parse error）。
 *
 * <p>修复后（裁定 = `ai-dev/design/eql-cross-db-compat/2026-10-04-wi5-phase0-adjudications.md`
 * §1.1/§3）：{@code sqlExpr_special} 新增 altLabel 备选分支，手写 EqlASTBuildVisitor
 * 构建期换序装入既有 {@link SqlRegularFunction}（name=instr，args=[source, begin]），
 * 不新增 AST 节点。既有逗号形态 {@code position(x,y)} 行为不变（可解析，编译期在未注册
 * 方言响亮报错——由 compile 层测试锁定）。
 */
public class TestEqlPositionFunctionParse extends BaseTestCase {

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
     * 修复前红：position(x in y) 解析失败；修复后归一化为 instr(source, begin)（调用点换序）。
     */
    @Test
    public void testPositionInFormNormalizedToInstrWithSwappedArgs() {
        SqlProgram program = parse("select o.id from AppUser o where position('b' in o.name) > 0");
        SqlRegularFunction fn = findRegularFunction(program);
        assertNotNull(fn, "position(x in y) must parse and normalize into SqlRegularFunction");
        assertEquals("instr", fn.getName());
        assertEquals(2, fn.getArgs().size());
        // 调用点换序：args = [source, begin]（instr(str, substr) 参数序：主串在前）
        assertEquals("o.name", fn.getArgs().get(0).toSqlString(), "first arg must be the searched string");
        assertEquals("'b'", fn.getArgs().get(1).toSqlString().trim(), "second arg must be the search substring");
        // 回显输出归一化后的形态
        String echo = program.toSqlString().replaceAll("\\s+", " ");
        assertEquals("instr(o.name,'b')", fn.toSqlString().replaceAll("\\s+", ""));
    }

    /**
     * 大小写不敏感：keyword token 词法层大小写不敏感（WI2 BUG-7 同源事实）。
     */
    @Test
    public void testLowerCasePositionForm() {
        SqlProgram program = parse("select o.id from AppUser o where position(o.name in 'abc') > 0");
        SqlRegularFunction fn = findRegularFunction(program);
        assertNotNull(fn);
        assertEquals("instr", fn.getName());
        assertEquals("'abc'", fn.getArgs().get(0).toSqlString().trim(), "first arg must be the searched string");
        assertEquals("o.name", fn.getArgs().get(1).toSqlString(), "second arg must be the search substring");
    }

    /**
     * POSITION 竞争登记（裁定 §3.4）：`position(a in (b,c))` 对 special 与 regular 两路
     * 均可行，ANTLR 自适应预测取 sqlExpr_functionCall 中备选序号最小者（sqlExpr_special
     * 先于 sqlRegularFunction）⇒ 恒归一化为 instr，行为确定。
     */
    @Test
    public void testInListCompetitionResolvesToSpecialForm() {
        SqlProgram program = parse("select o.id from AppUser o where position('b' in ('x','y')) > 0");
        SqlRegularFunction fn = findRegularFunction(program);
        assertNotNull(fn);
        assertEquals("instr", fn.getName());
        assertEquals(2, fn.getArgs().size());
    }

    /**
     * 既有逗号形态行为不变：position(x,y) 仍解析为普通 SqlRegularFunction（name=position）。
     * 其编译期行为（未注册方言响亮报错）由 compile 层测试锁定。
     */
    @Test
    public void testLegacyCommaFormUnchanged() {
        SqlProgram program = parse("select o.id from AppUser o where position(o.name, 'b') > 0");
        SqlRegularFunction fn = findRegularFunction(program);
        assertNotNull(fn);
        assertEquals("position", fn.getName());
        assertEquals(2, fn.getArgs().size());
        // 回显保持函数名原文（小写归一）与逗号形态
        assertTrue(program.toSqlString().replaceAll("\\s+", "").contains("position(o.name,'b')"),
                program.toSqlString());
    }
}
