package io.nop.cli.commands;

import io.nop.api.core.exceptions.NopException;
import io.nop.cli.CliErrors;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1-4.5-R1/R1b/R2 (plan 2403): CliDiffCommand must load the model named by its
 * parameter (not always newModelPath), both -O/-N are required, a missing resource
 * fails loudly, and the command is reachable through MainCommand. CliWatchCommand
 * must build a watcher without container injection.
 */
class TestCliDiffAndWatchFixes {

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @TempDir
    Path tempDir;

    private Path write(String name, String content) throws Exception {
        Path p = tempDir.resolve(name);
        Files.writeString(p, content);
        return p;
    }

    @Test
    void diffLoadsBothModelsAndProducesNonEmptyDiff() throws Exception {
        Path oldModel = write("old.json", "{\"a\":1}");
        Path newModel = write("new.json", "{\"a\":1,\"b\":2}");
        Path out = tempDir.resolve("diff.json");

        int exit = new CommandLine(new CliDiffCommand())
                .execute("-O", oldModel.toString(), "-N", newModel.toString(), "-o", out.toString());

        assertEquals(0, exit);
        String text = Files.readString(out);
        assertTrue(text.contains("\"b\""),
                "diff must contain the changed key; pre-fix both loads resolved newModelPath so diff was {}: " + text);
    }

    @Test
    void diffRequiresNewModelOption() throws Exception {
        Path oldModel = write("old.json", "{\"a\":1}");
        int exit = new CommandLine(new CliDiffCommand()).execute("-O", oldModel.toString());
        assertNotEquals(0, exit, "-N/--new-model must be required");
    }

    @Test
    void loadModelFailsLoudlyWhenResourceMissing() {
        CliDiffCommand cmd = new CliDiffCommand();
        NopException ex = assertThrows(NopException.class,
                () -> cmd.loadModel(tempDir.resolve("does-not-exist.json").toString()));
        assertEquals(CliErrors.ERR_CLI_FILE_NOT_EXISTS.getErrorCode(), ex.getErrorCode());
    }

    @Test
    void mainCommandRegistersDiffSubcommand() {
        CommandLine.Command annotation = MainCommand.class.getAnnotation(CommandLine.Command.class);
        assertTrue(Arrays.asList(annotation.subcommands()).contains(CliDiffCommand.class),
                "CliDiffCommand must be registered in MainCommand so `nop-cli diff` is reachable");
    }

    @Test
    void diffReachableThroughMainCommandEntryPoint() throws Exception {
        Path oldModel = write("old2.json", "{\"x\":1}");
        Path newModel = write("new2.json", "{\"x\":1,\"y\":3}");
        Path out = tempDir.resolve("diff2.json");

        int exit = new CommandLine(new MainCommand())
                .execute("diff", "-O", oldModel.toString(), "-N", newModel.toString(), "-o", out.toString());

        assertEquals(0, exit, "`nop-cli diff` must execute through the real entry point");
        assertTrue(Files.readString(out).contains("\"y\""));
    }

    /**
     * C10-004 leg 2 (plan 2563 I-C4-002 / adjudication E): a script that throws inside
     * processEvents must be logged (error level) and swallowed - the watch loop survives and
     * later events are still processed (aligns CliWatchCommand with CliWatchZipCommand's
     * catch+LOG.error shape).
     *
     * <p>Log-capture seam: cli-core's active SLF4J backend is jboss-logmanager (no logback on
     * the test classpath), so the design's ListAppender is realized as a JBoss LogManager
     * Handler attached to the command's logger (registered deviation).
     */
    @Test
    void watchProcessEventsLogsScriptFailureAndKeepsProcessingLaterEvents() throws Exception {
        java.util.List<java.util.logging.LogRecord> captured =
                new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.logging.Handler handler = new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord record) {
                captured.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        // attach on the ROOT logger: the error record comes from CliWatchCommand, the
        // "later events processed" proof comes from c:log's own logger - both must be captured
        org.jboss.logmanager.Logger jbossLogger =
                org.jboss.logmanager.LogContext.getLogContext().getLogger("");
        java.util.logging.Level oldLevel = jbossLogger.getLevel();
        jbossLogger.addHandler(handler);
        jbossLogger.setLevel(java.util.logging.Level.FINEST);
        try {
            Path badScript = write("boom.xpl", "<c:script><![CDATA[undefinedFnBoom();]]></c:script>");
            Path goodScript = write("ok.xpl", "<c:log info=\"second-run-ok\"/>");

            CliWatchCommand cmd = new CliWatchCommand();
            cmd.watchDirs = new File[]{tempDir.toFile()};
            cmd.outputDir = tempDir.resolve("out").toFile();
            cmd.scriptFile = badScript.toString();

            io.nop.core.resource.IResource badRes =
                    io.nop.core.resource.ResourceHelper.resolveRelativePathResource(badScript.toString());
            io.nop.xlang.api.XplModel badXpl =
                    io.nop.xlang.api.XLang.parseXpl(badRes, io.nop.xlang.ast.XLangOutputMode.none);

            Map<String, Object> state = new java.util.concurrent.ConcurrentHashMap<>();
            // first event batch: script throws - must be logged and swallowed, not propagated
            assertDoesNotThrow(() -> cmd.processEvents(badXpl,
                    java.util.Collections.emptyList(), state));

            // jboss-logmanager maps ERROR to its own Level instance; compare by intValue
            boolean errorLogged = captured.stream()
                    .anyMatch(r -> r.getLevel().intValue() >= java.util.logging.Level.SEVERE.intValue());
            assertTrue(errorLogged, "script failure must be visible as an ERROR log record");

            // a later event batch is still processed (loop-not-dead semantics)
            io.nop.core.resource.IResource goodRes =
                    io.nop.core.resource.ResourceHelper.resolveRelativePathResource(goodScript.toString());
            io.nop.xlang.api.XplModel goodXpl =
                    io.nop.xlang.api.XLang.parseXpl(goodRes, io.nop.xlang.ast.XLangOutputMode.none);
            assertDoesNotThrow(() -> cmd.processEvents(goodXpl,
                    java.util.Collections.emptyList(), state));
            boolean secondRunLogged = captured.stream()
                    .anyMatch(r -> r.getMessage() != null
                            && String.valueOf(r.getMessage()).contains("second-run-ok"));
            assertTrue(secondRunLogged, "a later event batch must still be processed after a failure");
        } finally {
            jbossLogger.removeHandler(handler);
            jbossLogger.setLevel(oldLevel);
        }
    }

    @Test
    void watchCommandBuildsWatcherWithoutContainerInjection() {
        CliWatchCommand cmd = new CliWatchCommand();
        CliWatchCommand.WatcherHandle handle = cmd.newFileWatcher();
        assertNotNull(handle);
        assertNotNull(handle.watcher, "watch must not NPE when no container factory is injected");
        assertTrue(handle.locallyOwned, "a locally created watcher is owned by the command");
        // NioFileWatchService uses a non-daemon thread; stop it so the test JVM can exit.
        handle.watcher.stop();
    }
}
