package io.nop.treesitter.shell;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Capability 2: the literal harvest.
 *
 * <p>The assertions here are about <b>counts</b>, not merely about having found
 * something. A harvester that only walked direct children would still "find the
 * dangerous command" in every one of these inputs while finding one command
 * instead of two — which is the shell of the implementation this guards against.
 * Every adversarial script therefore asserts the exact number of commands
 * recovered.</p>
 */
class ShellLiteralHarvestTest {

    private final ShellAnalysis analysis = ShellTestSupport.analysis();

    @Test
    void plainCommand() {
        LiteralHarvest harvest = analysis.harvestLiterals("echo hi\n");
        assertEquals(1, harvest.size());
        assertEquals("echo", harvest.commands().get(0).name());
        assertEquals(List.of("hi"), harvest.commands().get(0).arguments());
        assertFalse(harvest.commands().get(0).partial());
    }

    @Test
    void descendsIntoPipelines() {
        LiteralHarvest harvest = analysis.harvestLiterals("printf x | rm -rf /tmp/x\n");
        assertEquals(2, harvest.size(), "a pipeline has two commands, not one");
        assertEquals(List.of("printf", "rm"), harvest.commandNames());
    }

    @Test
    void descendsIntoControlFlow() {
        LiteralHarvest harvest =
                analysis.harvestLiterals("if test -d d; then rm --force d; fi\n");
        assertEquals(2, harvest.size(), "the `test` condition and the `rm` body");
        assertEquals(List.of("test", "rm"), harvest.commandNames());
    }

    @Test
    void descendsIntoForLoops() {
        LiteralHarvest harvest =
                analysis.harvestLiterals("for t in a b; do rm -r -f t; done\n");
        assertEquals(1, harvest.size());
        assertEquals("rm", harvest.commands().get(0).name());
    }

    @Test
    void descendsIntoCommandSubstitution() {
        LiteralHarvest harvest = analysis.harvestLiterals("echo \"$(rm -rf /tmp/x)\"\n");
        assertEquals(2, harvest.size(),
                "the command inside the substitution is a command of the script");
        assertEquals(List.of("echo", "rm"), harvest.commandNames());
    }

    @Test
    void singleQuotedCommandIsOneCommandNotTwo() {
        // `bash -c 'rm -rf /tmp/example'` — the quoted script is a raw_string
        // argument, not a nested parse. Reading it as two commands would mean
        // inventing syntax the grammar does not have.
        LiteralHarvest harvest = analysis.harvestLiterals("bash -c 'rm -rf /tmp/example'\n");
        assertEquals(1, harvest.size());
        assertEquals("bash", harvest.commands().get(0).name());
        assertEquals(List.of("-c", "'rm -rf /tmp/example'"), harvest.commands().get(0).arguments());
    }

    @Test
    void trapBodyIsNotParsedAsACommand() {
        LiteralHarvest harvest = analysis.harvestLiterals("trap 'rm -rf /tmp/x' EXIT\n");
        assertEquals(1, harvest.size());
        assertEquals("trap", harvest.commands().get(0).name());
    }

    /**
     * The partial marker. A command with an unreadable argument keeps the literal
     * words it does have and is marked incomplete, instead of being silently
     * shortened into an argv that looks whole.
     */
    @Test
    void anUnreadableArgumentMarksTheCommandPartial() {
        LiteralHarvest harvest = analysis.harvestLiterals("rm -rf $TARGET /data\n");
        assertEquals(1, harvest.size());
        LiteralHarvest.Command rm = harvest.commands().get(0);
        assertEquals("rm", rm.name());
        assertEquals(List.of("-rf", "/data"), rm.arguments(),
                "the literal words are kept, and only those");
        assertTrue(rm.partial(), "the argv is known to be a subset, and must say so");
    }

    @Test
    void aFullyLiteralCommandIsNotMarkedPartial() {
        LiteralHarvest harvest = analysis.harvestLiterals("rm -rf /tmp/x\n");
        assertFalse(harvest.commands().get(0).partial());
    }

    @Test
    void variableAssignmentPrefixIsNotAnArgument() {
        // Counting `FOO=bar` as an argument would shift every later position and
        // mis-attribute the paths that follow.
        LiteralHarvest harvest = analysis.harvestLiterals("FOO=bar cp /etc/a /tmp/b\n");
        assertEquals(1, harvest.size());
        assertEquals(List.of("/etc/a", "/tmp/b"), harvest.commands().get(0).arguments());
        assertFalse(harvest.commands().get(0).partial());
    }

    @Test
    void aTreeWithSyntaxErrorsYieldsNothingRatherThanAPartialList() {
        LiteralHarvest harvest = analysis.harvestLiterals("if then rm -rf /tmp/example\n");
        assertEquals(0, harvest.size(),
                "a tree with errors is not a tree whose commands can be enumerated");
        assertTrue(harvest.isEmpty());
    }

    @Test
    void harvestCarriesItsOwnSafetyMarker() {
        assertTrue(analysis.harvestLiterals("echo hi\n").isNotASafetyProof());
    }

    /** The full adversarial set the mature reference implementation uses. */
    @Test
    void referenceAdversarialSet() {
        record Case(String script, int expectedCommands) {
        }
        List<Case> cases = List.of(
                new Case("printf x | rm -rf /tmp/x\n", 2),
                new Case("if test -d /tmp/example; then rm --force /tmp/example; fi\n", 2),
                new Case("rm -rf \"$TARGET\" >/dev/null\n", 1),
                new Case("for target in /tmp/a /tmp/b; do rm -r -f \"$target\"; done\n", 1),
                new Case("echo \"$(rm -rf /tmp/example)\"\n", 2),
                new Case("bash -c 'rm -rf /tmp/example'\n", 1),
                new Case("trap 'rm -rf /tmp/example' EXIT\n", 1));
        for (Case testCase : cases) {
            LiteralHarvest harvest = analysis.harvestLiterals(testCase.script());
            assertEquals(testCase.expectedCommands(), harvest.size(),
                    "<<" + testCase.script() + ">> recovered " + harvest.commandNames());
        }
    }

    /** A malformed script must not be reported as if it had been understood. */
    @Test
    void aMalformedScriptIsNotReportedAsUnderstood() {
        assertTrue(analysis.harvestLiterals("if then rm -rf /tmp/example\n").isEmpty());
    }
}
