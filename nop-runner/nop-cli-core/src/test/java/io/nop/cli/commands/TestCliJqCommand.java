package io.nop.cli.commands;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

public class TestCliJqCommand {

    @Test
    void testRegisteredInMainCommand() {
        MainCommand main = new MainCommand();
        CommandLine cmd = new CommandLine(main);
        String help = cmd.getUsageMessage().replaceAll("\\s+", " ");
        assertTrue(help.contains("jq"), "jq should appear in help output");
    }

    @Test
    void testCommandInstantiation() {
        CliJqCommand cmd = new CliJqCommand();
        assertNotNull(cmd);
        assertNull(cmd.expression);
        assertNull(cmd.inputFile);
    }

    @Test
    void testCommandLineParsing() {
        MainCommand main = new MainCommand();
        CommandLine cmd = new CommandLine(main);
        cmd.parseArgs("jq", ".");
        // If parsing succeeds without exception, the subcommand is registered
        assertTrue(true);
    }
}
