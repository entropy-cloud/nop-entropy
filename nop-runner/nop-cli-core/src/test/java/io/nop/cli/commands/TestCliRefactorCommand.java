package io.nop.cli.commands;

import io.nop.autotest.junit.JunitBaseTestCase;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wiring tests for the {@code nop-cli refactor} subcommand.
 *
 * <p>The engine grammar {@code preview|apply --rules <vfs-prefix> [--json] <file|dir>...}
 * is owned by {@code RefactorOptions.parse} and is fail-closed, so these assert the
 * arguments survive forwarding untouched and that the engine's own rejection still
 * reaches the caller as a non-zero exit code.
 */
public class TestCliRefactorCommand extends JunitBaseTestCase {

    private static CliRefactorCommand commandFor(String... argv) {
        CliRefactorCommand cmd = new CliRefactorCommand();
        cmd.args = List.of(argv);
        return cmd;
    }

    @Test
    void testRegisteredInMainCommand() {
        String help = new CommandLine(new MainCommand()).getUsageMessage().replaceAll("\\s+", " ");
        assertTrue(help.contains("refactor"), "refactor should appear in help output");
    }

    @Test
    void testForwardsArgumentsVerbatim() {
        // options precede positionals in the engine grammar; forwarding must not reorder
        assertArrayEquals(new String[]{"apply", "--rules", "demo/rules", "--json", "src/a.java"},
                commandFor("apply", "--rules", "demo/rules", "--json", "src/a.java").toArgv());
    }

    @Test
    void testMissingRulesFailsClosed() {
        // --rules is the verified safe surface: the engine must reject the run, and that
        // rejection must not be reported as success.
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(err));
        int exit;
        try {
            exit = commandFor("preview", "src").call();
        } finally {
            System.setErr(originalErr);
        }
        assertEquals(2, exit, "a refactor run without --rules must exit 2, not 0");
        assertTrue(err.toString().contains("--rules"),
                "the engine's usage message must reach stderr: " + err);
    }
}