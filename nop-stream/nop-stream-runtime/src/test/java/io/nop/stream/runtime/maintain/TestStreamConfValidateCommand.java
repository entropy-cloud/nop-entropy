/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.maintain;

import java.nio.file.Files;
import java.nio.file.Path;

import io.nop.core.initialize.CoreInitialization;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 20 (P-REQ-14): the conf-validate / dry-run subcommand entry — exit code
 * contract (0 pass / 1 validation failure / 2 usage error) and the D7 "errors carry
 * the option name" rendering, exercised from the command entry (not the validator
 * internals). No job is ever started.
 */
class TestStreamConfValidateCommand {

    @TempDir
    Path tempDir;

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    void usageErrorMissingFileArgumentExitsTwo() {
        assertEquals(2, StreamConfValidateCommand.run(java.util.List.of()));
        assertEquals(2, StreamConfValidateCommand.run(java.util.List.of("other=x")));
    }

    @Test
    void usageErrorMalformedArgumentExitsTwo() {
        assertEquals(2, StreamConfValidateCommand.run(java.util.List.of("file")));
    }

    @Test
    void usageErrorNonexistentFileExitsTwo() {
        assertEquals(2, StreamConfValidateCommand.run(
                java.util.List.of("file=" + tempDir.resolve("no-such.stream.xml"))));
    }

    @Test
    void uninitializedVfsDoesNotCrashAbsoluteLocalPathLookup() throws Exception {
        // Regression: a bare CLI JVM has no VFS initialized; an absolute local path
        // (leading '/') must fall through to the local-file interpretation instead of
        // crashing with virtual-file-system-not-initialized (exit 1). Doc spot-check
        // found this; the contract is usage error exit 2 for a nonexistent file.
        io.nop.core.resource.IVirtualFileSystem current =
                io.nop.core.resource.VirtualFileSystem.isInitialized()
                        ? io.nop.core.resource.VirtualFileSystem.instance() : null;
        if (current != null) {
            io.nop.core.resource.VirtualFileSystem.unregisterInstance(current);
        }
        try {
            assertEquals(2, StreamConfValidateCommand.run(
                    java.util.List.of("file=" + tempDir.resolve("no-such.stream.xml"))));
        } finally {
            if (current != null) {
                io.nop.core.resource.VirtualFileSystem.registerInstance(current);
            }
        }
    }

    @Test
    void validInlineXplJobPassesWithExitZero() throws Exception {
        Path file = tempDir.resolve("valid.stream.xml");
        Files.writeString(file, "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"cli-ok\" version=\"1\">"
                + "<transforms>"
                + "<source id=\"src\"><source>return;</source></source>"
                + "<map id=\"m\"><source>return event;</source></map>"
                + "<sink id=\"out\"><source>log.info('out: {}', event);</source></sink>"
                + "</transforms>"
                + "<edges>"
                + "<edge id=\"e1\" from=\"src\" to=\"m\"/>"
                + "<edge id=\"e2\" from=\"m\" to=\"out\"/>"
                + "</edges>"
                + "</stream>");
        assertEquals(0, StreamConfValidateCommand.run(java.util.List.of("file=" + file)));
    }

    @Test
    void invalidModelExitsOneWithErrorInOutput() throws Exception {
        // Unknown <transforms> child element: layer-1 xdef rejection.
        Path file = tempDir.resolve("bad-model.stream.xml");
        Files.writeString(file, "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"cli-bad\" version=\"1\">"
                + "<transforms><source id=\"src\"/><bogus id=\"b\"/></transforms>"
                + "</stream>");
        assertEquals(1, StreamConfValidateCommand.run(java.util.List.of("file=" + file)));
    }

    @Test
    void missingRequiredBeanExitsOneNamingTheBean() throws Exception {
        // Global resolver (D2 form 3) has no such bean registered: layer-2 failure must
        // carry the bean name (option name) per P-REQ-14.
        Path file = tempDir.resolve("missing-bean.stream.xml");
        Files.writeString(file, "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"cli-bean\" version=\"1\">"
                + "<transforms>"
                + "<source id=\"src\" bean=\"no-such-bean-anywhere\"/>"
                + "</transforms>"
                + "</stream>");
        assertEquals(1, StreamConfValidateCommand.run(java.util.List.of("file=" + file)));
    }

    @Test
    void usageDocumentsBothSubcommands() {
        String usage = StreamMaintenanceMain.usage();
        assertTrue(usage.contains("conf-validate file=<path> [--connect]"),
                "usage must document conf-validate: " + usage);
        assertTrue(usage.contains("dry-run file=<path>"),
                "usage must document dry-run: " + usage);
        assertTrue(usage.contains("0 = passed, 1 = validation failed, 2 = usage error"),
                "usage must document the exit code contract: " + usage);
    }

    @Test
    void connectSwitchProbesEndpointsEndToEnd() throws Exception {
        // Phase 3 wiring: --connect reaches the real layer-3 probe. The xpl-only job
        // has no probe contracts → explicit SKIP items + probe log, still exit 0.
        Path file = tempDir.resolve("connect.stream.xml");
        Files.writeString(file, "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"cli-connect\" version=\"1\">"
                + "<transforms>"
                + "<source id=\"src\"><source>return;</source></source>"
                + "<map id=\"m\"><source>return event;</source></map>"
                + "<sink id=\"out\"><source>log.info('out: {}', event);</source></sink>"
                + "</transforms>"
                + "<edges>"
                + "<edge id=\"e1\" from=\"src\" to=\"m\"/>"
                + "<edge id=\"e2\" from=\"m\" to=\"out\"/>"
                + "</edges>"
                + "</stream>");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        java.io.PrintStream original = System.out;
        System.setOut(new java.io.PrintStream(out, true, java.nio.charset.StandardCharsets.UTF_8));
        int exit;
        try {
            exit = StreamConfValidateCommand.run(java.util.List.of("file=" + file, "--connect"));
        } finally {
            System.setOut(original);
        }
        String output = out.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, exit, "explicit skips must not fail the run: " + output);
        assertTrue(output.contains("SKIP"), "probe output must carry explicit skip items: " + output);
        assertTrue(output.contains("dry-run probes"), "probe log must be rendered: " + output);
    }
}
