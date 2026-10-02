package io.nop.xlang.compile;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.Program;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI3 补强：PrintResolvedIdentifier（WI0 快照 0% 靶点，61 行）。
 * 对经过词法作用域分析的表达式做调试打印：标识符解析结果、block/function/lambda 结构、
 * 闭包变量引用（&var,slot=N）。
 */
public class TestPrintResolvedIdentifier extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static String print(String source) {
        XLangCompileTool cp = XLang.newCompileTool().allowUnregisteredScopeVar(true);
        Program program = cp.parseFullExpr(null, source);
        Expression analyzed = new LexicalScopeAnalysis(cp.getScope()).analyze(program);
        PrintResolvedIdentifier printer = new PrintResolvedIdentifier();
        printer.visit(analyzed);
        return printer.getOutput();
    }

    /**
     * 标识符解析后打印 "name => 定义"，比较表达式两侧以运算符连接。
     */
    @Test
    public void testPrintResolvedCompareExpression() {
        String output = print("let x = 1; x < 10");
        assertTrue(output.contains("x => "), "必须输出标识符解析结果，实际: " + output);
        assertTrue(output.contains("kind=VAR_DECL"),
                "标识符必须解析到变量声明（VAR_DECL），实际: " + output);
    }

    /**
     * getOutput 返回累积缓冲，重复 visit 追加输出。
     */
    @Test
    public void testOutputAccumulatesAcrossVisits() {
        XLangCompileTool cp = XLang.newCompileTool().allowUnregisteredScopeVar(true);

        PrintResolvedIdentifier printer = new PrintResolvedIdentifier();
        printer.visit(new LexicalScopeAnalysis(cp.getScope()).analyze(cp.parseFullExpr(null, "let a = 1; a")));
        String first = printer.getOutput();
        assertTrue(first.contains("a => "), "第一次 visit 输出标识符，实际: " + first);

        printer.visit(new LexicalScopeAnalysis(cp.getScope()).analyze(cp.parseFullExpr(null, "let b = 2; b")));
        String second = printer.getOutput();
        assertTrue(second.length() > first.length(), "第二次 visit 的输出应追加到缓冲");
        assertTrue(second.startsWith(first));
    }

    /**
     * Program/块语句输出花括号结构。
     */
    @Test
    public void testBlockStructure() {
        String output = print("let a = 1; a");
        assertTrue(output.contains("{\n"), "program 顶层必须输出 {，实际: " + output);
        assertTrue(output.trim().endsWith("}"), "program 结束必须输出 }");
    }

    /**
     * 闭包变量：lambda 引用外部变量时输出 "&varName,slot=N" 引用行。
     */
    @Test
    public void testClosureVarReference() {
        String output = print("let outer = 1; () => outer + 1");
        assertTrue(output.contains("lambda"), "箭头函数输出 lambda 结构，实际: " + output);
        assertTrue(output.contains("&outer"), "闭包引用的外部变量必须以 & 前缀输出，实际: " + output);
    }

    /**
     * 具名函数声明输出 function name(){...} 结构。
     */
    @Test
    public void testFunctionDeclarationStructure() {
        String output = print("function f(a){ return a + 1; } f(2)");
        assertTrue(output.contains("function f()"), "具名函数输出 function 名结构，实际: " + output);
    }

    /**
     * 未注册的标识符（允许 scopeVar）打印 resolvedDefinition，不抛异常、不产生 "null =>"。
     */
    @Test
    public void testUnresolvedIdentifierDoesNotThrow() {
        String output = print("someVar + 1");
        assertTrue(output.contains("someVar"), "未解析标识符也要输出名字，实际: " + output);
        assertEquals(-1, output.indexOf(" => null"), "不允许打印出 ' => null' 形式的坏输出");
    }

    /**
     * 裸 Identifier 打印格式："name => " 前缀，无声明时回退 resolvedDefinition。
     */
    @Test
    public void testIdentifierPrintFormat() {
        Identifier id = Identifier.valueOf(null, "plain");
        PrintResolvedIdentifier printer = new PrintResolvedIdentifier();
        printer.visit(id);
        assertTrue(printer.getOutput().contains("plain => "), "标识符打印必须包含 'name => ' 格式");
    }
}
