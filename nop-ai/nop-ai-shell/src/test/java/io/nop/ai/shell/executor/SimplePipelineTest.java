package io.nop.ai.shell.executor;

import io.nop.ai.shell.parser.impl.DefaultParser;
import io.nop.ai.shell.registry.CommandRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple integration test for pipeline functionality.
 */
public class SimplePipelineTest {

    @Test
    public void testStandardPipe() {
        DefaultParser parser = new DefaultParser();
        CommandRegistry registry = new SimpleTestRegistry();
        PipeExecutor pipeExecutor = new PipeExecutor(registry, null);
        
        ExecutionResult result = pipeExecutor.execute("echo hello | echo world");
        
        System.out.println("Exit code: " + result.exitCode());
        System.out.println("Stdout: " + result.stdout());
        System.out.println("Is success: " + result.isSuccess());
        
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("world"));
        assertTrue(result.isSuccess());
    }

    @Test
    public void testOutputRedirect() {
        DefaultParser parser = new DefaultParser();
        CommandRegistry registry = new SimpleTestRegistry();
        PipeExecutor pipeExecutor = new PipeExecutor(registry, null);
        
        ExecutionResult result = pipeExecutor.execute("echo test > simple-output.txt");
        
        System.out.println("Exit code: " + result.exitCode());
        System.out.println("Stdout: " + result.stdout());
        
        assertEquals(0, result.exitCode());
        assertTrue(result.isSuccess());
        
        java.io.File file = new java.io.File("simple-output.txt");
        assertTrue(file.exists());
        file.delete();
    }

    private static class SimpleTestRegistry implements CommandRegistry {
        @Override
        public java.util.Set<String> commandNames() {
            return java.util.Set.of("echo");
        }

        @Override
        public java.util.Map<String, String> commandAliases() {
            return java.util.Map.of();
        }

        @Override
        public boolean hasCommand(String command) {
            return commandNames().contains(command);
        }

        @Override
        public Object invoke(CommandSession session, String command, Object... args) throws Exception {
            if ("echo".equals(command)) {
                StringBuilder sb = new StringBuilder();
                for (Object arg : args) {
                    sb.append(arg != null ? arg.toString() : "");
                    sb.append(" ");
                }
                session.out().println(sb.toString().trim());
                return 0;
            }
            return 1;
        }
    }
}
