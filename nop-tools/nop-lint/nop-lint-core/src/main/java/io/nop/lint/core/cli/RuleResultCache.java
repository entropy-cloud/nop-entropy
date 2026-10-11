package io.nop.lint.core.cli;

import io.nop.core.lang.json.JsonTool;
import io.nop.lint.core.NopLintException;
import io.nop.lint.core.engine.Diagnostic;
import io.nop.lint.core.node.SourceRange;
import io.nop.lint.core.rule.RuleDslModel;

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
 * the artifact write is atomic (temp file + atomic move). v1 adjudications
 * (plan): hits replay the diagnostic stream only — engine stats for hit
 * files are zero — and the cache is mutually exclusive with the baseline
 * and fix flows, whose semantics cannot be served by replay.</p>
 */
public final class RuleResultCache {

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

    private final Path file;
    private final String runFingerprint;
    private final Map<String, Object> artifact;

    private RuleResultCache(Path file, String runFingerprint, Map<String, Object> artifact) {
        this.file = file;
        this.runFingerprint = runFingerprint;
        this.artifact = artifact;
    }

    /**
     * Loads the artifact (or starts an empty one when absent) and validates
     * the run fingerprint. A corrupt file is a hard error — silently
     * refilling would hide an unusable cache from the operator (Rule #24).
     */
    public static RuleResultCache load(Path file, String runFingerprint) {
        if (!Files.exists(file)) {
            return new RuleResultCache(file, runFingerprint, newArtifact(runFingerprint));
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
        return new RuleResultCache(file, runFingerprint, artifact);
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
     * Atomic write (temp file + atomic move) — a killed CI step must never
     * leave a half-written artifact.
     */
    public void save() {
        try {
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, JsonTool.serialize(artifact, false),
                    StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new NopLintException("cannot write lint cache file '" + file
                    + "': " + e.getMessage(), e);
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
