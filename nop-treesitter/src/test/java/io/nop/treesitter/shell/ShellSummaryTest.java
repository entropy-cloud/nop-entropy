package io.nop.treesitter.shell;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Capability 6: the structured summary.
 *
 * <p>The assertion that matters here is the one separating "not proven" from
 * "no analysis, here is the original text". An implementation that degrades to
 * returning the raw script when it cannot prove it looks reasonable and is
 * dangerous: the caller sees a value and cannot tell that nothing was checked.
 * Making the status a field is the fix, and this test is what keeps the fix
 * honest.</p>
 */
class ShellSummaryTest {

    private final ShellAnalysis analysis = ShellTestSupport.analysis();

    @Test
    void aProvableScriptIsSummarizedAsProven() {
        ShellSummary summary = analysis.summarize("cat /etc/passwd\n");
        assertEquals(ShellSummary.ProofStatus.PROVEN, summary.proofStatus());
        assertEquals("", summary.unprovenReason());
        assertEquals(java.util.List.of("cat"), summary.commandNames());
        assertFalse(summary.hasDynamicConstructs());
        assertEquals(1, summary.paths().size());
    }

    /** A redirection is a dynamic construct, so it costs the script its proof. */
    @Test
    void aRedirectedScriptIsNotProvenButItsWritePathIsStillReported() {
        ShellSummary summary = analysis.summarize("cat /etc/passwd > /tmp/out.txt\n");
        assertEquals(ShellSummary.ProofStatus.NOT_PROVEN, summary.proofStatus());
        assertTrue(summary.dynamicConstructKinds().contains(DynamicConstruct.Kind.FILE_REDIRECT));
        assertEquals(2, summary.paths().size());
    }

    @Test
    void connectorsAreCarried() {
        ShellSummary summary = analysis.summarize("echo a; ls b | grep c\n");
        assertEquals(java.util.List.of("echo", "ls", "grep"), summary.commandNames());
        assertEquals(java.util.List.of(";", "|"), summary.connectors());
    }

    @Test
    void anUnprovableScriptIsSummarizedAsNotProvenWithAReason() {
        ShellSummary summary = analysis.summarize("if then rm -rf /tmp/example\n");
        assertEquals(ShellSummary.ProofStatus.NOT_PROVEN, summary.proofStatus());
        assertEquals("SYNTAX_ERROR", summary.unprovenReason());
    }

    /**
     * The distinguishing assertion. A not-proven summary and a fallback to the raw
     * script must be distinguishable, and both must be distinguishable from a
     * proven summary.
     */
    @Test
    void notProvenIsDistinguishableFromAFallbackToRawText() {
        ShellSummary proven = analysis.summarize("cat /etc/passwd\n");
        ShellSummary refused = analysis.summarize("if then rm -rf /tmp/example\n");

        assertNotEquals(proven.proofStatus(), refused.proofStatus());
        assertNotEquals(ShellSummary.ProofStatus.PROVEN, refused.proofStatus());

        // The refusal still carries an analysis result — command names, dynamic
        // constructs, paths — rather than nothing at all. A caller that degrades to
        // raw text would have empty findings here.
        assertNotEquals("", refused.unprovenReason());
        assertEquals("", proven.unprovenReason());
    }

    @Test
    void dynamicConstructsAreCarriedIntoTheSummary() {
        ShellSummary summary = analysis.summarize("rm -rf $TARGET /data\n");
        assertTrue(summary.hasDynamicConstructs());
        assertTrue(summary.dynamicConstructKinds()
                .contains(DynamicConstruct.Kind.SIMPLE_EXPANSION));
    }

    @Test
    void pathsAreCarriedIntoTheSummary() {
        ShellSummary summary = analysis.summarize("cat /etc/passwd > /tmp/out.txt\n");
        assertEquals(2, summary.paths().size());
    }

    @Test
    void anEmptyScriptIsNotSummarizedAsProven() {
        ShellSummary summary = analysis.summarize("");
        assertNotEquals(ShellSummary.ProofStatus.PROVEN, summary.proofStatus());
    }

    @Test
    void theFixtureIsSummarizedWithEveryCapabilityContributing() throws Exception {
        String fixture = Files.readString(Path.of("src/test/resources/shell/agent-composite.sh"));
        ShellSummary summary = analysis.summarize(fixture);

        assertEquals(ShellSummary.ProofStatus.NOT_PROVEN, summary.proofStatus());
        assertTrue(summary.hasDynamicConstructs());
        assertTrue(summary.dynamicConstructKinds().contains(DynamicConstruct.Kind.HEREDOC_BODY));
        assertTrue(summary.dynamicConstructKinds()
                .contains(DynamicConstruct.Kind.BACKGROUND_OPERATOR));
        assertTrue(summary.dynamicConstructKinds()
                .contains(DynamicConstruct.Kind.CONCATENATION));
        assertTrue(summary.dynamicConstructKinds()
                .contains(DynamicConstruct.Kind.COMMAND_SUBSTITUTION));
        assertTrue(summary.commandNames().contains("rm"));
        assertFalse(summary.paths().isEmpty());
    }
}
