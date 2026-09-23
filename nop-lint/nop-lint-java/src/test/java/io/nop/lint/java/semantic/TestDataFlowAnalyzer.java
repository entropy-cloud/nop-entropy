package io.nop.lint.java.semantic;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The DefUseChain + ConstantPropagation matrix (roadmap item 30 Phase 1,
 * plan 2026-09-24-0600-1): used/unused/self-assignment/parameter/argument
 * collection, nested-block and shadowing scope isolation, field access
 * exclusion, the six constant forms, reassignment invalidation, and the
 * unbound name fallback.
 */
public class TestDataFlowAnalyzer {

    private final DataFlowAnalyzer analyzer = new DataFlowAnalyzer();

    private MethodDeclaration parseMethod(String methodSource) {
        String source = "class T {\n" + methodSource + "\n}\n";
        CompilationUnit cu = JavaTypeResolver.parseUnit(source.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return cu.findFirst(MethodDeclaration.class).orElseThrow();
    }

    // ==================== DefUseChain ====================

    @Test
    public void usedVariableHasUseSites() {
        DefUseChain chain = analyzer.buildDefUseChain(parseMethod(
                "void m() { int used = 1; int skip = used + 1; }"));
        DefUseChain.Record used = chain.recordOf("used").orElseThrow();
        assertTrue(used.isUsed(), "used+1 reads the variable");
        DefUseChain.Record unused = chain.recordOf("skip").orElseThrow();
        assertFalse(unused.isUsed(), "skip is declared but never read");
    }

    @Test
    public void selfAssignmentDetected() {
        DefUseChain chain = analyzer.buildDefUseChain(parseMethod(
                "void m() { int x = 1; x = x; }"));
        DefUseChain.Record record = chain.recordOf("x").orElseThrow();
        assertTrue(record.isSelfAssigned(), "x = x is the identity self-assignment form");
    }

    @Test
    public void nonSelfAssignmentNotFlagged() {
        DefUseChain chain = analyzer.buildDefUseChain(parseMethod(
                "void m() { int x = 1; x = 2; }"));
        DefUseChain.Record record = chain.recordOf("x").orElseThrow();
        assertFalse(record.isSelfAssigned(), "x = 2 is a real reassignment");
    }

    @Test
    public void parameterIsADefinitionSite() {
        DefUseChain chain = analyzer.buildDefUseChain(parseMethod(
                "void m(int param) { int x = param + 1; }"));
        DefUseChain.Record param = chain.recordOf("param").orElseThrow();
        assertTrue(param.isUsed(), "the parameter is read by x = param + 1");
        assertFalse(param.definitionSites().isEmpty(), "the parameter is a definition site");
    }

    @Test
    public void methodArgumentIsAUseSite() {
        DefUseChain chain = analyzer.buildDefUseChain(parseMethod(
                "void m() { int arg = 1; helper(arg); }\nvoid helper(int v) { }"));
        DefUseChain.Record record = chain.recordOf("arg").orElseThrow();
        assertTrue(record.isUsed(), "the variable is used as a method argument");
    }

    @Test
    public void fieldAccessDoesNotShadowLocal() {
        DefUseChain chain = analyzer.buildDefUseChain(parseMethod(
                "void m() { int count = 1; this.count = count + 1; }"));
        // the local `count` IS read by `count + 1` on the RHS
        DefUseChain.Record record = chain.recordOf("count").orElseThrow();
        assertTrue(record.isUsed(), "the RHS count + 1 reads the local");
    }

    @Test
    public void nestedBlockUsageIsCollected() {
        DefUseChain chain = analyzer.buildDefUseChain(parseMethod(
                "void m(boolean flag) { int x = 1; if (flag) { int y = x + 1; } }"));
        DefUseChain.Record record = chain.recordOf("x").orElseThrow();
        assertTrue(record.isUsed(), "the use inside the if body is collected");
    }

    @Test
    public void shadowedDeclarationsBuildIndependentChains() {
        DefUseChain chain = analyzer.buildDefUseChain(parseMethod(
                "void m() { int x = 1; { int x = 2; } }"));
        List<DefUseChain.Record> xs = chain.records().stream()
                .filter(r -> r.variableName().equals("x")).toList();
        assertEquals(2, xs.size(), "the two x declarations build independent chains");
        assertFalse(xs.get(0).isUsed(), "the outer x (before the inner declaration) is unused");
        assertFalse(xs.get(1).isUsed(), "the inner x (after its declaration) is unused");
    }

    // ==================== ConstantPropagation ====================

    @Test
    public void stringConstantPropagates() {
        ConstantPropagation constants = analyzer.propagateConstants(parseMethod(
                "void m() { String msg = \"hello\"; }"));
        ConstantPropagation.ConstantResult result = constants.constantValueOf("msg");
        assertTrue(result instanceof ConstantPropagation.Constant, "String literal is constant");
        assertEquals("hello", ((ConstantPropagation.Constant) result).value());
    }

    @Test
    public void intConstantPropagates() {
        ConstantPropagation constants = analyzer.propagateConstants(parseMethod(
                "void m() { int count = 42; }"));
        ConstantPropagation.ConstantResult result = constants.constantValueOf("count");
        assertTrue(result instanceof ConstantPropagation.Constant);
        assertEquals("42", ((ConstantPropagation.Constant) result).value());
    }

    @Test
    public void stringConcatIsConstant() {
        ConstantPropagation constants = analyzer.propagateConstants(parseMethod(
                "void m() { String msg = \"a\" + \"b\"; }"));
        ConstantPropagation.ConstantResult result = constants.constantValueOf("msg");
        assertTrue(result instanceof ConstantPropagation.Constant, "compile-time string concat");
    }

    @Test
    public void booleanConstantPropagates() {
        ConstantPropagation constants = analyzer.propagateConstants(parseMethod(
                "void m() { boolean flag = true; }"));
        ConstantPropagation.ConstantResult result = constants.constantValueOf("flag");
        assertTrue(result instanceof ConstantPropagation.Constant);
        assertEquals("true", ((ConstantPropagation.Constant) result).value());
    }

    @Test
    public void charConstantPropagates() {
        ConstantPropagation constants = analyzer.propagateConstants(parseMethod(
                "void m() { char c = 'x'; }"));
        ConstantPropagation.ConstantResult result = constants.constantValueOf("c");
        assertTrue(result instanceof ConstantPropagation.Constant);
    }

    @Test
    public void longConstantPropagates() {
        ConstantPropagation constants = analyzer.propagateConstants(parseMethod(
                "void m() { long size = 42L; }"));
        ConstantPropagation.ConstantResult result = constants.constantValueOf("size");
        assertTrue(result instanceof ConstantPropagation.Constant);
    }

    @Test
    public void nullConstantPropagates() {
        ConstantPropagation constants = analyzer.propagateConstants(parseMethod(
                "void m() { String s = null; }"));
        ConstantPropagation.ConstantResult result = constants.constantValueOf("s");
        assertTrue(result instanceof ConstantPropagation.Constant);
    }

    @Test
    public void reassignmentInvalidatesTheConstant() {
        // c++ is a UnaryExpr increment (NOT an AssignExpr) — the F1 blocker:
        // without counting it, c would incorrectly remain constant "1"
        ConstantPropagation constants = analyzer.propagateConstants(parseMethod(
                "void m() { int c = 1; c++; use(c); }\nvoid use(int v) { }"));
        ConstantPropagation.ConstantResult result = constants.constantValueOf("c");
        assertTrue(result instanceof ConstantPropagation.NotConstant,
                "the increment invalidates the constant (F1 blocker regression)");
    }

    @Test
    public void methodCallInitializerIsNotConstant() {
        ConstantPropagation constants = analyzer.propagateConstants(parseMethod(
                "void m() { int x = compute(); }\nint compute() { return 1; }"));
        ConstantPropagation.ConstantResult result = constants.constantValueOf("x");
        assertTrue(result instanceof ConstantPropagation.NotConstant,
                "a method call is not a compile-time constant");
    }

    @Test
    public void unknownVariableReturnsUnknown() {
        ConstantPropagation constants = analyzer.propagateConstants(parseMethod(
                "void m() { int x = 1; }"));
        ConstantPropagation.ConstantResult result = constants.constantValueOf("nonexistent");
        assertTrue(result instanceof ConstantPropagation.Unknown,
                "an undeclared name is Unknown, not NotConstant (the three-state distinction)");
    }
}
