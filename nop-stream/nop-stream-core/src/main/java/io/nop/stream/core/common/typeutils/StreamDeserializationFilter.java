/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.typeutils;

import java.io.ObjectInputFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_CLASS_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CLASS_NOT_ALLOWED;

/**
 * F-10a (plan 2026-09-04-1326-3): JEP 290 {@link ObjectInputFilter} for the native
 * Java-deserialization choke point ({@link JavaStreamSerializer}).
 *
 * <p>Checkpoint storage bytes cross a trust boundary (the storage write-access set is
 * wider than the operator set: shared JDBC storage, backup-restore, lateral movement),
 * and {@code MemoryStateSerDe.deserializeValue} routes arbitrary {@code byte[]} /
 * {@code __java_bytes__} payloads from snapshots into
 * {@code ObjectInputStream.readObject()} — previously with NO filter, the one path
 * bypassing the platform's {@code ClassNameValidator} whitelist discipline.
 *
 * <p><strong>Baseline whitelist</strong> (aligned with {@code ClassNameValidator.ALLOWED_PREFIXES}):
 * {@code io.nop.*}, {@code java.*}, {@code javax.*}, {@code jakarta.*} and array
 * descriptors of those. <strong>Escape hatch</strong>: user-declared state classes
 * outside the baseline are admitted by setting the system property
 * {@link #EXTRA_ALLOWED_PREFIXES_PROPERTY} to a comma-separated list of additional
 * class-name prefixes (e.g. {@code com.mycompany.stream.state.}) — user-defined
 * {@code IStreamSerializer} payloads with custom {@code Serializable} types restore
 * without touching code.
 *
 * <p>Rejections fail fast with the typed {@code ERR_STREAM_CLASS_NOT_ALLOWED} error
 * (never a raw {@code InvalidClassException} leaking to callers).
 */
public final class StreamDeserializationFilter {

    /**
     * System property declaring additional allowed class-name prefixes (comma-separated)
     * for native state deserialization — the documented compatibility escape hatch for
     * user-defined state classes outside the {@code io.nop.*} / JDK baseline.
     */
    public static final String EXTRA_ALLOWED_PREFIXES_PROPERTY =
            "nop.stream.state.deserialize.allowed-prefixes";

    private static final List<String> BASELINE_PREFIXES = List.of(
            "io.nop.",
            "java.",
            "javax.",
            "jakarta.",
            "[");

    private StreamDeserializationFilter() {
    }

    /**
     * Cached filter configuration: {@link #create()} runs for EVERY
     * Java-deserialization on the CEP state hot path. The filter was previously
     * rebuilt (lambda allocation + prefix-list merge) per call. The property is
     * still re-read on each call so tests and embedding processes can adjust the
     * escape hatch without restarting the JVM — the filter instance and prefix
     * list are only rebuilt when the property VALUE changes, which makes the
     * rebuild decision a pure function of the same input the old code used.
     */
    private static volatile CachedConfig cachedConfig;

    private static final class CachedConfig {
        final String propertyValue;
        final List<String> prefixes;
        final ObjectInputFilter filter;

        CachedConfig(String propertyValue, List<String> prefixes, ObjectInputFilter filter) {
            this.propertyValue = propertyValue;
            this.prefixes = prefixes;
            this.filter = filter;
        }
    }

    /**
     * Returns a filter with the baseline prefixes merged with the
     * {@link #EXTRA_ALLOWED_PREFIXES_PROPERTY} escape hatch (property re-read per
     * call; the filter instance is cached until the property value changes).
     */
    public static ObjectInputFilter create() {
        String extra = System.getProperty(EXTRA_ALLOWED_PREFIXES_PROPERTY, "");
        CachedConfig cached = cachedConfig;
        if (cached == null || !cached.propertyValue.equals(extra)) {
            List<String> allowed = allowedPrefixes(extra);
            cached = new CachedConfig(extra, allowed, buildFilter(allowed));
            cachedConfig = cached;
        }
        return cached.filter;
    }

    private static ObjectInputFilter buildFilter(List<String> allowed) {
        return filterInfo -> {
            Class<?> clazz = filterInfo.serialClass();
            if (clazz == null) {
                // stream-level values (depth/size/refs) — no class verdict
                return ObjectInputFilter.Status.UNDECIDED;
            }
            String name = clazz.getName();
            for (String prefix : allowed) {
                if (name.startsWith(prefix)) {
                    return ObjectInputFilter.Status.ALLOWED;
                }
            }
            return ObjectInputFilter.Status.REJECTED;
        };
    }

    /** Baseline + system-property-declared prefixes (trimmed, non-blank). */
    static List<String> allowedPrefixes() {
        return allowedPrefixes(System.getProperty(EXTRA_ALLOWED_PREFIXES_PROPERTY, ""));
    }

    /** Baseline merged with the given extra prefixes (trimmed, non-blank). */
    static List<String> allowedPrefixes(String extra) {
        if (extra == null || extra.isBlank()) {
            return BASELINE_PREFIXES;
        }
        List<String> merged = new ArrayList<>(BASELINE_PREFIXES);
        for (String token : extra.split(",")) {
            String prefix = token.trim();
            if (!prefix.isEmpty() && !merged.contains(prefix)) {
                merged.add(prefix);
            }
        }
        return merged;
    }

    /**
     * Typed rejection wrapper: converts the JEP 290 {@link InvalidClassException}
     * raised by a REJECTED class into {@code ERR_STREAM_CLASS_NOT_ALLOWED} with the
     * rejected class name and the escape-hatch migration hint.
     */
    static io.nop.api.core.exceptions.NopException rejected(Throwable cause, String contextType) {
        String message = String.valueOf(cause);
        return new StreamException(ERR_STREAM_CLASS_NOT_ALLOWED, cause)
                .param(ARG_CLASS_NAME, message)
                .param(ARG_DETAIL, "Native state deserialization rejected a class outside the "
                        + "allowed prefixes while restoring " + contextType
                        + ". If this is a user-defined state class, declare its package prefix "
                        + "via the system property " + EXTRA_ALLOWED_PREFIXES_PROPERTY
                        + " (comma-separated prefixes), or migrate the state to an "
                        + "io.nop.* / JDK type.");
    }

    /**
     * Test/embedding helper: validates a class name against the current filter
     * configuration — returns {@code true} when the class would be allowed.
     */
    static boolean isAllowed(String className, List<String> allowedPrefixes) {
        for (String prefix : allowedPrefixes) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    static List<String> baselinePrefixes() {
        return BASELINE_PREFIXES;
    }

    static List<String> parseExtraPrefixes(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
