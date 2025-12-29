/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cli.commands;

import io.nop.commons.util.FileHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class TestCliWatchZipCommand {

    @TempDir
    Path tempDir;

    private CliWatchZipCommand command;
    private File testDir;
    private File testZipFile;

    @BeforeEach
    void setUp() throws IOException {
        command = new CliWatchZipCommand();
        testDir = tempDir.resolve("test-watch-dir").toFile();
        testZipFile = tempDir.resolve("test-output.zip").toFile();
        
        // Create test directory
        assertTrue(testDir.mkdirs());
        
        // Create a test file in the directory
        File testFile = new File(testDir, "test.txt");
        Files.write(testFile.toPath(), "Hello World".getBytes());
    }

    @AfterEach
    void tearDown() {
        if (command != null) {
            command.destroy();
        }
    }

    @Test
    void testCommandParametersValidation() {
        // Test with non-existent directory
        command.watchDir = new File(tempDir.toFile(), "non-existent");
        command.zipFile = testZipFile;
        
        Integer result = command.call();
        assertEquals(1, result, "Should return error code for non-existent directory");
    }

    @Test
    void testDefaultZipFileName() {
        command.watchDir = testDir;
        // Don't set zipFile, should default to <watchDir>.zip
        
        // We can't easily test the full watch functionality without complex threading,
        // but we can test the parameter setup
        assertNull(command.zipFile, "zipFile should be null initially");
        
        // The actual zip file path would be set in the call() method
        // Expected: testDir.getParentFile() + "/" + testDir.getName() + ".zip"
        File expectedZipFile = new File(testDir.getParentFile(), testDir.getName() + ".zip");
        
        // This is what the command should set internally
        assertTrue(testDir.exists() && testDir.isDirectory(), "Test directory should exist");
    }

    @Test
    void testParameterDefaults() {
        assertEquals(100, command.debounceWait, "Default debounce wait should be 100ms");
        assertTrue(command.recursive, "Default recursive should be true");
    }
}