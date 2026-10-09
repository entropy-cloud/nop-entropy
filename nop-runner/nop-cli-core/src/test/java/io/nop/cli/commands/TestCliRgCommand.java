package io.nop.cli.commands;

import io.nop.autotest.junit.JunitBaseTestCase;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end wiring tests for the {@code nop-cli rg} subcommand.
 *
 * <p>These assert real search outcomes and exit codes rather than a loose
 * "did not throw" bound: a reflective bridge that hands an unparsed
 * {@code NopRgMain} instance to {@code call()} NPEs on {@code Path.of(null)},
 * and a vacuous assertion would not catch it.
 */
public class TestCliRgCommand extends JunitBaseTestCase {

    private Path corpus;

    private void writeCorpus() throws Exception {
        corpus = Files.createTempDirectory("nop-cli-rg-test");
        Files.writeString(corpus.resolve("a.txt"), "alpha needle here\nnothing\n");
        Files.writeString(corpus.resolve("b.txt"), "beta\nanother needle\n");
    }

    @Test
    void testRegisteredInMainCommand() {
        String help = new CommandLine(new MainCommand()).getUsageMessage().replaceAll("\\s+", " ");
        assertTrue(help.contains("rg"), "rg should appear in help output");
    }

    @Test
    void testSearchFindsMatchesAndReturnsZero() throws Exception {
        writeCorpus();
        try {
            int exit = ToolBridge.runRg(new String[]{"needle", corpus.toString()},
                    System.out, System.err);
            assertEquals(0, exit, "a matching search must exit 0");
        } finally {
            deleteRecursively(corpus);
        }
    }

    @Test
    void testSearchWithoutMatchesReturnsOne() throws Exception {
        writeCorpus();
        try {
            int exit = ToolBridge.runRg(new String[]{"zzz-not-present", corpus.toString()},
                    System.out, System.err);
            assertEquals(1, exit, "a non-matching search must exit 1 (rg convention)");
        } finally {
            deleteRecursively(corpus);
        }
    }

    @Test
    void testMissingPathReturnsTwo() throws Exception {
        int exit = ToolBridge.runRg(new String[]{"needle", corpus + "-does-not-exist"},
                System.out, System.err);
        assertEquals(2, exit, "an unusable search root must exit 2");
    }

    private static void deleteRecursively(Path root) throws Exception {
        if (root == null || !Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (Exception ignored) {
                    // best-effort temp cleanup
                }
            });
        }
    }
}