package io.nop.lint.java;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.node.SourceRange;
import io.nop.lint.core.suppress.SuppressionProvider;
import io.nop.lint.core.suppress.SuppressionSpan;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code @SuppressWarnings} extraction matrix (design 09 §3): every
 * value form × every declaration type × overlap/non-overlap against
 * simulated diagnostic ranges, plus the fail-closed malformed-value paths
 * and the ignored-value surface (no fabricated suppression).
 */
class TestJavaSuppressWarnings {

    private static final String R1 = "demo/rule-one";
    private static final String R2 = "demo/rule-two";

    private static final SuppressionProvider PROVIDER = JavaSuppressWarningsProvider.get();

    // ==================== helpers ====================

    private static List<SuppressionSpan> extract(String source) {
        LintTree tree = JavaLanguage.get().parse(source);
        return PROVIDER.extractSpans(tree);
    }

    private static SourceRange rangeOf(String source, String snippet) {
        byte[] utf8 = source.getBytes(StandardCharsets.UTF_8);
        byte[] needle = snippet.getBytes(StandardCharsets.UTF_8);
        int index = indexOf(utf8, needle);
        if (index < 0) {
            throw new IllegalArgumentException("snippet not in source: " + snippet);
        }
        return new SourceRange(index, index + needle.length);
    }

    private static int indexOf(byte[] source, byte[] needle) {
        outer:
        for (int i = 0; i <= source.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (source[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static SuppressionSpan singleSpan(String source, String ruleId) {
        List<SuppressionSpan> spans = extract(source);
        assertEquals(1, spans.size(), "expected exactly one suppression span");
        assertEquals(ruleId.isEmpty() ? List.of() : List.of(ruleId),
                List.copyOf(spans.get(0).ruleIds()));
        return spans.get(0);
    }

    // ==================== value forms ====================

    @Test
    void nopLintPrefixedValueIsTheRecommendedForm() {
        String source = "@SuppressWarnings(\"nop-lint:demo/rule-one\")\nclass A {}\n";
        singleSpan(source, R1);
    }

    @Test
    void bareRuleIdValueIsRecognizedForCompatibility() {
        String source = "@SuppressWarnings(\"demo/rule-one\")\nclass A {}\n";
        singleSpan(source, R1);
    }

    @Test
    void allValueSuppressesEveryRule() {
        String source = "@SuppressWarnings(\"all\")\nclass A {}\n";
        SuppressionSpan span = singleSpan(source, "");
        assertTrue(span.covers(R1) && span.covers(R2), "the all form covers any rule id");
    }

    @Test
    void qualifiedAnnotationNameIsRecognized() {
        String source = "@java.lang.SuppressWarnings(\"nop-lint:demo/rule-one\")\nclass A {}\n";
        singleSpan(source, R1);
    }

    @Test
    void arrayArgumentContributesOneSpanPerValue() {
        String source = """
                @SuppressWarnings({"nop-lint:demo/rule-one", "nop-lint:demo/rule-two"})
                class A {}
                """;
        List<SuppressionSpan> spans = extract(source);
        assertEquals(2, spans.size(), "one span per string value");
        assertTrue(spans.get(0).covers(R1));
        assertTrue(spans.get(1).covers(R2));
    }

    @Test
    void namedValueFormIsRecognized() {
        String source = "@SuppressWarnings(value = \"nop-lint:demo/rule-one\")\nclass A {}\n";
        singleSpan(source, R1);
    }

    @Test
    void markerAnnotationHasNoValueAndProducesNoSpan() {
        String source = "@SuppressWarnings\nclass A {}\n";
        assertTrue(extract(source).isEmpty(),
                "a marker (valueless) form carries nothing to recognize");
    }

    @Test
    void otherToolsValuesProduceNoFabricatedSuppression() {
        // "unchecked" and PMD aliases become bare-rule-id spans per design 09 §3
        // compatibility — they can only ever match an actual rule id, never fabricate.
        String source = "@SuppressWarnings({\"unchecked\", \"PMD.AvoidUsingVolatile\"})\nclass A {}\n";
        List<SuppressionSpan> spans = extract(source);
        assertEquals(2, spans.size());
        assertFalse(spans.get(0).covers(R1) || spans.get(0).covers(R2),
                "a foreign value matches only its own literal id");
        assertEquals("unchecked", spans.get(0).ruleIds().iterator().next());
    }

    // ==================== declaration types ====================

    @Test
    void classDeclarationScope() {
        String source = "@SuppressWarnings(\"nop-lint:demo/rule-one\")\nclass A {\n    int x;\n}\n";
        SuppressionSpan span = singleSpan(source, R1);
        // the tree-sitter declaration node spans from its leading modifiers
        // (the annotation) through the closing brace
        assertEquals(rangeOf(source, "@SuppressWarnings(\"nop-lint:demo/rule-one\")\nclass A {\n    int x;\n}"),
                span.range());
    }

    @Test
    void methodDeclarationScope() {
        String source = """
                class A {
                    @SuppressWarnings("nop-lint:demo/rule-one")
                    public void run() {
                        System.out.println("x");
                    }
                }
                """;
        SuppressionSpan span = singleSpan(source, R1);
        assertEquals(rangeOf(source, "@SuppressWarnings(\"nop-lint:demo/rule-one\")\n"
                + "    public void run() {\n        System.out.println(\"x\");\n    }"), span.range());
    }

    @Test
    void fieldDeclarationScope() {
        String source = """
                class A {
                    @SuppressWarnings("nop-lint:demo/rule-one")
                    private String field = "v";
                }
                """;
        SuppressionSpan span = singleSpan(source, R1);
        assertEquals(rangeOf(source, "@SuppressWarnings(\"nop-lint:demo/rule-one\")\n"
                + "    private String field = \"v\";"), span.range());
    }

    @Test
    void parameterDeclarationScope() {
        String source = """
                class A {
                    void run(@SuppressWarnings("nop-lint:demo/rule-one") int a) {
                    }
                }
                """;
        SuppressionSpan span = singleSpan(source, R1);
        assertEquals(rangeOf(source, "@SuppressWarnings(\"nop-lint:demo/rule-one\") int a"), span.range());
    }

    @Test
    void spanCoversDiagnosticsInsideButNotOutsideTheDeclaration() {
        String source = """
                class A {
                    @SuppressWarnings("nop-lint:demo/rule-one")
                    void inside() {
                        System.out.println("hit");
                    }

                    void outside() {
                        System.out.println("miss");
                    }
                }
                """;
        SuppressionSpan span = singleSpan(source, R1);
        SourceRange hit = rangeOf(source, "System.out.println(\"hit\")");
        SourceRange miss = rangeOf(source, "System.out.println(\"miss\")");
        assertTrue(span.covers(R1) && span.overlaps(hit), "inside the method: suppressed");
        assertFalse(span.overlaps(miss), "outside the method: reported");
    }

    @Test
    void multiLineDiagnosticOverlappingTheDeclarationIsSuppressed() {
        String source = """
                class A {
                    @SuppressWarnings("demo/rule-one")
                    void m() {
                        foo.invoke(
                            "x");
                    }
                }
                """;
        SuppressionSpan span = singleSpan(source, R1);
        SourceRange multiLine = new SourceRange(rangeOf(source, "foo.invoke(").startByte(),
                rangeOf(source, "\"x\")").endByte());
        assertTrue(span.overlaps(multiLine), "overlap semantics match design 09 §2.2");
    }

    // ==================== fail-closed paths ====================

    @Test
    void blankNopLintRuleIdFailsClosed() {
        NopLintException e = assertThrows(NopLintException.class,
                () -> extract("@SuppressWarnings(\"nop-lint:\")\nclass A {}\n"));
        assertTrue(e.getMessage().contains("nop-lint prefix but not a parseable rule id"));
    }

    @Test
    void malformedNopLintRuleIdFailsClosed() {
        NopLintException e = assertThrows(NopLintException.class,
                () -> extract("@SuppressWarnings(\"nop-lint:a b\")\nclass A {}\n"));
        assertTrue(e.getMessage().contains("nop-lint prefix but not a parseable rule id"));
    }

    @Test
    void foreignValuesNeverThrow() {
        // values that do not claim nop-lint semantics are compatibility bare ids
        List<SuppressionSpan> spans = extract("@SuppressWarnings(\"a b c\")\nclass A {}\n");
        assertEquals(1, spans.size(), "recognized as a bare (never-matching) rule id, not an error");
    }

    // ==================== binding wiring ====================

    @Test
    void javaLanguageExposesTheProvider() {
        assertTrue(JavaLanguage.get().suppressionProvider() instanceof JavaSuppressWarningsProvider,
                "the binding carries its annotation provider through the LintLanguage hook");
    }

    @Test
    void nonSuppressWarningsAnnotationsAreIgnored() {
        String source = """
                @Deprecated
                class A {
                    @Override
                    public String toString() {
                        return "x";
                    }
                }
                """;
        assertTrue(extract(source).isEmpty());
    }
}
