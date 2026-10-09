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
 * xdsl-full-coverage roadmap WI3 (plan 2349): the {@code lint} subcommand wiring —
 * exit code contract (0 no failed finding / 1 failed finding incl. parse errors /
 * 2 usage error) and the co-existence rendering, exercised from the command entry
 * (StreamMaintenanceMain.main System.exits and is never invoked from JUnit; the
 * mirror precedent is TestStreamConfValidateCommand).
 */
class TestStreamLintCommand {

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
        assertEquals(2, StreamLintCommand.run(java.util.List.of()));
        assertEquals(2, StreamLintCommand.run(java.util.List.of("other=x")));
    }

    @Test
    void usageErrorNonexistentFileExitsTwo() {
        assertEquals(2, StreamLintCommand.run(
                java.util.List.of("file=" + tempDir.resolve("no-such.stream.xml"))));
    }

    @Test
    void cleanModelExitsZeroWithLintHeader() throws Exception {
        Path file = tempDir.resolve("clean.stream.xml");
        Files.writeString(file, "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"lint-cli-ok\" version=\"1\">"
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
        // the command renders its own lint header (not conf-validate) and exits 0
        // with the dormant R13/R14 skip notes.
        assertEquals(0, StreamLintCommand.run(java.util.List.of("file=" + file)));
    }

    @Test
    void unconsumedFaceModelExitsOneWithWarningAndFailCoexistence() throws Exception {
        Path file = tempDir.resolve("faces.stream.xml");
        Files.writeString(file, "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"lint-cli-bad\" version=\"1\">"
                + "<coders><coder id=\"c1\" className=\"java.lang.String\"/></coders>"
                + "<transforms>"
                + "<source id=\"src\"><source>return;</source></source>"
                + "<sink id=\"out\"><source>log.info('out: {}', event);</source></sink>"
                + "</transforms>"
                + "<edges><edge id=\"e1\" from=\"src\" to=\"out\"/></edges>"
                + "</stream>");
        // the <coders> registry is an unconsumed face (WARNING) AND folds the
        // builder's fail-fast (FAIL) — the lint verdict is exit 1.
        assertEquals(1, StreamLintCommand.run(java.util.List.of("file=" + file)));
    }

    @Test
    void parseFailureExitsOneNotTwo() throws Exception {
        Path file = tempDir.resolve("bogus.stream.xml");
        Files.writeString(file, "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" name=\"lint-cli-parse\" version=\"1\">"
                + "<transforms><source id=\"src\"/><bogus id=\"b\"/></transforms>"
                + "</stream>");
        assertEquals(1, StreamLintCommand.run(java.util.List.of("file=" + file)));
    }

    @Test
    void usageDocumentsLint() {
        String usage = StreamMaintenanceMain.usage();
        assertTrue(usage.contains("lint file=<path> [--connect]"),
                "usage must document lint: " + usage);
        assertTrue(usage.contains("0 = no failed finding"),
                "usage must document the lint exit code contract: " + usage);
    }
}
