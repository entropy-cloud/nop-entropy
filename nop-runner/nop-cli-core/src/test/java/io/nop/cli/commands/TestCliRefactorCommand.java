package io.nop.cli.commands;

import io.nop.autotest.junit.JunitBaseTestCase;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for the {@code nop-cli refactor} bridge.
 *
 * <p>{@code RefactorOptions.parse} owns fail-closed parsing and expects
 * {@code preview|apply --rules <vfs-prefix> [--json] <file|dir>...}. The emitted
 * argv must match that grammar positionally, so a renamed option or reordered
 * argument fails here instead of surfacing as a runtime usage error.
 */
public class TestCliRefactorCommand extends JunitBaseTestCase {

    @Test
    void testRegisteredInMainCommand() {
        String help = new CommandLine(new MainCommand()).getUsageMessage().replaceAll("\\s+", " ");
        assertTrue(help.contains("refactor"), "refactor should appear in help output");
    }

    @Test
    void testEmitsEngineGrammarOrder() {
        CliRefactorCommand cmd = new CliRefactorCommand();
        cmd.mode = "preview";
        cmd.rules = "demo/rules";
        cmd.json = false;
        cmd.targets = new String[]{"src/a.java", "src/b.java"};

        assertArrayEquals(new String[]{
                "preview", "--rules", "demo/rules", "src/a.java", "src/b.java"
        }, cmd.toArgv());
    }

    @Test
    void testJsonFlagPrecedesTargets() {
        CliRefactorCommand cmd = new CliRefactorCommand();
        cmd.mode = "apply";
        cmd.rules = "demo/rules";
        cmd.json = true;
        cmd.targets = new String[]{"src/a.java"};

        assertArrayEquals(new String[]{
                "apply", "--rules", "demo/rules", "--json", "src/a.java"
        }, cmd.toArgv());
    }

    @Test
    void testRulesOptionIsRequired() {
        CommandLine commandLine = new CommandLine(new MainCommand());
        // --rules is the verified safe surface; omitting it must be rejected at
        // parse time rather than silently defaulting to some ruleset.
        assertThrows(CommandLine.ParameterException.class,
                () -> commandLine.parseArgs("refactor", "preview", "src/a.java"),
                "refactor without --rules must fail closed");
    }
}