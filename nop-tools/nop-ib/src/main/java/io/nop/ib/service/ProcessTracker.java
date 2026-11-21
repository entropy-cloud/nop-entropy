package io.nop.ib.service;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * plan 2588 running-cancel support (design 09 §三): every child process the
 * service spawns for a repo root is registered here while alive — the three
 * process sources are MavenFallback delegation, JUnitForkExecutor batches and
 * JUnitWorkerPool resident workers. Keyed by canonical repo root (a lane runs
 * one job at a time, so every live process of a root belongs to its running
 * job). Cancel walks the whole tree: Java's {@code destroyForcibly} only kills
 * the DIRECT child, but surefire forks grand-children — those are reached via
 * {@code ProcessHandle#descendants()} before the direct kill.
 */
public final class ProcessTracker {

    private ProcessTracker() {
    }

    /** canonical root → live processes (concurrent set per root). */
    private static final Map<Path, Set<Process>> LIVE = new ConcurrentHashMap<>();

    public static void register(Path root, Process process) {
        LIVE.computeIfAbsent(root, k -> ConcurrentHashMap.newKeySet()).add(process);
    }

    public static void unregister(Path root, Process process) {
        Set<Process> set = LIVE.get(root);
        if (set != null)
            set.remove(process);
    }

    /**
     * Tree-kills every live registered process of {@code root}: descendants
     * first (destroyForcibly on each), then the process itself. Returns how
     * many direct processes were killed.
     */
    public static int killAll(Path root) {
        Set<Process> set = LIVE.get(root);
        if (set == null)
            return 0;
        int killed = 0;
        for (Process p : set) {
            set.remove(p);
            if (!p.isAlive())
                continue;
            p.descendants().forEach(ProcessHandle::destroyForcibly);
            p.destroyForcibly();
            killed++;
        }
        return killed;
    }
}
