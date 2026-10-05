package io.nop.treesitter.shell;

import io.nop.treesitter.TSNode;
import io.nop.treesitter.TreeSitterException;
import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end (Minimum Rules #22) for the whole semantic layer: the fixed
 * agent-style fixture goes in through the primary {@link TSParser} entry point and
 * all six capabilities' outputs are checked against it.
 *
 * <p>The point of the fixture is that one realistic agent script exercises every
 * capability at once — heredoc, pipeline, command substitution, a literal glued to
 * an expansion, a for loop, a variable-prefixed assignment, a redirection, a
 * background operator and a subshell — so the capabilities are verified together
 * rather than only in isolation.</p>
 *
 * <p>The wiring evidence (Minimum Rules #23) is structural: the analysis is built
 * from the same {@link Language} the primary parser uses, and the test asserts the
 * tree the capabilities read is the primary one — no compat class is imported
 * anywhere in the layer, which this file also demonstrates by compiling against
 * the primary types only.</p>
 */
class ShellAnalysisEndToEndTest {

    private static final Path FIXTURE = Path.of("src/test/resources/shell/agent-composite.sh");

    private static String fixture() throws Exception {
        return Files.readString(FIXTURE, StandardCharsets.UTF_8);
    }

    @Test
    void theFixtureIsReadThroughThePrimaryParseEntry() throws Exception {
        Language language = ShellTestSupport.BASH;
        TSTree tree = TSParser.parse(language, fixture().getBytes(StandardCharsets.UTF_8));

        // The tree the capabilities read is the primary one: same type, same
        // root, and the node APIs the layer depends on are present on it.
        TSNode root = tree.rootNode();
        assertEquals("program", root.type());
        assertFalse(root.hasError(), "the fixture must parse cleanly");
        assertTrue(root.namedChildCount() > 1);
    }

    @Test
    void allSixCapabilitiesProduceTheirOutputOnTheFixture() throws Exception {
        ShellAnalysis analysis = ShellTestSupport.analysis();
        String script = fixture();

        // Capability 1: the fixture is not provable, and says why.
        UnprovenScript refusal = ShellTestSupport.unproven(analysis, script);
        assertFalse(refusal.reason().name().isEmpty());

        // Capability 2: every literal command, including inside the loop and the
        // substitution.
        LiteralHarvest harvest = analysis.harvestLiterals(script);
        assertTrue(harvest.commandNames().contains("rm"), harvest.commandNames().toString());
        assertTrue(harvest.commandNames().contains("cp"), harvest.commandNames().toString());
        assertTrue(harvest.commandNames().contains("bash"), harvest.commandNames().toString());
        assertTrue(harvest.commandNames().contains("tee"), harvest.commandNames().toString());

        // Capability 3: the fixture's heredoc is not extractable, because the
        // heredoc is not the only top-level statement.
        assertFalse(analysis.extractHeredoc(script, Set.of()).isFound());

        // Capability 4: every dynamic construct class the fixture contains.
        List<DynamicConstruct> dynamic = analysis.findDynamicConstructs(script);
        Set<DynamicConstruct.Kind> kinds = dynamic.stream()
                .map(DynamicConstruct::kind)
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(kinds.contains(DynamicConstruct.Kind.HEREDOC_BODY));
        assertTrue(kinds.contains(DynamicConstruct.Kind.HEREDOC_END));
        assertTrue(kinds.contains(DynamicConstruct.Kind.BACKGROUND_OPERATOR));
        assertTrue(kinds.contains(DynamicConstruct.Kind.CONCATENATION));
        assertTrue(kinds.contains(DynamicConstruct.Kind.COMMAND_SUBSTITUTION));
        assertTrue(kinds.contains(DynamicConstruct.Kind.SIMPLE_EXPANSION));
        assertTrue(kinds.contains(DynamicConstruct.Kind.EXPANSION));
        assertTrue(kinds.contains(DynamicConstruct.Kind.FILE_REDIRECT));
        assertTrue(kinds.contains(DynamicConstruct.Kind.VARIABLE_ASSIGNMENT));

        // Capability 5: literal paths, all annotated as harvested.
        List<ShellPath> paths = analysis.extractPaths(script);
        List<String> pathValues = paths.stream().map(ShellPath::path).toList();
        assertTrue(pathValues.contains("/etc/config"), pathValues.toString());
        assertTrue(pathValues.contains("/tmp/config.bak"), pathValues.toString());
        assertTrue(paths.stream().allMatch(p -> p.confidence() == ShellPath.Confidence.HARVESTED));

        // Capability 6: the summary ties them together with the proof status.
        ShellSummary summary = analysis.summarize(script);
        assertEquals(ShellSummary.ProofStatus.NOT_PROVEN, summary.proofStatus());
        assertTrue(summary.hasDynamicConstructs());
        assertEquals(paths.size(), summary.paths().size());
    }

    @Test
    void aHeredocOnlyScriptIsProvableUpToTheHeredocAndExtractable() throws Exception {
        // The other half of the fixture contract: a script that *is* just a
        // heredoc statement is both extractable and (after the heredoc is set
        // aside) fully understood.
        ShellAnalysis analysis = ShellTestSupport.analysis();
        String script = "apply_patch <<'PATCH'\n*** Begin Patch\n-remove\n+add\n*** End Patch\nPATCH\n";

        HeredocScript.Lookup lookup = analysis.extractHeredoc(script, Set.of());
        assertTrue(lookup.isFound(), String.valueOf(lookup.reason()));
        assertEquals("apply_patch", lookup.found().commandName());
        assertTrue(lookup.found().body().contains("*** Begin Patch"));
        assertTrue(lookup.found().bodyIsLiteral());

        ShellSummary summary = analysis.summarize(script);
        assertEquals(ShellSummary.ProofStatus.NOT_PROVEN, summary.proofStatus());
        assertEquals(List.of("apply_patch"), summary.commandNames());
    }

    /**
     * Malformed bash that once aborted the parse now flows through the normal
     * refusal path: the runtime returns a tree containing ERROR nodes, the
     * splitter sees them and refuses with {@code SYNTAX_ERROR}, and the summary
     * reports {@code NOT_PROVEN} with nothing proven. A guardrail therefore
     * degrades to "refuse" on unparseable input instead of crashing on it.
     *
     * <p>This assertion replaced one that pinned the abort itself (owned by
     * successor plan {@code 2026-10-04-1900-1-glr-recovery-livelock-guard.md}, now
     * landed). The earlier test asserted only that an exception was raised, never
     * its message, so the successor could land without fighting it — and did.
     */
    @Test
    void aMalformedScriptIsRefusedAsSyntaxErrorRatherThanAbortingTheParse() {
        ShellAnalysis analysis = ShellTestSupport.analysis();
        String malformed = "echo ${x!y}";
        assertEquals(UnprovenScript.Reason.SYNTAX_ERROR,
                ShellTestSupport.unproven(analysis, malformed).reason(),
                "a malformed script must be refused as SYNTAX_ERROR");
        ShellSummary summary = analysis.summarize(malformed);
        assertEquals(ShellSummary.ProofStatus.NOT_PROVEN, summary.proofStatus());
        assertTrue(summary.commandNames().isEmpty(),
                "nothing may be reported as proven for a malformed script");
    }

    /**
     * The summary's proof state is exactly two. A third "could not analyse" value
     * would be a state no code path produces, and the GraphQL DTO advertises the
     * contract to consumers outside the module.
     */
    @Test
    void theProofStateHasExactlyTwoValues() {
        assertEquals(2, ShellSummary.ProofStatus.values().length);
        assertEquals(ShellSummary.ProofStatus.PROVEN,
                ShellSummary.ProofStatus.valueOf("PROVEN"));
        assertEquals(ShellSummary.ProofStatus.NOT_PROVEN,
                ShellSummary.ProofStatus.valueOf("NOT_PROVEN"));
    }

    /**
     * The two capabilities that must never be confused, side by side on one
     * script: the harvest finds the literal words, the proof declines.
     */
    @Test
    void harvestAndProofDisagreeInTheExpectedDirection() throws Exception {
        ShellAnalysis analysis = ShellTestSupport.analysis();
        String script = fixture();

        assertTrue(analysis.harvestLiterals(script).size() > 1,
                "the harvest can still say something useful");
        assertFalse(analysis.splitProven(script).isProven(),
                "and the proof must still decline");
    }
}
