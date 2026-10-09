package io.nop.cli.commands;

import io.nop.autotest.junit.JunitBaseTestCase;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wiring tests for the {@code nop-cli lint} subcommand.
 *
 * <p>The lint command surface ({@code check|match|test} plus its options) belongs to
 * {@code NopLintCli}; this subcommand only forwards. The exit-code contract under test
 * is that a rejected run surfaces as non-zero rather than being swallowed into a
 * success report.
 */
public class TestCliLintCommand extends JunitBaseTestCase {

    private static CliLintCommand commandFor(String... argv) {
        CliLintCommand cmd = new CliLintCommand();
        cmd.args = List.of(argv);
        return cmd;
    }

    @Test
    void testRegisteredInMainCommand() {
        String help = new CommandLine(new MainCommand()).getUsageMessage().replaceAll("\\s+", " ");
        assertTrue(help.contains("lint"), "lint should appear in help output");
    }

    @Test
    void testForwardsArgumentsVerbatim() {
        assertArrayEquals(new String[]{"check", "src/main", "--profile", "standard", "--json"},
                commandFor("check", "src/main", "--profile", "standard", "--json").toArgv());
    }

    @Test
    void testUnknownSubcommandFailsClosed() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(err));
        int exit;
        try {
            exit = commandFor("bogus-subcommand").call();
        } finally {
            System.setErr(originalErr);
        }
        assertEquals(2, exit, "an unknown lint subcommand must exit 2, not report success");
    }
}