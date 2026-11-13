package io.nop.ai.tools.sequential_thinking.service;

import io.nop.ai.tools.sequential_thinking.model.ThoughtData;
import io.nop.ai.tools.sequential_thinking.model.ThoughtStage;
import io.nop.commons.util.FileHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2557 C6-007 (ruling E) carrier: ThoughtStorage lifecycle semantics.
 *
 * <ul>
 * <li>clearHistory deletes the session file outright ({@code
 * Files.deleteIfExists}) — storage returns to the pre-session baseline
 * (existing "clear keeps file" assertions updated to delete semantics,
 * C10-021 sanctioned).</li>
 * <li>TTL sweep hosted at the saveSession entry with once-per-date throttle:
 * expired session files are swept on the first save of a calendar date when
 * {@code nop.ai.tools.thought-retention-days} > 0; default 0 = off.</li>
 * </ul>
 */
public class TestThoughtStorageRetention {

    private File tempDir;

    @AfterEach
    public void tearDown() throws Exception {
        if (tempDir != null && tempDir.exists()) {
            FileHelper.deleteAll(tempDir);
        }
    }

    private File newTempDir() throws Exception {
        tempDir = Files.createTempDirectory("nop-ai-thought-retention").toFile();
        return tempDir;
    }

    private ThoughtData newThought(String thought) {
        ThoughtData data = new ThoughtData();
        data.setThought(thought);
        data.setThoughtNumber(1);
        data.setTotalThoughts(1);
        data.setStage(ThoughtStage.ANALYSIS);
        return data;
    }

    private void makeStale(File file, long ageMillis) throws Exception {
        long stale = System.currentTimeMillis() - ageMillis;
        assertTrue(file.setLastModified(stale), "setLastModified must succeed for test setup: " + file);
    }

    private static final long TWO_DAYS_MILLIS = 2L * 24L * 3600L * 1000L;

    @Test
    public void testClearHistoryDeletesSessionFileBackToBaseline() throws Exception {
        File dir = newTempDir();
        ThoughtStorage storage = new ThoughtStorage(dir.getAbsolutePath());

        storage.addThought("s-clear", newThought("x"));
        File sessionFile = new File(dir, "s-clear.json");
        assertTrue(sessionFile.exists(), "precondition: session file exists before clear");

        storage.clearHistory("s-clear");

        assertFalse(sessionFile.exists(), "clearHistory must delete the session file (ruling E)");
        assertTrue(storage.getAllThoughts("s-clear").isEmpty(), "cleared session reads back empty");

        // clearing a session that never existed is a no-op, not an error
        storage.clearHistory("never-existed");
    }

    @Test
    public void testClearHistoryIsIdempotent() throws Exception {
        File dir = newTempDir();
        ThoughtStorage storage = new ThoughtStorage(dir.getAbsolutePath());
        storage.addThought("s-idem", newThought("x"));
        storage.clearHistory("s-idem");
        storage.clearHistory("s-idem");
        assertFalse(new File(dir, "s-idem.json").exists());
    }

    @Test
    public void testTtlSweepDeletesExpiredSessionFilesOnSave() throws Exception {
        File dir = newTempDir();
        ThoughtStorage storage = new ThoughtStorage(dir.getAbsolutePath());
        storage.setThoughtRetentionDays(1);

        File expired = new File(dir, "expired-session.json");
        Files.writeString(expired.toPath(), "[]");
        makeStale(expired, TWO_DAYS_MILLIS);
        File fresh = new File(dir, "fresh-session.json");
        Files.writeString(fresh.toPath(), "[]");

        // saveSession entry triggers the once-per-date sweep
        storage.addThought("current", newThought("x"));

        assertFalse(expired.exists(), "expired session file must be swept by the TTL sweep");
        assertTrue(fresh.exists(), "fresh session file must survive the TTL sweep");
        assertEquals(1, storage.getAllThoughts("current").size(), "the triggering save must still succeed");
    }

    @Test
    public void testTtlSweepThrottledOncePerDate() throws Exception {
        File dir = newTempDir();
        ThoughtStorage storage = new ThoughtStorage(dir.getAbsolutePath());
        storage.setThoughtRetentionDays(1);

        // first save triggers today's sweep
        storage.addThought("trigger", newThought("x"));

        // a file that becomes stale only AFTER today's sweep must stay until
        // the next calendar date (once-per-date throttle)
        File staleAfterSweep = new File(dir, "stale-after-sweep.json");
        Files.writeString(staleAfterSweep.toPath(), "[]");
        makeStale(staleAfterSweep, TWO_DAYS_MILLIS);

        storage.addThought("trigger", newThought("y"));

        assertTrue(staleAfterSweep.exists(),
                "once-per-date throttle: no second sweep on the same date");
    }

    @Test
    public void testRetentionDisabledByDefaultKeepsStaleFiles() throws Exception {
        File dir = newTempDir();
        ThoughtStorage storage = new ThoughtStorage(dir.getAbsolutePath());

        File stale = new File(dir, "stale-session.json");
        Files.writeString(stale.toPath(), "[]");
        makeStale(stale, TWO_DAYS_MILLIS);

        storage.addThought("current", newThought("x"));

        assertTrue(stale.exists(), "retention days = 0 (default off) must not sweep anything");
    }
}
