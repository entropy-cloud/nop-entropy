package io.nop.lint.core.testing;

import io.nop.lint.core.NopLintException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused proofs for the {@code *.expect} fixture format (design 03 §4.2,
 * v1 subset): legal parsing, the 1-based line defaults, and every fail-closed
 * rejection path — missing/empty diagnostics list, unknown fields, illegal
 * line numbers, and the {@code fix} expectation field (roadmap item 25).
 */
public class TestExpectParser {

    private static final String PATH = "/test/lint/broken-suites/demo/x/x.expect";

    private final ExpectParser parser = new ExpectParser();

    private NopLintException rejected(String yaml) {
        return assertThrows(NopLintException.class, () -> parser.parse(PATH, yaml),
                "expected fail-closed rejection for: " + yaml);
    }

    @Test
    public void legalExpectationWithAllFieldsParses() {
        ExpectModel model = parser.parse(PATH,
                "diagnostics:\n"
                        + "  - line: 3\n"
                        + "    endLine: 5\n"
                        + "    ruleId: demo/no-console\n"
                        + "    messageContains: \"System.out\"\n");

        assertEquals(1, model.diagnostics().size());
        ExpectDiagnostic diagnostic = model.diagnostics().get(0);
        assertEquals(3, diagnostic.line());
        assertEquals(5, diagnostic.endLine());
        assertEquals("demo/no-console", diagnostic.ruleId());
        assertEquals("System.out", diagnostic.messageContains());
        assertEquals(PATH, model.resourcePath());
    }

    @Test
    public void endLineDefaultsToLineWhenAbsent() {
        ExpectModel model = parser.parse(PATH,
                "diagnostics:\n"
                        + "  - line: 12\n"
                        + "    ruleId: demo/r\n"
                        + "    messageContains: frag\n");

        assertEquals(12, model.diagnostics().get(0).line());
        assertEquals(12, model.diagnostics().get(0).endLine(),
                "endLine defaults to line for single-line expectations");
    }

    @Test
    public void multipleEntriesKeepDeclarationOrder() {
        ExpectModel model = parser.parse(PATH,
                "diagnostics:\n"
                        + "  - line: 2\n"
                        + "    ruleId: demo/a\n"
                        + "    messageContains: a\n"
                        + "  - line: 7\n"
                        + "    endLine: 9\n"
                        + "    ruleId: demo/b\n"
                        + "    messageContains: b\n");

        assertEquals(2, model.diagnostics().size());
        assertEquals("demo/a", model.diagnostics().get(0).ruleId());
        assertEquals("demo/b", model.diagnostics().get(1).ruleId());
    }

    // ==================== fail-closed: structure ====================

    @Test
    public void missingDiagnosticsListIsRejected() {
        NopLintException ex = rejected("ruleId: demo/no-console\n");
        assertTrue(ex.getMessage().contains(PATH) && ex.getMessage().contains("diagnostics"),
                "message must carry the fixture path and the missing field: " + ex.getMessage());
    }

    @Test
    public void nonListDiagnosticsIsRejected() {
        NopLintException ex = rejected("diagnostics: not-a-list\n");
        assertTrue(ex.getMessage().contains(PATH), ex.getMessage());
    }

    @Test
    public void emptyDiagnosticsListIsRejected() {
        NopLintException ex = rejected("diagnostics: []\n");
        assertTrue(ex.getMessage().contains("empty"), ex.getMessage());
    }

    @Test
    public void emptyFileIsRejected() {
        NopLintException ex = rejected("");
        assertTrue(ex.getMessage().contains(PATH), ex.getMessage());
    }

    @Test
    public void invalidYamlIsRejected() {
        NopLintException ex = rejected("diagnostics: [unclosed\n");
        assertTrue(ex.getMessage().contains(PATH) && ex.getMessage().contains("YAML"), ex.getMessage());
    }

    // ==================== fail-closed: unknown fields ====================

    @Test
    public void unknownTopLevelFieldIsRejected() {
        NopLintException ex = rejected("diagnostics: []\nunexpected: 1\n");
        assertTrue(ex.getMessage().contains("unexpected"), ex.getMessage());
    }

    @Test
    public void unknownEntryFieldIsRejected() {
        NopLintException ex = rejected("diagnostics:\n  - line: 1\n    ruleId: r\n"
                + "    messageContains: m\n    severity: error\n");
        assertTrue(ex.getMessage().contains("severity"), ex.getMessage());
    }

    @Test
    public void fixFieldInEntryIsRejectedPointingToAutofixItem() {
        NopLintException ex = rejected("diagnostics:\n  - line: 1\n    ruleId: r\n"
                + "    messageContains: m\n    fix: \"replacement()\"\n");
        assertTrue(ex.getMessage().contains("fix"), ex.getMessage());
        assertTrue(ex.getMessage().contains("item 25"),
                "rejection must point to the autofix roadmap item: " + ex.getMessage());
    }

    @Test
    public void fixFieldAtTopLevelIsRejected() {
        NopLintException ex = rejected("diagnostics:\n  - line: 1\n    ruleId: r\n"
                + "    messageContains: m\nfix: replacement\n");
        assertTrue(ex.getMessage().contains("item 25"), ex.getMessage());
    }

    // ==================== fail-closed: line numbers ====================

    @Test
    public void missingLineIsRejected() {
        NopLintException ex = rejected("diagnostics:\n  - ruleId: r\n    messageContains: m\n");
        assertTrue(ex.getMessage().contains("line"), ex.getMessage());
    }

    @Test
    public void lineBelowOneIsRejected() {
        NopLintException ex = rejected("diagnostics:\n  - line: 0\n    ruleId: r\n"
                + "    messageContains: m\n");
        assertTrue(ex.getMessage().contains(">= 1"), ex.getMessage());
    }

    @Test
    public void nonIntegerLineIsRejected() {
        NopLintException ex = rejected("diagnostics:\n  - line: 1.5\n    ruleId: r\n"
                + "    messageContains: m\n");
        assertTrue(ex.getMessage().contains("whole-number"), ex.getMessage());
    }

    @Test
    public void nonNumericLineIsRejected() {
        NopLintException ex = rejected("diagnostics:\n  - line: third\n    ruleId: r\n"
                + "    messageContains: m\n");
        assertTrue(ex.getMessage().contains("integer >= 1"), ex.getMessage());
    }

    @Test
    public void endLineBeforeLineIsRejected() {
        NopLintException ex = rejected("diagnostics:\n  - line: 5\n    endLine: 4\n"
                + "    ruleId: r\n    messageContains: m\n");
        assertTrue(ex.getMessage().contains("endLine=4"), ex.getMessage());
    }

    // ==================== fail-closed: entry fields ====================

    @Test
    public void scalarEntryIsRejected() {
        NopLintException ex = rejected("diagnostics:\n  - just-a-string\n");
        assertTrue(ex.getMessage().contains("mapping"), ex.getMessage());
    }

    @Test
    public void blankRuleIdIsRejected() {
        NopLintException ex = rejected("diagnostics:\n  - line: 1\n    ruleId: \"\"\n"
                + "    messageContains: m\n");
        assertTrue(ex.getMessage().contains("ruleId"), ex.getMessage());
    }

    @Test
    public void missingMessageContainsIsRejected() {
        NopLintException ex = rejected("diagnostics:\n  - line: 1\n    ruleId: r\n");
        assertTrue(ex.getMessage().contains("messageContains"), ex.getMessage());
    }
}
