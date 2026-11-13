package io.nop.lint.core.cli;

import io.nop.commons.concurrent.lock.LocalFileLock;
import io.nop.core.lang.json.JsonTool;
import io.nop.lint.core.NopLintException;
import io.nop.lint.core.engine.Diagnostic;
import io.nop.lint.core.node.SourceRange;
import io.nop.lint.core.rule.RuleDslModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The CI result cache behind {@code --cache} (roadmap item 43, design 11
 * §4): one JSON artifact mapping per-file fingerprints to replayable
 * diagnostics. A fingerprint = SHA-256(file bytes) scoped under a run key =
 * SHA-256(rule-set identity) ⊕ profile ⊕ fix mode ⊕ the sorted effective
 * {@code --rules} filter ⊕ the cache format version — any of these changing
 * invalidates the artifact (R1 Major 1: the --rules filter MUST be part of
 * the key, or a narrowed run silently replays a wider rule set's
 * diagnostics).
 *
 * <p>Fail-closed faces: a corrupt cache file (unparseable JSON, wrong
 * shape) aborts the run instead of being silently ignored and refilled;
 * the artifact write is atomic (unique temp file + atomic move, the temp
 * cleaned up on failure). v1 adjudications (plan): hits replay the
 * diagnostic stream only — engine stats for hit files are zero — and the
 * cache is mutually exclusive with the baseline and fix flows, whose
 * semantics cannot be served by replay.</p>
 *
 * <p>Cross-run mutual exclusion (plan 2531 W-03, adjudication 3): the
 * whole load→save read-modify-write cycle of one run is enclosed by a
 * {@link LocalFileLock} on a sibling lock file — acquired at
 * {@link #load}, released by {@link #save} (the release happens even when
 * the save fails). Concurrent runs sharing one {@code --cache} file are
 * serialized (cross-process: bounded poll then acquire; acquisition
 * failure is a fail-closed error, never a skipped save) so one run's
 * results can never be overwritten by another run's stale snapshot. A run
 * that abandons between load and save (process death, an abort) relies on
 * the CLI process domain: the OS releases the file lock when the process
 * exits, so no cross-run lock poison is possible. Within one JVM the grant
 * is thread-scoped: the run's own thread nests load→save freely, while a
 * second thread on the same cache file fails closed immediately — the
 * documented same-JVM behavior, never a silent crossed write.</p>
 */
public final class RuleResultCache {

    private static final Logger LOG = LoggerFactory.getLogger(RuleResultCache.class);

    private static final MessageDigest SHA256_PROTOTYPE = createSha256();

    private static MessageDigest createSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }


    /**
     * Bumped whenever the replay contract or engine semantics change in a
     * way that invalidates previously stored diagnostics.
     */
    public static final int FORMAT_VERSION = 1;

    /**
     * The lock acquisition wait (plan 2531 adjudication 3): a bounded poll
     * so a second CI process on the same cache file either follows the
     * first or fails closed long before a hung run could pile up waiters.
     */
    private static final int LOCK_POLL_INTERVAL_MS = 50;
    private static final int LOCK_TIMEOUT_MS = 10_000;

    /**
     * One cache file's live lock grant (plan 2531 adjudication 3, lock
     * lifecycle). Reentrancy is thread-scoped: the run's own thread nests
     * load→save freely, while a second JVM thread on the same file fails
     * closed immediately (the documented same-JVM face — never a silent
     * crossed write) and a second process waits on the bounded poll. All
     * registry access happens under {@code REGISTRY_MONITOR}.
     */
    private static final Object REGISTRY_MONITOR = new Object();
    private static final Map<Path, LockGrant> LOCK_GRANTS = new java.util.concurrent.ConcurrentHashMap<>();

    private static final class LockGrant {
        final LocalFileLock lock;
        Thread owner;
        int depth;

        LockGrant(LocalFileLock lock, Thread owner) {
            this.lock = lock;
            this.owner = owner;
            this.depth = 1; // the creating load takes the first grant level
        }
    }

    private final Path file;
    private final String runFingerprint;
    private final Map<String, Object> artifact;
    private final Path lockFile;

    private RuleResultCache(Path file, String runFingerprint, Map<String, Object> artifact,
                            Path lockFile) {
        this.file = file;
        this.runFingerprint = runFingerprint;
        this.artifact = artifact;
        this.lockFile = lockFile;
    }

    /**
     * Loads the artifact (or starts an empty one when absent) and validates
     * the run fingerprint. A corrupt file is a hard error — silently
     * refilling would hide an unusable cache from the operator (Rule #24).
     * The load takes this thread's grant on the cache file's lock; the grant
     * is held until the paired {@link #save} (or released immediately when
     * the load itself fails, so a failed load never poisons later runs).
     */
    public static RuleResultCache load(Path file, String runFingerprint) {
        Path lockFile = lockFileFor(file);
        acquireLockGrant(lockFile);
        try {
            return loadArtifact(file, runFingerprint, lockFile);
        } catch (RuntimeException | Error e) {
            // a failed load must not poison the lock for later runs
            // (plan 2531 adjudication 3, lock lifecycle)
            releaseLockGrant(lockFile);
            throw e;
        }
    }

    /**
     * Takes this thread's grant on the cache file's lock: the first grant
     * acquires the cross-process file lock (bounded poll, fail-closed on
     * timeout); a nested load on the same thread deepens its grant; a
     * different thread on an already-granted file fails closed.
     */
    private static void acquireLockGrant(Path lockFile) {
        synchronized (REGISTRY_MONITOR) {
            LockGrant grant = LOCK_GRANTS.get(lockFile);
            if (grant != null && grant.owner != Thread.currentThread()) {
                throw new NopLintException("another thread of this JVM holds the lint cache"
                        + " file lock '" + lockFile + "' (fail-closed: same-JVM concurrent"
                        + " access to one cache file is never a silent crossed write)");
            }
            if (grant != null) {
                grant.depth++;
                return;
            }
            LocalFileLock lock = new LocalFileLock(lockFile.toFile(), LOCK_POLL_INTERVAL_MS,
                    LOCK_TIMEOUT_MS);
            lock.lock();
            LOCK_GRANTS.put(lockFile, new LockGrant(lock, Thread.currentThread()));
        }
    }

    /**
     * Releases one level of this thread's grant; the outermost release
     * drops the cross-process file lock.
     */
    private static void releaseLockGrant(Path lockFile) {
        synchronized (REGISTRY_MONITOR) {
            LockGrant grant = LOCK_GRANTS.get(lockFile);
            if (grant == null || grant.owner != Thread.currentThread()) {
                throw new NopLintException("unpaired lint cache lock release for '" + lockFile
                        + "': no live grant on this thread (a caller bug must not pass silently)");
            }
            grant.depth--;
            if (grant.depth == 0) {
                LOCK_GRANTS.remove(lockFile);
                grant.lock.unlock();
            }
        }
    }

    private static RuleResultCache loadArtifact(Path file, String runFingerprint,
                                                Path lockFile) {
        if (!Files.exists(file)) {
            return new RuleResultCache(file, runFingerprint, newArtifact(runFingerprint), lockFile);
        }
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new NopLintException("cannot read lint cache file '" + file + "': "
                    + e.getMessage(), e);
        }
        Object parsed;
        try {
            parsed = JsonTool.parse(text);
        } catch (RuntimeException e) {
            throw new NopLintException("lint cache file '" + file
                    + "' is corrupt (not JSON); delete it or point --cache at a fresh file", e);
        }
        if (!(parsed instanceof Map)) {
            throw new NopLintException("lint cache file '" + file + "' is corrupt"
                    + " (not a JSON object)");
        }
        Map<String, Object> artifact = (Map<String, Object>) parsed;
        Object storedVersion = artifact.getOrDefault("version", -1);
        if (!(storedVersion instanceof Number)) {
            throw new NopLintException("corrupt cache file '" + file + "': the format"
                    + " version is missing or not a number (fail-closed)");
        }
        if (!Integer.valueOf(FORMAT_VERSION).equals(((Number) storedVersion).intValue())) {
            throw new NopLintException("lint cache file '" + file + "' has format version '"
                    + artifact.get("version") + "'; expected " + FORMAT_VERSION
                    + " (delete it or point --cache at a fresh file)");
        }
        if (!runFingerprint.equals(artifact.get("runFingerprint"))) {
            // different rules/profile/fix-mode/--rules: not reusable
            artifact = newArtifact(runFingerprint);
        }
        return new RuleResultCache(file, runFingerprint, artifact, lockFile);
    }

    /**
     * The lock file: a sibling of the cache file (same directory, one lock
     * per cache file, no nesting — no deadlock cycle).
     */
    private static Path lockFileFor(Path file) {
        return file.resolveSibling(file.getFileName() + ".lock");
    }

    private static Map<String, Object> newArtifact(String runFingerprint) {
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("version", FORMAT_VERSION);
        artifact.put("runFingerprint", runFingerprint);
        artifact.put("files", new LinkedHashMap<String, Object>());
        return artifact;
    }

    /**
     * The replay lookup: non-null when the file's content hash matches a
     * stored entry (the entry carries the replayable diagnostics).
     */
    @SuppressWarnings("unchecked")
    public CachedDiagnostics get(String path, byte[] fileBytes) {
        Map<String, Object> files = files(artifact);
        Object entry = files.get(path);
        if (!(entry instanceof Map)) {
            return null;
        }
        Map<String, Object> map = (Map<String, Object>) entry;
        if (!sha256(fileBytes).equals(map.get("hash"))) {
            return null;
        }
        Object raw = map.get("diagnostics");
        if (!(raw instanceof List)) {
            throw corrupt(path, "the diagnostics field is missing or not a list");
        }
        List<?> rawList = (List<?>) raw;
        List<Diagnostic> diagnostics = new ArrayList<>(rawList.size());
        for (Object item : rawList) {
            if (!(item instanceof Map)) {
                throw corrupt(path, "a diagnostic entry is not an object");
            }
            Map<?, ?> d = (Map<?, ?>) item;
            diagnostics.add(new Diagnostic(text(d, "ruleId", path), text(d, "severity", path),
                    text(d, "message", path),
                    new SourceRange(number(d, "startByte", path), number(d, "endByte", path))));
        }
        return new CachedDiagnostics(diagnostics);
    }

    private static NopLintException corrupt(String path, String detail) {
        return new NopLintException("cache entry corrupt for '" + path + "': " + detail
                + " (delete the artifact or re-run without --cache; fail-closed)");
    }

    private static String text(Map<?, ?> d, String key, String path) {
        Object value = d.get(key);
        if (!(value instanceof String)) {
            throw corrupt(path, "the diagnostic field '" + key + "' is missing or not a string");
        }
        return (String) value;
    }

    private static int number(Map<?, ?> d, String key, String path) {
        Object value = d.get(key);
        if (!(value instanceof Number)) {
            throw corrupt(path, "the diagnostic field '" + key + "' is missing or not a number");
        }
        return ((Number) value).intValue();
    }

    public void put(String path, byte[] fileBytes, List<Diagnostic> diagnostics) {
        Map<String, Object> files = files(artifact);
        List<Map<String, Object>> raw = new ArrayList<>(diagnostics.size());
        for (Diagnostic d : diagnostics) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("ruleId", d.ruleId());
            entry.put("severity", d.severity());
            entry.put("message", d.message());
            entry.put("startByte", d.range().startByte());
            entry.put("endByte", d.range().endByte());
            raw.add(entry);
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("hash", sha256(fileBytes));
        entry.put("diagnostics", raw);
        files.put(path, entry);
    }

    /**
     * Atomic write (unique temp file + atomic move — plan 2531 W-03/W-10,
     * adjudication 3) — a killed CI step must never leave a half-written
     * artifact, and two concurrent runs must never share one temp file name
     * (the fixed name let one writer's move empty the other's temp). A
     * failed write deletes its temp file and releases the lock (the release
     * happens even on failure — the lock is this run's load→save RMW
     * grant).
     */
    public void save() {
        try {
            Path temp = Files.createTempFile(file.toAbsolutePath().getParent(),
                    file.getFileName().toString(), ".tmp");
            boolean moved = false;
            try {
                Files.writeString(temp, JsonTool.serialize(artifact, false),
                        StandardCharsets.UTF_8);
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
                moved = true;
            } finally {
                if (!moved) {
                    try {
                        Files.deleteIfExists(temp);
                    } catch (IOException cleanupFailure) {
                        // the primary write failure is already propagating; a
                        // leftover temp file must not mask it, only be reported
                        LOG.warn("nop.lint.cache.temp-cleanup-failed:file={},temp={}", file,
                                temp, cleanupFailure);
                    }
                }
            }
        } catch (IOException e) {
            throw new NopLintException("cannot write lint cache file '" + file
                    + "': " + e.getMessage(), e);
        } finally {
            releaseLockGrant(lockFile);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> files(Map<String, Object> artifact) {
        return (Map<String, Object>) artifact.get("files");
    }

    /**
     * The run-level fingerprint: rule-set identity (sorted id + severity +
     * message digest), profile, fix mode and the effective --rules filter.
     */
    public static String runFingerprint(RuleSetLoader.LoadedRuleSet loaded, String profile,
                                        CliOptions.FixMode fixMode, List<String> rulesFilter) {
        StringBuilder sb = new StringBuilder();
        loaded.rulesByLanguage().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    sb.append(entry.getKey()).append(':');
                    for (RuleDslModel rule : entry.getValue()) {
                        sb.append(rule.getId()).append('|').append(rule.getSeverity())
                                .append('|').append(rule.getMessage()).append(';');
                    }
                });
        sb.append("#profile=").append(profile);
        sb.append("#fix=").append(fixMode);
        sb.append("#rules=").append(String.join(",", rulesFilter.stream().sorted().toList()));
        return sha256(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256(byte[] bytes) {
        return HexFormat.of().formatHex(prototypedDigest().digest(bytes));
    }

    /**
     * A fresh SHA-256 instance cloned from a warm prototype: MessageDigest is
     * stateful and not thread-safe, so it must never be shared, but the
     * per-call provider lookup is also avoidable (plan 08).
     */
    private static MessageDigest prototypedDigest() {
        try {
            return (MessageDigest) SHA256_PROTOTYPE.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("SHA-256 digest prototype cannot clone", e);
        }
    }

    /**
     * One cache hit's replayable payload.
     */
    public record CachedDiagnostics(List<Diagnostic> diagnostics) {
    }
}
