package io.nop.ai.agent.conflict;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process {@link IWriteIntentRegistry} backed by a
 * {@link ConcurrentHashMap} keyed by normalized file path. Intents for the
 * same path are accumulated in a {@code CopyOnWriteArrayList}-style list
 * (an ordinary {@code ArrayList} guarded by the path's own lock, since the
 * atomicity region must cover both the insert and the conflicting-read).
 *
 * <p><b>Atomicity</b>: {@link #registerAndGetConflicting} synchronizes on
 * the per-path list object before reading the conflicting intents and
 * appending the new intent. Two concurrent registrations for the same
 * path therefore serialize on the same monitor: at least one of the two
 * callers will observe the other's intent in the returned set
 * (TOCTOU-safe).
 *
 * <p><b>Lifecycle</b>: intents accumulate per session and are removed in
 * bulk by {@link #releaseSession} when the session terminates. Released
 * lists are not deleted from the map (a path key may become empty but is
 * cheap to retain); this keeps the per-path monitor stable across
 * register/release cycles and avoids race conditions where a release
 * concurrently nulls out the list another thread is locking on.
 *
 * <p>Design {@code nop-ai-agent-multi-agent.md} §3.1 (Phase 1 simplified
 * in-process mechanism).
 */
public final class InMemoryWriteIntentRegistry implements IWriteIntentRegistry {

    private final Map<String, List<WriteIntent>> intentsByPath = new ConcurrentHashMap<>();

    @Override
    public Set<WriteIntent> registerAndGetConflicting(WriteIntent intent) {
        if (intent == null) {
            throw new IllegalArgumentException("intent must not be null");
        }
        String path = intent.getFilePath();
        if (path == null) {
            throw new IllegalArgumentException("intent.filePath must not be null");
        }
        // computeIfAbsent gives us a stable per-path list (our monitor).
        List<WriteIntent> bucket = intentsByPath.computeIfAbsent(path, k -> new ArrayList<>());

        Set<WriteIntent> conflicting;
        // The atomic region: read existing cross-session intents AND
        // append the new intent under the same lock. Without this, two
        // concurrent registrations could both observe an empty set and
        // both proceed (TOCTOU). Synchronizing on the bucket list keeps
        // contention per-path rather than global.
        synchronized (bucket) {
            conflicting = new HashSet<>();
            for (WriteIntent existing : bucket) {
                if (!sameSession(existing, intent)) {
                    conflicting.add(existing);
                }
            }
            bucket.add(intent);
        }
        return conflicting;
    }

    @Override
    public void releaseSession(String sessionId) {
        if (sessionId == null) {
            return;
        }
        // Remove this session's intents from every path bucket. Each
        // bucket is locked independently so a long release does not block
        // unrelated paths. We do not delete empty buckets (see class
        // javadoc) — the monitor must stay stable.
        for (List<WriteIntent> bucket : intentsByPath.values()) {
            synchronized (bucket) {
                bucket.removeIf(i -> sessionId.equals(i.getSessionId()));
            }
        }
    }

    /**
     * Test/diagnostic accessor: the set of currently-registered intents for
     * a given path (across all sessions). Returns an unmodifiable snapshot.
     */
    public Set<WriteIntent> getIntentsForPath(String path) {
        if (path == null) {
            return Collections.emptySet();
        }
        List<WriteIntent> bucket = intentsByPath.get(path);
        if (bucket == null) {
            return Collections.emptySet();
        }
        synchronized (bucket) {
            return Collections.unmodifiableSet(new HashSet<>(bucket));
        }
    }

    /**
     * Test/diagnostic accessor: {@code true} when no intents are currently
     * registered for any path. Used by lifecycle tests to assert that
     * session release cleared all intents.
     */
    public boolean isEmpty() {
        for (List<WriteIntent> bucket : intentsByPath.values()) {
            synchronized (bucket) {
                if (!bucket.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean sameSession(WriteIntent a, WriteIntent b) {
        String sa = a.getSessionId();
        String sb = b.getSessionId();
        if (sa == null || sb == null) {
            return false;
        }
        return sa.equals(sb);
    }
}
