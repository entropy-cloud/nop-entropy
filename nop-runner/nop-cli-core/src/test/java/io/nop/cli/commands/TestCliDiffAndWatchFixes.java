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
