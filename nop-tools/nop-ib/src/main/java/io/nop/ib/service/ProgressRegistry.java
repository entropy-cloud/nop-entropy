package io.nop.ib.service;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

/**
 * plan 2588: in-process progress facts keyed by repo root (design 09 §三).
 * Writers are the CLI command threads (TestCommand reports modulesDone /
 * modulesTotal on both main dispatch paths; CompileCommand reports the compile
 * selection size); readers assemble job snapshots and long-poll intermediate
 * states. Memory face is O(roots): entries are overwritten by the root's next
 * build and never cleared per job. Every update wakes the long-poll waiters of
 * that root (the second new wakeup point next to QUEUED→RUNNING).
 */
public final class ProgressRegistry {

    private ProgressRegistry() {
    }

    /** root → latest progress snapshot (volatile map semantics). */
    private static final Map<Path, Map<String, Object>> PROGRESS = new ConcurrentHashMap<>();

    /** root → jobs whose long-poll wait must be woken on progress updates. */
    private static final Map<Path, List<BuildJob>> WATCHERS = new ConcurrentHashMap<>();

    /** long-poll registration; jobs register at submit and leave at terminal. */
    static void watch(Path root, BuildJob job) {
        WATCHERS.computeIfAbsent(root, k -> new CopyOnWriteArrayList<>()).add(job);
    }

    static void unwatch(BuildJob job) {
        List<BuildJob> list = WATCHERS.get(job.repoRoot);
        if (list != null)
            list.remove(job);
    }

    /**
     * Publishes a progress snapshot for {@code root} (whole-map replace, atomic)
     * and wakes every long-poll waiter of that root.
     */
    public static void update(Path root, Map<String, Object> snapshot) {
        PROGRESS.put(root, snapshot);
        List<BuildJob> list = WATCHERS.get(root);
        if (list != null)
            for (BuildJob job : list) {
                synchronized (job) {
                    job.notifyAll();
                }
            }
    }

    /** latest progress facts for {@code root}, or null when none published. */
    public static Map<String, Object> get(Path root) {
        return PROGRESS.get(root);
    }
}
