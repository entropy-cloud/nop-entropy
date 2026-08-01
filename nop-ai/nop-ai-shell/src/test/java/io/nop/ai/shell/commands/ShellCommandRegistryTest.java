package io.nop.ai.shell.commands;

import io.nop.ai.shell.NopAiShellErrors;
import io.nop.ai.shell.commands.impl.EchoCommand;
import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShellCommandRegistryTest {

    @Test
    void testRegisterCommand() {
        ShellCommandRegistry registry = new ShellCommandRegistry();
        EchoCommand echo = new EchoCommand();

        registry.registerCommand(echo);

        assertTrue(registry.hasCommand("echo"));
        assertEquals(echo, registry.findCommand("echo"));
    }

    @Test
    void testRegisterMultipleCommands() {
        ShellCommandRegistry registry = new ShellCommandRegistry();
        EchoCommand echo1 = new EchoCommand();
        EchoCommand echo2 = new EchoCommand();

        registry.registerCommand(echo1);
        registry.registerCommand(echo2);

        assertEquals(1, registry.listCommandNames().length);
        assertEquals("echo", registry.listCommandNames()[0]);
    }

    @Test
    void testRegisterNullCommand() {
        ShellCommandRegistry registry = new ShellCommandRegistry();

        assertThrows(NopException.class, () -> registry.registerCommand(null));
    }

    @Test
    void testRegisterCommandWithNullName() {
        ShellCommandRegistry registry = new ShellCommandRegistry();
        IShellCommand command = new IShellCommand() {
            @Override
            public String name() {
                return null;
            }

            @Override
            public String description() {
                return "Test command";
            }

            @Override
            public String usage() {
                return "test";
            }

            @Override
            public String getHelp() {
                return "Help";
            }

            @Override
            public int execute(IShellCommandExecutionContext context) throws Exception {
                return 0;
            }
        };

        assertThrows(NopException.class, () -> registry.registerCommand(command));
    }

    @Test
    void testRegisterAlias() {
        ShellCommandRegistry registry = new ShellCommandRegistry();
        EchoCommand echo = new EchoCommand();

        registry.registerCommand(echo);
        registry.registerAlias("e", "echo");

        assertTrue(registry.hasCommand("e"));
        assertEquals(echo, registry.findCommand("e"));
    }

    @Test
    void testRegisterAliasForNonexistentCommand() {
        ShellCommandRegistry registry = new ShellCommandRegistry();

        assertThrows(NopException.class, () -> registry.registerAlias("alias", "nonexistent"));
    }

    @Test
    void testRegisterNullAlias() {
        ShellCommandRegistry registry = new ShellCommandRegistry();
        EchoCommand echo = new EchoCommand();

        registry.registerCommand(echo);

        assertThrows(NopException.class, () -> registry.registerAlias(null, "echo"));
    }

    @Test
    void testRegisterNullCommandNameForAlias() {
        ShellCommandRegistry registry = new ShellCommandRegistry();
        EchoCommand echo = new EchoCommand();

        registry.registerCommand(echo);

        assertThrows(NopException.class, () -> registry.registerAlias("alias", null));
    }

    @Test
    void testFindCommand() {
        ShellCommandRegistry registry = new ShellCommandRegistry();
        EchoCommand echo = new EchoCommand();

        registry.registerCommand(echo);

        IShellCommand found = registry.findCommand("echo");

        assertNotNull(found);
        assertEquals(echo, found);
    }

    @Test
    void testFindCommandByAlias() {
        ShellCommandRegistry registry = new ShellCommandRegistry();
        EchoCommand echo = new EchoCommand();

        registry.registerCommand(echo);
        registry.registerAlias("e", "echo");

        IShellCommand found = registry.findCommand("e");

        assertNotNull(found);
        assertEquals(echo, found);
    }

    @Test
    void testFindNonexistentCommand() {
        ShellCommandRegistry registry = new ShellCommandRegistry();

        IShellCommand found = registry.findCommand("nonexistent");

        assertNull(found);
    }

    @Test
    void testFindNullCommand() {
        ShellCommandRegistry registry = new ShellCommandRegistry();

        IShellCommand found = registry.findCommand(null);

        assertNull(found);
    }

    @Test
    void testHasCommand() {
        ShellCommandRegistry registry = new ShellCommandRegistry();
        EchoCommand echo = new EchoCommand();

        registry.registerCommand(echo);

        assertTrue(registry.hasCommand("echo"));
        assertFalse(registry.hasCommand("nonexistent"));
    }

    @Test
    void testHasCommandByAlias() {
        ShellCommandRegistry registry = new ShellCommandRegistry();
        EchoCommand echo = new EchoCommand();

        registry.registerCommand(echo);
        registry.registerAlias("e", "echo");

        assertTrue(registry.hasCommand("e"));
    }

    @Test
    void testGetAllCommands() {
        ShellCommandRegistry registry = new ShellCommandRegistry();
        EchoCommand echo = new EchoCommand();

        registry.registerCommand(echo);
        registry.registerAlias("e", "echo");

        var allCommands = registry.getAllCommands();

        assertEquals(1, allCommands.size());
        assertTrue(allCommands.containsKey("echo"));
        assertFalse(allCommands.containsKey("e"));
    }

    @Test
    void testGetAllIncludingAliases() {
        ShellCommandRegistry registry = new ShellCommandRegistry();
        EchoCommand echo = new EchoCommand();

        registry.registerCommand(echo);
        registry.registerAlias("e", "echo");

        var allIncludingAliases = registry.getAllIncludingAliases();

        assertEquals(2, allIncludingAliases.size());
        assertTrue(allIncludingAliases.containsKey("echo"));
        assertTrue(allIncludingAliases.containsKey("e"));
        assertEquals(echo, allIncludingAliases.get("echo"));
        assertEquals(echo, allIncludingAliases.get("e"));
    }

    @Test
    void testListCommandNames() {
        ShellCommandRegistry registry = new ShellCommandRegistry();
        EchoCommand echo = new EchoCommand();

        registry.registerCommand(echo);

        String[] names = registry.listCommandNames();

        assertEquals(1, names.length);
        assertEquals("echo", names[0]);
    }

    @Test
    void testListCommandNamesEmpty() {
        ShellCommandRegistry registry = new ShellCommandRegistry();

        String[] names = registry.listCommandNames();

        assertEquals(0, names.length);
    }

    /**
     * P3-MA3-1 focused value-level assertions (plan 2026-08-01-0936-1): the
     * converted validation guards must fail fast with the module ErrorCodes
     * and preserve the verbatim English messages via params.
     */
    @Test
    void validationFailuresCarryErrorCodeAndVerbatimMessage() {
        ShellCommandRegistry registry = new ShellCommandRegistry();

        NopException e1 = assertThrows(NopException.class, () -> registry.registerCommand(null));
        assertEquals(NopAiShellErrors.ERR_AI_SHELL_INVALID_ARG.getErrorCode(), e1.getErrorCode());
        assertEquals("Command cannot be null", e1.getParams().get(NopAiShellErrors.ARG_MSG));

        NopException e2 = assertThrows(NopException.class,
                () -> registry.registerAlias("alias", "nonexistent"));
        assertEquals(NopAiShellErrors.ERR_AI_SHELL_COMMAND_NOT_FOUND.getErrorCode(), e2.getErrorCode());
        assertEquals("nonexistent", e2.getParams().get(NopAiShellErrors.ARG_COMMAND_NAME));
        assertTrue(e2.getMessage().contains("Command not found: nonexistent"));
    }
}
