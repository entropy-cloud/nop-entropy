package io.nop.lint.java.semantic;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import io.nop.lint.core.NopLintException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hand-computed anchors for the three metrics (roadmap item 32 Phase 1,
 * plan 2026-09-24-1000-1 Decisions 3/4): every expectation is derived from
 * the adjudicated increment/product tables in the plan and the class
 * javadoc — the whitepaper's switch-single-increment and else-if flattening
 * rules are pinned by dedicated examples, and the failure shape (null
 * input, NPath saturation) is fail-closed.
 */
public class TestMetricsEvaluator {

    private final MetricsEvaluator evaluator = new MetricsEvaluator();
    private final JavaParser parser = new JavaParser(
            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17));

    private MethodDeclaration method(String methodSource) {
        CompilationUnit unit = parser.parse("class Demo { " + methodSource + " }").getResult().orElseThrow();
        return unit.findAll(MethodDeclaration.class).get(0);
    }

    @Test
    public void emptyMethodIsOneZeroOne() {
        MethodDeclaration m = method("void m() { }");

        assertEquals(1, evaluator.cyclomaticComplexity(m));
        assertEquals(0, evaluator.cognitiveComplexity(m));
        assertEquals(1, evaluator.nPathComplexity(m));
    }

    @Test
    public void cyclomaticCountsEveryDecisionPointOnce() {
        MethodDeclaration m = method("""
                void m(int a, int b) {
                    if (a > 0) { }
                    for (int i = 0; i < 3; i++) { }
                    while (a > b) { }
                    do { } while (a > 0);
                    switch (a) {
                        case 1: break;
                        case 2: break;
                        default: break;
                    }
                    try { } catch (Exception e) { }
                    int x = a > 0 ? 1 : 2;
                    boolean y = a > 0 && b > 0 || a == b;
                }
                """);

        // base 1 + if/for/while/do + 2 case labels + catch + ternary + && + ||
        assertEquals(11, evaluator.cyclomaticComplexity(m));
    }

    @Test
    public void nestedIfAccumulatesNesting() {
        MethodDeclaration m = method("""
                void m(int a) {
                    if (a > 0) {
                        if (a > 5) { }
                    }
                }
                """);

        assertEquals(3, evaluator.cyclomaticComplexity(m), "base 1 + two ifs");
        assertEquals(3, evaluator.cognitiveComplexity(m), "outer if +1, inner if +1+1");
        assertEquals(4, evaluator.nPathComplexity(m));
    }

    @Test
    public void elseIfChainIsFlatIncrements() {
        MethodDeclaration m = method("""
                void m(int a) {
                    if (a == 1) {
                    } else if (a == 2) {
                    } else if (a == 3) {
                    } else {
                    }
                }
                """);

        assertEquals(4, evaluator.cognitiveComplexity(m),
                "each chain link is a flat +1 (the whitepaper's flattening)");
        assertEquals(4, evaluator.cyclomaticComplexity(m), "base 1 + three ifs");
        assertEquals(8, evaluator.nPathComplexity(m), "each if doubles the path count");
    }

    @Test
    public void switchIsOneStructuralIncrementForCognitive() {
        MethodDeclaration m = method("""
                void m(int a) {
                    switch (a) {
                        case 1: break;
                        case 2: break;
                        default: break;
                    }
                }
                """);

        assertEquals(3, evaluator.cyclomaticComplexity(m), "base 1 + two case labels");
        assertEquals(1, evaluator.cognitiveComplexity(m),
                "a switch and all its cases combined = a single +1 (whitepaper B1)");
        assertEquals(3, evaluator.nPathComplexity(m), "two case labels -> x(n+1) = x3");
    }

    @Test
    public void loopNestingMatchesTheWhitepaperShape() {
        MethodDeclaration m = method("""
                void m(int max) {
                    for (int i = 0; i < max; i++) {
                        for (int j = 0; j < i; j++) {
                            if (i % j == 0) {
                            }
                        }
                    }
                }
                """);

        assertEquals(4, evaluator.cyclomaticComplexity(m));
        assertEquals(6, evaluator.cognitiveComplexity(m), "for +1, for +2, if +3");
        assertEquals(8, evaluator.nPathComplexity(m));
    }

    @Test
    public void lambdaRaisesNestingWithoutOwnIncrement() {
        MethodDeclaration m = method("""
                void m(java.util.List<Integer> list, int a) {
                    Runnable r = () -> {
                        if (a > 0) { }
                    };
                }
                """);

        assertEquals(2, evaluator.cognitiveComplexity(m), "lambda +0 but the if inside is +1+1");
        assertEquals(2, evaluator.cyclomaticComplexity(m));
        assertEquals(2, evaluator.nPathComplexity(m));
    }

    @Test
    public void logicalOperatorRunsAreOneIncrementEach() {
        MethodDeclaration m = method("""
                void m(boolean a, boolean b, boolean c) {
                    if (a && b && c) { }
                    if (a || b) { }
                }
                """);

        assertEquals(6, evaluator.cyclomaticComplexity(m),
                "base 1 + 2 ifs + two && nodes + || (each short-circuit node is a decision point)");
        assertEquals(4, evaluator.cognitiveComplexity(m),
                "if +1, one && run +1, if +1, one || run +1 (flat, per Decision 3)");
        assertEquals(32, evaluator.nPathComplexity(m),
                "each short-circuit operator node doubles (2 ifs + 2 && + 1 ||)");
    }

    @Test
    public void catchClausesAndTernaryCountPerTheTables() {
        MethodDeclaration m = method("""
                void m(int a) {
                    try {
                        int x = a > 0 ? 1 : 2;
                    } catch (IllegalStateException e) {
                    } catch (IllegalArgumentException e) {
                    }
                }
                """);

        assertEquals(4, evaluator.cyclomaticComplexity(m), "base 1 + ternary + two catches");
        assertEquals(3, evaluator.cognitiveComplexity(m), "ternary +1, catch +1, catch +1");
        assertEquals(8, evaluator.nPathComplexity(m), "ternary x2, each catch x2 (Decision 4)");
    }

    @Test
    public void nPathSaturatesInsteadOfOverflowing() {
        StringBuilder source = new StringBuilder("void m(int a) { ");
        for (int i = 0; i < 80; i++) {
            source.append("if (a > ").append(i).append(") { ");
        }
        for (int i = 0; i < 80; i++) {
            source.append("} ");
        }
        source.append(" }");

        assertEquals(Long.MAX_VALUE, evaluator.nPathComplexity(method(source.toString())),
                "2^80 paths saturate at Long.MAX_VALUE instead of overflowing");
    }

    @Test
    public void nullMethodFailsClosed() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> evaluator.cyclomaticComplexity(null));
        assertTrue(ex.getMessage().contains("a metrics query requires a method declaration"),
                ex.getMessage());
    }
}
