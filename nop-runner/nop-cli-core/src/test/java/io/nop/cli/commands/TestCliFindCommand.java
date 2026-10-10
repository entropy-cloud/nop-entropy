package io.nop.cli.commands;

import io.nop.autotest.junit.JunitBaseTestCase;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wiring tests for the {@code nop-cli find} subcommand.
 *
 * <p>Asserts real lookup outcomes, forwarded argv and exit codes rather than a loose
 * "did not throw" bound: a bridge that hands an unparsed command instance to {@code call()}
 * NPEs, and a vacuous assertion would not catch it.
 */
public class TestCliFindCommand extends JunitBaseTestCase {

    private Path corpus;

    private void writeCorpus() throws Exception {
        corpus = Files.createTempDirectory("nop-cli-find-test");
        Files.createDirectories(corpus.resolve("src/main/java"));
        Files.writeString(corpus.resolve("src/main/java/NopFinder.java"), "class NopFinder {}\n");
        Files.writeString(corpus.resolve("plain-nopfind.txt"), "x\n");
    }

    private static CliFindCommand commandFor(String... argv) {
        CliFindCommand cmd = new CliFindCommand();
        cmd.args = List.of(argv);
        return cmd;
    }

    @Test
    void testRegisteredInMainCommand() {
        String help = new CommandLine(new MainCommand()).getUsageMessage().replaceAll("\\s+", " ");
        assertTrue(help.contains(" find "), "find should appear in the nop-cli subcommand list");
        assertTrue(new CommandLine(new MainCommand()).getSubcommands().containsKey("find"),
                "MainCommand.subcommands must contain a 'find' subcommand");
    }

    @Test
    void testForwardsArgumentsVerbatim() {
        // no option is redeclared here, so argv must reach NopFindMain untouched
        assertArrayEquals(new String[]{"nopfind", "corpus", "-n", "5", "--json"},
                commandFor("nopfind", "corpus", "-n", "5", "--json").toArgv());
    }

    @Test
    void testLookupPrintsRankedMatchesAndReturnsZero() throws Exception {
        writeCorpus();
        PrintStream originalOut = System.out;
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outBytes, true));
        int exitCode;
        try {
            exitCode = commandFor("nopfind", corpus.toString()).call();
        } finally {
            System.out.flush();
            System.setOut(originalOut);
            deleteRecursively(corpus);
        }
        String stdout = outBytes.toString();
        assertEquals(0, exitCode, "a matching lookup must exit 0");
        assertTrue(stdout.contains("src/main/java/NopFinder.java"), stdout);
        assertTrue(stdout.contains("plain-nopfind.txt"), stdout);
        // score 前缀，排名靠前者分数更低
        assertTrue(stdout.indexOf("NopFinder.java") < stdout.indexOf("plain-nopfind.txt"), stdout);
    }

    @Test
    void testLookupWithoutMatchesReturnsOne() throws Exception {
        writeCorpus();
        try {
            assertEquals(1, commandFor("zzz-not-present", corpus.toString()).call(),
                    "a non-matching lookup must exit 1 (rg convention)");
        } finally {
            deleteRecursively(corpus);
        }
    }

    @Test
    void testMissingPathReturnsTwo() {
        assertEquals(2, commandFor("nopfind", "/no-such-dir-" + System.nanoTime()).call(),
                "an unusable search root must exit 2");
    }

    @Test
    void testHelpDelegatesToNopFindMain() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outBytes, true));
        int exitCode;
        try {
            exitCode = commandFor("--help").call();
        } finally {
            System.out.flush();
            System.setOut(originalOut);
        }
        String help = outBytes.toString();
        assertEquals(0, exitCode);
        assertTrue(help.contains("nop-find"), help);
        assertTrue(help.contains("--case-sensitive"), help);
        assertTrue(help.contains("QUERY"), help);
    }

    @Test
    void testJsonModeIsForwarded() throws Exception {
        writeCorpus();
        PrintStream originalOut = System.out;
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outBytes, true));
        int exitCode;
        try {
            exitCode = commandFor("nopfind", corpus.toString(), "--json").call();
        } finally {
            System.out.flush();
            System.setOut(originalOut);
            deleteRecursively(corpus);
        }
        String stdout = outBytes.toString();
        assertEquals(0, exitCode, stdout);
        assertTrue(stdout.contains("\"matches\":["), stdout);
        assertTrue(stdout.contains("\"count\":2"), stdout);
        assertFalse(stdout.contains("src/main/java/NopFinder.java\t"), stdout);
    }

    @Test
    void testEndToEndThroughMainCommand() throws Exception {
        // MainCommand → CliFindCommand → NopFindMain → FindCoordinator → FuzzyFileScorer/TopKCollector → stdout
        writeCorpus();
        PrintStream originalOut = System.out;
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outBytes, true));
        int exitCode;
        try {
            exitCode = new CommandLine(new MainCommand()).execute("find", "nopfind", corpus.toString());
        } finally {
            System.out.flush();
            System.setOut(originalOut);
            deleteRecursively(corpus);
        }
        String stdout = outBytes.toString();
        assertEquals(0, exitCode, stdout);
        assertTrue(stdout.contains("7\tsrc/main/java/NopFinder.java"), stdout);
        assertTrue(stdout.contains("106\tplain-nopfind.txt"), stdout);
    }

    private static void deleteRecursively(Path root) throws Exception {
        if (root == null || !Files.exists(root)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}