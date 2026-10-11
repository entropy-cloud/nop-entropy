package io.nop.lint.java.semantic;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hand-computed anchors for the scope kernel (roadmap item 33 Phase 1,
 * plan Decisions 5/6): fields order-independently visible across the class
 * body, locals/params from their declaration point, the switch block as ONE
 * scope, lambda/catch/for-init shadowing fields but never an enclosing
 * local (the compile-error form is still judged), and no-definition as a
 * legitimate null. Query positions are computed from the fixture text via
 * {@link #pos} — never hand-counted (text-block indentation drifts).
 */
public class TestScopeAnalyzer {

    private final ScopeAnalyzer analyzer = new ScopeAnalyzer();
    private final JavaParser parser = new JavaParser(
            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17));

    private CompilationUnit unit(String source) {
        return parser.parse(source).getResult().orElseThrow();
    }

    /**
     * The 1-based (line, column) of the nth (0-based) occurrence of
     * {@code token} in the fixture text.
     */
    static int[] pos(String source, String token, int occurrence) {
        int index = -1;
        for (int i = 0; i <= occurrence; i++) {
            index = source.indexOf(token, index + 1);
            if (index < 0) {
                throw new IllegalStateException("token not found: " + token + "#" + occurrence);
            }
        }
        int line = 1;
        for (int i = 0; i < index; i++) {
            if (source.charAt(i) == '\n') {
                line++;
            }
        }
        int column = index - source.lastIndexOf('\n', index);
        return new int[]{line, column};
    }

    @Test
    public void localResolvesToItsDeclarationAndKindIsBlock() {
        String source = """
                class Demo {
                    void m() {
                        int total = 1;
                        int doubled = total + total;
                    }
                }
                """;
        CompilationUnit u = unit(source);
        int[] ref = pos(source, "total + total", 0);

        ScopeAnalyzer.Definition def = analyzer.definitionOf(u, ref[0], ref[1]);
        assertEquals("total", def.name());
        assertEquals(3, def.line());

        assertEquals("block", analyzer.scopeKind(u, ref[0], ref[1]));
        assertEquals(List.of("total", "doubled"), analyzer.declaredNames(u, ref[0], ref[1]));
    }

    @Test
    public void useBeforeDeclarationIsNotVisible() {
        String source = """
                class Demo {
                    void m() {
                        int doubled = secret + 1;
                        int secret = 2;
                    }
                }
                """;
        int[] ref = pos(source, "secret + 1", 0);

        assertNull(analyzer.definitionOf(unit(source), ref[0], ref[1]),
                "a local is visible from its declaration point on (plan Decision 5)");
    }

    @Test
    public void fieldIsVisibleAcrossTheClassBodyRegardlessOfOrder() {
        String source = """
                class Demo {
                    void m() {
                        int local = fieldLater + 1;
                    }

                    private int fieldLater = 5;

                    void n() {
                        int other = fieldEarly;
                    }

                    private int fieldEarly = 6;
                }
                """;
        CompilationUnit u = unit(source);
        int[] useLater = pos(source, "fieldLater + 1", 0);
        int[] useEarly = pos(source, "fieldEarly", 0);

        ScopeAnalyzer.Definition later = analyzer.definitionOf(u, useLater[0], useLater[1]);
        assertEquals("fieldLater", later.name());
        assertEquals(6, later.line(), "the field declared AFTER the use resolves (order-free)");
        int[] fieldDecl = pos(source, "private int fieldLater", 0);
        assertEquals("class", analyzer.scopeKind(u, fieldDecl[0], fieldDecl[1]));

        ScopeAnalyzer.Definition early = analyzer.definitionOf(u, useEarly[0], useEarly[1]);
        assertEquals("fieldEarly", early.name());
        assertEquals(12, early.line());
    }

    @Test
    public void parameterResolvesAndMethodScopeSeesParameters() {
        String source = """
                class Demo {
                    int m(int amount) {
                        return amount + 1;
                    }
                }
                """;
        CompilationUnit u = unit(source);
        int[] ref = pos(source, "amount + 1", 0);
        int[] param = pos(source, "int amount", 0);

        ScopeAnalyzer.Definition def = analyzer.definitionOf(u, ref[0], ref[1]);
        assertEquals("amount", def.name());
        assertEquals(2, def.line());
        assertEquals(List.of("amount"), analyzer.declaredNames(u, param[0], param[1]),
                "the method scope declares its parameters");
    }

    @Test
    public void switchBlockIsOneScope() {
        String source = """
                class Demo {
                    void m(int a) {
                        switch (a) {
                            case 1:
                                int first = 1;
                                break;
                            case 2:
                                int second = first + 1;
                                break;
                        }
                    }
                }
                """;
        CompilationUnit u = unit(source);
        int[] ref = pos(source, "first + 1", 0);
        int[] decl = pos(source, "int first", 0);

        assertEquals("switch", analyzer.scopeKind(u, ref[0], ref[1]));
        ScopeAnalyzer.Definition def = analyzer.definitionOf(u, ref[0], ref[1]);
        assertEquals("first", def.name());
        assertEquals(5, def.line(), "case 2 sees case 1's declaration (single switch scope)");

        List<String> names = analyzer.declaredNames(u, decl[0], decl[1]);
        assertTrue(names.contains("first") && names.contains("second"),
                "the switch scope declares both cases' variables: " + names);
    }

    @Test
    public void innerBlockShadowsJudgmentOnCompileErrorFormStillJudges() {
        String source = """
                class Demo {
                    void m() {
                        int x = 1;
                        if (x > 0) {
                            int x = 2;
                        }
                    }
                }
                """;
        int[] decl = pos(source, "x = 2", 0);

        assertTrue(analyzer.shadows(unit(source), decl[0], decl[1]),
                "nested local-local same-name is a compile error form, still judged as shadowing");
    }

    @Test
    public void sameScopeDuplicateIsNotShadowing() {
        String source = """
                class Demo {
                    int same = 1;
                    int same = 2;
                }
                """;
        int[] second = pos(source, "int same = 2", 0);

        assertFalse(analyzer.shadows(unit(source), second[0], second[1]),
                "a same-scope duplicate declaration is not shadowing");
    }

    @Test
    public void lambdaAndCatchParametersShadowFieldsNotOuterLocals() {
        String source = """
                class Demo {
                    private int value = 1;

                    void m(int outer) {
                        java.util.function.IntUnaryOperator f = v -> value + outer + v;
                        try {
                            int r = 1;
                        } catch (IllegalStateException value) {
                            int inner = value.ordinal();
                        }
                        for (int outer2 = 0; outer2 < 3; outer2++) {
                            int use = outer2;
                        }
                    }
                }
                """;
        CompilationUnit u = unit(source);
        int[] fieldRef = pos(source, "value + outer", 0);
        int[] catchParam = pos(source, "value) {", 0);

        // `value` inside the lambda resolves to the field (order-free)
        ScopeAnalyzer.Definition fieldDef = analyzer.definitionOf(u, fieldRef[0], fieldRef[1]);
        assertEquals("value", fieldDef.name());
        assertEquals(2, fieldDef.line());

        // the catch parameter `value` shadows the field
        assertTrue(analyzer.shadows(u, catchParam[0], catchParam[1]),
                "a catch parameter may shadow a field");

        // the lambda's outer reference: the method parameter stays visible
        String errorForm = """
                class Demo {
                    void m(int outer) {
                        java.util.function.IntUnaryOperator f = v -> outer + v;
                    }
                }
                """;
        CompilationUnit errorUnit = unit(errorForm);
        int[] outerRef = pos(errorForm, "outer + v", 0);

        ScopeAnalyzer.Definition paramRef = analyzer.definitionOf(errorUnit, outerRef[0], outerRef[1]);
        assertEquals("outer", paramRef.name(), "the method parameter stays visible in the lambda");
        assertEquals(2, paramRef.line());
    }

    @Test
    public void crossClassAndExternalNamesAreLegitimateNulls() {
        String source = """
                class Demo {
                    void m() {
                        int total = java.lang.Math.max(1, unknownName);
                    }
                }
                """;
        int[] maxRef = pos(source, "Math.max", 0);
        int[] unknownRef = pos(source, "unknownName", 0);

        assertNull(analyzer.definitionOf(unit(source), maxRef[0], maxRef[1]),
                "an external type's member is not resolvable in the single unit");
        assertNull(analyzer.definitionOf(unit(source), unknownRef[0], unknownRef[1]),
                "an undeclared name is a legitimate null, not a failure");
    }

    @Test
    public void catchParameterAndForInitAreTheirOwnScopes() {
        String source = """
                class Demo {
                    void m() {
                        try {
                            int r = 1;
                        } catch (IllegalStateException e) {
                            int use = e.ordinal();
                        }
                        for (int i = 0; i < 3; i++) {
                            int use2 = i;
                        }
                    }
                }
                """;
        CompilationUnit u = unit(source);
        int[] catchParam = pos(source, "IllegalStateException e", 0);
        int[] eRef = pos(source, "e.ordinal", 0);
        int[] forInit = pos(source, "int i = 0", 0);
        int[] iRef = pos(source, "i;", 0);

        assertEquals("catch", analyzer.scopeKind(u, catchParam[0], catchParam[1]));
        ScopeAnalyzer.Definition catchDef = analyzer.definitionOf(u, eRef[0], eRef[1]);
        assertEquals("e", catchDef.name());
        assertEquals(5, catchDef.line());

        assertEquals("for", analyzer.scopeKind(u, forInit[0], forInit[1]));
        ScopeAnalyzer.Definition forDef = analyzer.definitionOf(u, iRef[0], iRef[1]);
        assertEquals("i", forDef.name());
        assertEquals(8, forDef.line());
    }
}
