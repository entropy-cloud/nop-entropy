package io.nop.ai.shell.adapter;

import io.nop.ai.shell.model.SimpleCommand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ExternalCommandAdapter is a deliberate fallback stub: real external process execution lives in
 * the nop-shell module which is not a dependency of nop-ai-shell. Its only contract is the
 * {@link UnsupportedOperationException} message; the executor's fallback wiring (127 + stderr
 * message) is covered by {@code ShellCommandExecutorTest.testCommandNotFoundReturns127}.
 */
public class TestExternalCommandAdapter {

    @Test
    public void testFallbackThrowsWithCommandName() {
        ExternalCommandAdapter adapter = new ExternalCommandAdapter();
        SimpleCommand cmd = SimpleCommand.builder("git").arg("status").build();

        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class, () ->
                adapter.execute(cmd, null, null, null, null));

        assertTrue(ex.getMessage().contains("requires nop-shell dependency"),
                "message should explain the missing dependency: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("git"),
                "message should include the command name: " + ex.getMessage());
    }

    @Test
    public void testFallbackThrowsForAnyCommand() {
        ExternalCommandAdapter adapter = new ExternalCommandAdapter();
        SimpleCommand cmd = SimpleCommand.builder("ls").arg("-la").build();

        assertThrows(UnsupportedOperationException.class, () ->
                adapter.execute(cmd, null, null, null, null));
    }

    @Test
    public void testErrorMessageMatchesExecutorFallback() {
        ExternalCommandAdapter adapter = new ExternalCommandAdapter();
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class, () ->
                adapter.execute(SimpleCommand.builder("nonexistent_cmd").build(), null, null, null, null));
        assertEquals("External command fallback requires nop-shell dependency. Command: nonexistent_cmd",
                ex.getMessage());
    }
}
