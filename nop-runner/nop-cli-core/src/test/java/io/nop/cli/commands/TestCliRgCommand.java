package io.nop.cli.commands;

import io.nop.autotest.junit.JunitBaseTestCase;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wiring tests for the {@code nop-cli rg} subcommand.
 *
 * <p>Asserts real search outcomes and exit codes rather than a loose "did not throw"
 * bound: a bridge that hands an unparsed command instance to {@code call()} NPEs, and a
 * vacuous assertion would not catch it.
 */
public class TestCliRgCommand extends JunitBaseTestCase {

    private Path corpus;

    private void writeCorpus() throws Exception {
        corpus = Files.createTempDirectory("nop-cli-rg-test");
        Files.writeString(corpus.resolve("a.txt"), "alpha needle here\nnothing\n");
        Files.writeString(corpus.resolve("b.txt"), "beta\nanother needle\n");
    }

    private static CliRgCommand commandFor(String... argv) {
        CliRgCommand cmd = new CliRgCommand();
        cmd.args = List.of(argv);
        return cmd;
    }

    @Test
    void testRegisteredInMainCommand() {
        String help = new CommandLine(new MainCommand()).getUsageMessage().replaceAll("\\s+", " ");
        assertTrue(help.contains("rg"), "rg should appear in help output");
    }

    @Test
    void testForwardsArgumentsVerbatim() {
        // no option is redeclared here, so argv must reach NopRgMain untouched
        assertArrayEquals(new String[]{"needle", "corpus", "-i", "--json"},
                commandFor("needle", "corpus", "-i", "--json").toArgv());
    }

    @Test
    void testSearchFindsMatchesAndReturnsZero() throws Exception {
        writeCorpus();
        try {
            assertEquals(0, commandFor("needle", corpus.toString()).call(),
                    "a matching search must exit 0");
        } finally {
            deleteRecursively(corpus);
        }
    }

    @Test
    void testSearchWithoutMatchesReturnsOne() throws Exception {
        writeCorpus();
        try {
            assertEquals(1, commandFor("zzz-not-present", corpus.toString()).call(),
                    "a non-matching search must exit 1 (rg convention)");
        } finally {
            deleteRecursively(corpus);
        }
    }

    @Test
    void testMissingPathReturnsTwo() {
        assertEquals(2, commandFor("needle", "/no-such-dir-" + System.nanoTime()).call(),
                "an unusable search root must exit 2");
    }

    private static void deleteRecursively(Path root) throws Exception {
        if (root == null || !Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (Exception ignored) {
                    // best-effort temp cleanup
                }
            });
        }
    }
}