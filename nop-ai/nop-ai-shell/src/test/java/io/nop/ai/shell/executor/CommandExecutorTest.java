package io.nop.ai.shell.executor;

import io.nop.ai.shell.parser.impl.DefaultParser;
import io.nop.ai.shell.registry.CommandRegistry;
import io.nop.ai.shell.script.ScriptEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CommandExecutorTest {

    private static class SimpleTestRegistry implements CommandRegistry {
        @Override
        public Set<String> commandNames() {
            return Set.of("echo", "ls", "pwd", "exit");
        }

        @Override
        public Map<String, String> commandAliases() {
            return Map.of("e", "echo", "q", "exit");
        }

        @Override
        public boolean hasCommand(String command) {
            return commandNames().contains(command) || commandAliases().containsKey(command);
        }

        @Override
        public Object invoke(CommandSession session, String command, Object... args) throws Exception {
            switch (command) {
                case "echo":
                case "e":
                    for (Object arg : args) {
                        session.out().print(arg);
                        session.out().print(" ");
                    }
                    session.out().println();
                    return 0;

                case "ls":
                    session.out().println("file1.txt file2.txt directory/");
                    return 0;

                case "pwd":
                    session.out().println("/current/directory");
                    return 0;

                case "exit":
                case "q":
                    session.out().println("Exiting...");
                    return 0;

                default:
                    session.err().println("Unknown command: " + command);
                    return 1;
            }
        }
    }

    private CommandExecutor executor;

    @BeforeEach
    void setUp() {
        DefaultParser parser = new DefaultParser();
        CommandRegistry registry = new SimpleTestRegistry();
        ScriptEngine scriptEngine = null;
        executor = new CommandExecutor(parser, registry, scriptEngine);
    }

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    @Test
    void testSimpleCommand() {
        ExecutionResult result = executor.execute("echo hello world");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("hello"));
        assertTrue(result.isSuccess());
    }

    @Test
    void testSimpleCommandWithAlias() {
        ExecutionResult result = executor.execute("e hello world");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("hello"));
        assertTrue(result.isSuccess());
    }

    @Test
    void testUnknownCommand() {
        ExecutionResult result = executor.execute("unknown_command");

        assertEquals(1, result.exitCode());
        assertFalse(result.isSuccess());
        assertTrue(result.stderr().contains("Unknown command"));
    }

    @Test
    void testCommandWithMultipleArgs() {
        ExecutionResult result = executor.execute("echo one two three");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("one"));
        assertTrue(result.stdout().contains("two"));
        assertTrue(result.stdout().contains("three"));
    }

    @Test
    void testLsCommand() {
        ExecutionResult result = executor.execute("ls");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("file1.txt"));
        assertTrue(result.isSuccess());
    }

    @Test
    void testPwdCommand() {
        ExecutionResult result = executor.execute("pwd");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("/current/directory"));
        assertTrue(result.isSuccess());
    }

    @Test
    void testExitCommand() {
        ExecutionResult result = executor.execute("exit");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Exiting"));
        assertTrue(result.isSuccess());
    }

    @Test
    void testEmptyCommand() {
        ExecutionResult result = executor.execute("");

        assertEquals(0, result.exitCode());
        assertNotNull(result.stdout());
    }
}
