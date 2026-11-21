package io.nop.ib.tasks;

import io.nop.commons.path.SegmentGlobPathMatcher;
import io.nop.ib.NopIbException;

import java.util.regex.Pattern;

/**
 * Module-relative glob matching for task inputs/outputs (design 08 §二).
 * Supports {@code *} (within a segment), {@code ?} (one char) and
 * {@code **} (across directory segments). Paths use '/' separators.
 *
 * <p>Matching engine migrated to the platform
 * {@link SegmentGlobPathMatcher} (analysis
 * 2026-11-22-nop-ib-nop-git-platform-reuse); this class keeps ib's config-error
 * contract: a bad glob is a {@link NopIbException} (exit 3), never a bare
 * IllegalArgumentException.
 *
 * <p>Expansion granularity (design 08 / wave 12): outputs expand to matching
 * <em>files</em> only — directories never enter the fingerprint set, so the
 * fingerprint state is a file collection.
 */
public final class GlobMatcher {
    private static final SegmentGlobPathMatcher MATCHER = SegmentGlobPathMatcher.instance();

    private GlobMatcher() {
    }

    /** Compiles and validates eagerly; a bad glob is a config error (fail-loud). */
    public static Pattern compile(String glob) {
        try {
            return MATCHER.compile(glob);
        } catch (IllegalArgumentException e) {
            throw new NopIbException(e.getMessage());
        }
    }

    public static boolean matches(Pattern compiled, String relPath) {
        return MATCHER.matches(compiled, relPath);
    }
}
