package io.nop.ai.agent.security;

import io.nop.commons.crypto.HashHelper;
import io.nop.commons.util.StringHelper;

import java.util.Map;
import java.util.TreeMap;

/**
 * Deterministic SHA-256-based fingerprint of an action's dangerous intent
 * (design §6.2/§6.3): {@code action_fingerprint = SHA-256(actionKind + argv +
 * cwd + criticalEnv)[:32]}.
 *
 * <p>Used by {@link IPostDenialGuard} implementations (e.g.
 * {@link FingerprintPostDenialGuard}) to detect blind retries — two actions
 * with the same actionKind, arguments, working directory, and critical
 * environment produce the same fingerprint and are therefore treated as the
 * same (already-denied) intent. A legitimate follow-up (changed parameters,
 * lower privileges, narrower approval scope) naturally produces a different
 * fingerprint and is therefore not blocked by exact-fingerprint matching.
 *
 * <p><b>Determinism</b>: the fingerprint is computed from a canonical
 * serialization of the inputs, so identical inputs always produce identical
 * fingerprints regardless of {@code Map} insertion order. The arguments and
 * criticalEnv maps are sorted by key ({@link TreeMap}) before being rendered
 * via {@code toString()}, so {@code {b=2,a=1}} and {@code {a=1,b=2}} produce
 * the same fingerprint.
 *
 * <p><b>Truncation</b>: the SHA-256 digest is hex-encoded and truncated to
 * 32 hex characters (128 bits). The collision probability at agent-session
 * scale is negligible (design §6.3 non-goal — fingerprint collision handling
 * is a deferred successor).
 *
 * <p><b>Null safety</b>: null {@code arguments}, {@code workDir}, or
 * {@code criticalEnv} are uniformly treated as empty strings, so a missing
 * component does not cause a failure and consistently maps to a stable
 * fingerprint.
 *
 * <p>This is a pure value type — stateless, side-effect-free, deterministic.
 */
public final class ActionFingerprint {

    /** The fixed length of the fingerprint hex string (32 hex chars = 128 bits). */
    public static final int FINGERPRINT_HEX_LENGTH = 32;

    private final String value;

    private ActionFingerprint(String value) {
        this.value = value;
    }

    /**
     * Compute the deterministic fingerprint of an action's dangerous intent.
     *
     * @param actionKind    the tool name / operation category (e.g.
     *                      {@code "shell.exec"}); may be null (treated as
     *                      empty string)
     * @param arguments     the tool-call arguments map; may be null or empty
     *                      (treated as empty string). Keys are sorted
     *                      canonically so insertion order does not affect the
     *                      fingerprint.
     * @param workDir       the working directory; may be null (treated as
     *                      empty string)
     * @param criticalEnv   the critical environment variables map; may be null
     *                      or empty (treated as empty string). Keys are sorted
     *                      canonically.
     * @return the fingerprint value type wrapping the 32-hex-char digest
     */
    public static ActionFingerprint compute(String actionKind, Map<String, Object> arguments,
                                            String workDir, Map<String, String> criticalEnv) {
        String kind = actionKind != null ? actionKind : "";
        String canonicalArgs = canonicalMap(arguments);
        String dir = workDir != null ? workDir : "";
        String canonicalEnv = canonicalStringMap(criticalEnv);

        String material = kind + "|" + canonicalArgs + "|" + dir + "|" + canonicalEnv;
        byte[] digest = HashHelper.sha256(
                StringHelper.utf8Bytes(material), null);
        String hex = StringHelper.bytesToHex(digest);
        if (hex.length() > FINGERPRINT_HEX_LENGTH) {
            hex = hex.substring(0, FINGERPRINT_HEX_LENGTH);
        }
        return new ActionFingerprint(hex);
    }

    /**
     * Wrap an already-computed fingerprint hex string. Used when the
     * fingerprint value is carried through from another source (e.g. stored
     * in a {@link DenialResult}).
     *
     * @param value the fingerprint hex string; may be null
     */
    public static ActionFingerprint of(String value) {
        return new ActionFingerprint(value);
    }

    private static String canonicalMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        return new TreeMap<>(map).toString();
    }

    private static String canonicalStringMap(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        return new TreeMap<>(map).toString();
    }

    /**
     * @return the 32-hex-char fingerprint value; never null for a
     *         {@link #compute}-produced instance, may be null for an
     *         {@link #of}-wrapped instance
     */
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ActionFingerprint that = (ActionFingerprint) o;
        return java.util.Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return "ActionFingerprint{" + value + '}';
    }
}
