package io.nop.cli.commands;

import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.watch.FileWatcher;
import io.nop.core.resource.watch.NioFileWatchService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lifecycle carrier for {@link CliWatchCommand} (plan 2563 MA-02, C10-004 leg 1). Pins the
 * correct fix shape: the ICancellable returned by watcher.watch() is cancelled on every exit
 * path (null-guarded), while the injected (shared) watch service is NOT stopped - the
 * locallyOwned stop() is the only service-level cleanup (CL-011 footgun interception leg).
 *
 * <p>Test seams (design R1-M6): stdin EOF via System.setIn (restored in finally, R2-m2);
 * watchKeyMap is private in NioFileWatchService (cross-module) - asserted via reflection
 * (registered seam).
 */
public class TestCliWatchCommandLifecycle {

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

    private NioFileWatchService ownedService;
    private final List<AutoCloseable> cleanups = new java.util.ArrayList<>();

    @AfterEach
    void tearDown() {
        for (AutoCloseable c : cleanups) {
            try {
                c.close();
            } catch (Exception e) {
                // best-effort test cleanup
            }
        }
        cleanups.clear();
    }

    static class RecordingFileWatcher extends FileWatcher {
        final AtomicInteger watchCalls = new AtomicInteger();
        volatile Cancellable lastRegistration;
        volatile boolean stopCalled;

        RecordingFileWatcher(NioFileWatchService service) {
            super(service, GlobalExecutors.globalTimer());
        }

        @Override
        public io.nop.api.core.util.ICancellable watch(List<String> paths, List<String> fileNamePatterns,
                                                       int debounce,
                                                       java.util.function.Consumer<java.util.Collection<io.nop.core.resource.watch.FileChangeEvent>> handler) {
            watchCalls.incrementAndGet();
            io.nop.api.core.util.ICancellable registration = super.watch(paths, fileNamePatterns, debounce, handler);
            this.lastRegistration = (Cancellable) registration;
            return registration;
        }

        @Override
        public void stop() {
            stopCalled = true;
            super.stop();
        }
    }

    private int watchKeyMapSize(NioFileWatchService service) throws Exception {
        Field field = NioFileWatchService.class.getDeclaredField("watchKeyMap");
        field.setAccessible(true);
        Map<?, ?> map = (Map<?, ?>) field.get(service);
        return map.size();
    }

    /**
     * Injected factory + stdin EOF: registration cancelled, watchKeyMap falls back to empty,
     * and the shared watch service is NOT stopped (only locallyOwned services are stopped).
     */
    @Test
    void injectedPathCancelsRegistrationAndKeepsSharedServiceAlive() throws Exception {
        ownedService = new NioFileWatchService();
        ownedService.start();
        RecordingFileWatcher watcher = new RecordingFileWatcher(ownedService);

        Path watchDir = Files.createDirectory(tempDir.resolve("watched"));
        Path script = tempDir.resolve("noop.xpl");
        Files.writeString(script, "<c:log info=\"noop\"/>");

        CliWatchCommand cmd = new CliWatchCommand();
        cmd.watchDirs = new File[]{watchDir.toFile()};
        cmd.scriptFile = script.toString();
        cmd.debounceWait = 10;
        cmd.fileWatcherFactory = new FileWatcherFactory() {
            @Override
            public FileWatcher newFileWatcher() {
                return watcher;
            }
        };

        assertEquals(0, watchKeyMapSize(ownedService), "no registration before watch");

        InputStream originalIn = System.in;
        try {
            System.setIn(new java.io.ByteArrayInputStream(new byte[0])); // immediate EOF
            Integer exit = cmd.call();
            assertEquals(0, exit);
        } finally {
            System.setIn(originalIn); // R2-m2: restore stdin so other tests are not polluted
        }

        assertEquals(1, watcher.watchCalls.get(), "watch() registered once");
        assertNotNull(watcher.lastRegistration, "watch() returned a registration");
        assertTrue(watcher.lastRegistration.isCancelled(),
                "the ICancellable from watch() must be cancelled on exit (MA-02)");
        assertEquals(0, watchKeyMapSize(ownedService),
                "watchKeyMap must fall back to empty after cancel (registration removed)");
        assertFalse(watcher.stopCalled,
                "shared (injected) watch service must NOT be stopped by the command (CL-011 footgun leg)");

        // the service is still usable after the command finished
        assertTrue(ownedService.isActive(), "shared service must still be running");
    }

    /**
     * Script missing: watch() is never reached, registration is null - the null guard in the
     * finally block must not NPE (R2-m4), and the command still fails loudly.
     */
    @Test
    void missingScriptExitsLoudlyWithoutRegistrationNpe() throws Exception {
        ownedService = new NioFileWatchService();
        ownedService.start();
        RecordingFileWatcher watcher = new RecordingFileWatcher(ownedService);

        Path watchDir = Files.createDirectory(tempDir.resolve("watched2"));

        CliWatchCommand cmd = new CliWatchCommand();
        cmd.watchDirs = new File[]{watchDir.toFile()};
        cmd.scriptFile = tempDir.resolve("missing.xpl").toString();
        cmd.fileWatcherFactory = new FileWatcherFactory() {
            @Override
            public FileWatcher newFileWatcher() {
                return watcher;
            }
        };

        InputStream originalIn = System.in;
        try {
            System.setIn(new java.io.ByteArrayInputStream(new byte[0]));
            org.junit.jupiter.api.Assertions.assertThrows(Exception.class, cmd::call);
        } finally {
            System.setIn(originalIn);
        }

        assertEquals(0, watcher.watchCalls.get(), "watch() must not be called when the script is missing");
        assertFalse(watcher.stopCalled);
    }
}
