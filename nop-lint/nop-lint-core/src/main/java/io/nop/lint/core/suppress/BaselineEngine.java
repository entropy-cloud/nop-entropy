package io.nop.lint.core.suppress;

import io.nop.lint.core.engine.Diagnostic;
import io.nop.lint.core.node.SourceRange;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The baseline engine (roadmap item 27, design 09 §5): fingerprinting, the
 * per-file decision set, and the write/check primitives.
 *
 * <p><b>Fingerprint</b> (the plan's adjudication amending design 09 §5.1's
 * literal wording): sha256 over {@code ruleId} UTF-8 bytes, {@code 0x0A},
 * and the RAW SOURCE BYTE SLICE of the diagnostic's range. The range is
 * already a UTF-8 byte span, so the slice never round-trips through a
 * String — multi-byte content hashes correctly by construction — and byte
 * offsets are excluded from the hash, so line-number drift does not
 * invalidate an entry (content addressing).</p>
 *
 * <p><b>Decision set</b> (the fix-interaction adjudication): computed once
 * per file from the ORIGINAL content's lint, it acts as a stateless
 * predicate over every fix multipass sweep and the report lint — a
 * baseline-hit diagnostic never becomes a fix candidate at any pass, which
 * is exactly the "a suppressed diagnostic produces no fix" contract of
 * design 09 §6. The multi-set consumption ledger advances only against the
 * original lint (count decrements capped at the original occurrences); the
 * residual ("stale") entries are those the original lint did not fully
 * consume — the "基线只减不增" tightening signal.</p>
 */
public final class BaselineEngine {

    private BaselineEngine() {
    }

    /**
     * The content fingerprint of one diagnostic (see the class javadoc for
     * the exact byte layout).
     */
    public static String fingerprint(String ruleId, byte[] source, SourceRange range) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
        digest.update(ruleId.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0x0A);
        int start = Math.max(range.startByte(), 0);
        int end = Math.min(range.endByte(), source.length);
        if (end > start) {
            digest.update(source, start, end - start);
        }
        StringBuilder hex = new StringBuilder(digest.getDigestLength() * 2);
        for (byte b : digest.digest()) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }

    /**
     * Groups baseline entries by their canonical file path (the map key is
     * the {@link ExemptionFilter#normalize} form).
     */
    public static Map<String, List<BaselineFile.Entry>> byFile(List<BaselineFile.Entry> entries) {
        Map<String, List<BaselineFile.Entry>> byFile = new LinkedHashMap<>();
        for (BaselineFile.Entry entry : entries) {
            byFile.computeIfAbsent(entry.file(), key -> new ArrayList<>()).add(entry);
        }
        return byFile;
    }

    /**
     * The write primitive: one file's residual diagnostics become baseline
     * entries — same rule and fingerprint merge into one entry with an
     * occurrence count, in first-appearance order.
     */
    public static List<BaselineFile.Entry> writeEntries(String canonicalFile,
                                                        List<Diagnostic> diagnostics,
                                                        byte[] source) {
        Map<String, BaselineFile.Entry> merged = new LinkedHashMap<>();
        List<String> order = new ArrayList<>();
        for (Diagnostic diagnostic : diagnostics) {
            String fingerprint = fingerprint(diagnostic.ruleId(), source, diagnostic.range());
            String key = diagnostic.ruleId() + "|" + fingerprint;
            BaselineFile.Entry existing = merged.get(key);
            if (existing == null) {
                merged.put(key, new BaselineFile.Entry(diagnostic.ruleId(), canonicalFile,
                        fingerprint, 1));
                order.add(key);
            } else {
                merged.put(key, new BaselineFile.Entry(existing.rule(), existing.file(),
                        existing.fingerprint(), existing.count() + 1));
            }
        }
        List<BaselineFile.Entry> entries = new ArrayList<>(order.size());
        for (String key : order) {
            entries.add(merged.get(key));
        }
        return entries;
    }

    /**
     * The per-file decision set built from the ORIGINAL content's lint (see
     * the class javadoc).
     */
    public static FileBaseline compute(String canonicalFile, List<Diagnostic> originalDiagnostics,
                                       List<BaselineFile.Entry> entries, byte[] source) {
        // the multi-set ledger: rule|fingerprint -> remaining forgiveness
        Map<String, Integer> ledger = new HashMap<>();
        List<BaselineFile.Entry> keyedEntries = new ArrayList<>();
        if (entries != null) {
            for (BaselineFile.Entry entry : entries) {
                ledger.merge(entry.rule() + "|" + entry.fingerprint(), entry.count(), Integer::sum);
                keyedEntries.add(entry);
            }
        }

        Set<String> consumedKeys = new HashSet<>();
        int consumed = 0;
        for (Diagnostic diagnostic : originalDiagnostics) {
            String key = diagnostic.ruleId() + "|"
                    + fingerprint(diagnostic.ruleId(), source, diagnostic.range());
            Integer left = ledger.get(key);
            if (left != null && left > 0) {
                ledger.put(key, left - 1);
                consumedKeys.add(key);
                consumed++;
            }
        }

        List<BaselineFile.Entry> stale = new ArrayList<>();
        for (BaselineFile.Entry entry : keyedEntries) {
            int left = ledger.getOrDefault(entry.rule() + "|" + entry.fingerprint(), 0);
            if (left > 0) {
                stale.add(new BaselineFile.Entry(entry.rule(), entry.file(), entry.fingerprint(), left));
            }
        }
        return new FileBaseline(consumed, consumedKeys, stale);
    }

    /**
     * One file's baseline decision: how many original diagnostics the
     * baseline forgave, which rule/fingerprint pairs that covers (the
     * stateless predicate for every fix pass and the report lint), and
     * which entries the original content did not fully use (the
     * tightening signal for {@code --baseline-check}).
     */
    public static final class FileBaseline {

        private final int consumed;
        private final Set<String> consumedKeys;
        private final List<BaselineFile.Entry> stale;

        private FileBaseline(int consumed, Set<String> consumedKeys, List<BaselineFile.Entry> stale) {
            this.consumed = consumed;
            this.consumedKeys = Set.copyOf(consumedKeys);
            this.stale = List.copyOf(stale);
        }

        /**
         * The stateless predicate: true when the original lint forgave this
         * rule/fingerprint pair at least once. The decision is fixed by the
         * original lint — a suppressed key stays suppressed for the whole
         * run, at every fix pass and on the report lint alike.
         */
        public boolean suppresses(String ruleId, String fingerprint) {
            return consumedKeys.contains(ruleId + "|" + fingerprint);
        }

        /**
         * How many original diagnostics the baseline forgave.
         */
        public int consumed() {
            return consumed;
        }

        /**
         * The entries whose forgiveness the original content did not fully
         * use — the tightening signal for {@code --baseline-check}.
         */
        public List<BaselineFile.Entry> staleEntries() {
            return stale;
        }
    }
}
