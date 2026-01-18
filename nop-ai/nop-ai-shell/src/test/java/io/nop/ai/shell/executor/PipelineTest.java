/*
 * Copyright (c) 2025, Entropy Cloud
 *
 * Licensed to the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nop.ai.shell.executor;

import io.nop.ai.shell.parser.impl.DefaultParser;
import io.nop.ai.shell.registry.CommandRegistry;
import io.nop.ai.shell.script.ScriptEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for pipeline functionality.
 */
public class PipelineTest {

    private PipeExecutor pipeExecutor;
    private CommandRegistry registry;
    private DefaultParser parser;

    @BeforeEach
    void setUp() {
        parser = new DefaultParser();
        registry = createTestRegistry();
        pipeExecutor = new PipeExecutor(registry, null);
    }

    @AfterEach
    void tearDown() {
        cleanupTestFiles();
    }

    @Test
    void testStandardPipe() {
        ExecutionResult result = pipeExecutor.execute("echo hello | echo world");
        
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("world"));
        assertTrue(result.isSuccess());
    }

    @Test
    void testAndPipeSuccess() {
        ExecutionResult result = pipeExecutor.execute("echo success && echo also-runs");
        
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("success"));
        assertTrue(result.stdout().contains("also-runs"));
        assertTrue(result.isSuccess());
    }

    @Test
    void testAndPipeFailure() {
        ExecutionResult result = pipeExecutor.execute("exit 1 && echo should-not-run");
        
        assertEquals(1, result.exitCode());
        assertFalse(result.stdout().contains("should-not-run"));
        assertFalse(result.stdout().contains("exit"));
    }

    @Test
    void testOrPipeFailure() {
        ExecutionResult result = pipeExecutor.execute("exit 1 || echo fallback");
        
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("fallback"));
        assertFalse(result.stdout().contains("exit"));
    }

    @Test
    void testOrPipeSuccess() {
        ExecutionResult result = pipeExecutor.execute("echo success || echo fallback");
        
        assertEquals(0, result.exitCode());
        assertFalse(result.stdout().contains("fallback"));
        assertTrue(result.stdout().contains("success"));
    }

    @Test
    void testOutputRedirect() {
        ExecutionResult result = pipeExecutor.execute("echo test > test-output.txt");
        
        assertEquals(0, result.exitCode());
        assertTrue(result.isSuccess());
        assertTrue(Files.exists(Paths.get("test-output.txt")));
    }

    @Test
    void testOutputAppend() {
        ExecutionResult result = pipeExecutor.execute(
                "echo line1 >> test-append.txt && echo line2 >> test-append.txt");
        
        assertEquals(0, result.exitCode());
        assertTrue(Files.exists(Paths.get("test-append.txt")));
        
        String content;
        try {
            content = Files.readString(Paths.get("test-append.txt"));
            assertNotNull(content);
            assertTrue(content.contains("line1"));
            assertTrue(content.contains("line2"));
        } catch (Exception e) {
            fail("Failed to read file: " + e.getMessage());
        } finally {
            cleanupTestFiles();
        }
    }

    @Test
    void testComplexPipeline() {
        ExecutionResult result = pipeExecutor.execute(
                "echo hello | echo world && echo done > test-complex.txt");

        assertEquals(0, result.exitCode());
        // When output is redirected to file, check that file contains output
        assertTrue(Files.exists(Paths.get("test-complex.txt")));
        String fileContent;
        try {
            fileContent = Files.readString(Paths.get("test-complex.txt"));
            assertTrue(fileContent.contains("done"));
        } catch (Exception e) {
            fail("Failed to read file: " + e.getMessage());
        }
        cleanupTestFiles();
    }

    private CommandRegistry createTestRegistry() {
        return new CommandRegistry() {
            @Override
            public Object invoke(CommandSession session, String command, Object... args) throws Exception {
                if ("echo".equals(command)) {
                    StringBuilder sb = new StringBuilder();
                    for (Object arg : args) {
                        sb.append(arg != null ? arg.toString() : "");
                        sb.append(" ");
                    }
                    // Check if this is stderr redirect (2>)
                    boolean isStderrRedirect = args.length > 0 && "2>".equals(args[0]);
                    if (isStderrRedirect) {
                        // Write to stderr (stream 2 is being redirected)
                        session.err().println(sb.toString().trim());
                    } else {
                        // Write to stdout
                        session.out().println(sb.toString().trim());
                    }
                    return 0;
                }
                
                if ("exit".equals(command)) {
                    int exitCode = 0;
                    if (args.length == 0) {
                        return 0;
                    }
                    if ("0".equals(args[0])) {
                        return 0;
                    }
                    if ("1".equals(args[0])) {
                        exitCode = 1;
                    }
                    if (args.length == 1 && "exit".equals(args[0])) {
                        try {
                            exitCode = Integer.parseInt(args[0].toString());
                        } catch (NumberFormatException e) {
                            exitCode = 1;
                        }
                    }
                    return exitCode;
                }
                
                session.err().println("Unknown command: " + command);
                return 1;
            }

            @Override
            public Set<String> commandNames() {
                return Set.of("echo", "exit");
            }

            @Override
            public Map<String, String> commandAliases() {
                return Map.of();
            }

            @Override
            public boolean hasCommand(String command) {
                return commandNames().contains(command);
            }
        };
    }

    private void cleanupTestFiles() {
        String[] testFiles = {
                "test-output.txt", "test-append.txt",
                "test-error.txt", "test-complex.txt"
        };
        
        for (String filename : testFiles) {
            try {
                Files.deleteIfExists(Paths.get(filename));
            } catch (Exception e) {
            // Ignore cleanup errors
            }
        }
    }
}
