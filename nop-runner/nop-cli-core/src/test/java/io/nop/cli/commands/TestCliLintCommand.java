package io.nop.cli.commands;

import io.nop.autotest.junit.JunitBaseTestCase;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

public class TestCliLintCommand extends JunitBaseTestCase {

    @Test
    void testRegisteredInMainCommand() {
        MainCommand main = new MainCommand();
        CommandLine cmd = new CommandLine(main);
        String help = cmd.getUsageMessage().replaceAll("\\s+", " ");
        assertTrue(help.contains("lint"), "lint should appear in help output");
    }

    @Test
    void testToArgvContainsSubcommand() {
        CliLintCommand cmd = new CliLintCommand();
        cmd.subcommand = "check";
        cmd.targets = new String[]{"src/"};
        String[] argv = cmd.toArgv();
        assertEquals("check", argv[0]);
        assertEquals("src/", argv[1]);
    }
}
