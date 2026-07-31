package io.nop.ai.shell.checker;

import io.nop.ai.shell.model.SimpleCommand;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCommandChecker {

    private static final ICommandCheckContext CTX = new ICommandCheckContext() {
        @Override
        public String workingDirectory() {
            return "/work";
        }

        @Override
        public Map<String, String> environment() {
            return Map.of("PATH", "/usr/bin");
        }

        @Override
        public boolean isRegisteredCommand(String commandName) {
            return commandName.equals("echo");
        }
    };

    @Test
    public void testDefaultCommandCheckerAllowsAll() {
        DefaultCommandChecker checker = new DefaultCommandChecker();

        assertNull(checker.check(SimpleCommand.builder("echo").arg("hello").build(), CTX));
        assertNull(checker.check(SimpleCommand.builder("rm").arg("-rf").arg("/").build(), CTX));
        assertNull(checker.check(SimpleCommand.builder("dangerous").build(), CTX));
    }

    @Test
    public void testCheckerContractPassReturnsNull() {
        ICommandChecker checker = (command, ctx) -> null;
        assertNull(checker.check(SimpleCommand.builder("echo").build(), CTX));
    }

    @Test
    public void testCheckerContractRejectReturnsMessage() {
        ICommandChecker checker = (command, ctx) ->
                ctx.isRegisteredCommand(command.getCommand()) ? null : "command not registered: " + command.getCommand();

        assertNull(checker.check(SimpleCommand.builder("echo").build(), CTX));

        String message = checker.check(SimpleCommand.builder("unknown").build(), CTX);
        assertTrue(message != null && message.contains("not registered"), "reject message should explain the rejection");
    }

    @Test
    public void testCheckContextContract() {
        assertEquals("/work", CTX.workingDirectory());
        assertEquals("/usr/bin", CTX.environment().get("PATH"));
        assertTrue(CTX.isRegisteredCommand("echo"));
    }
}
