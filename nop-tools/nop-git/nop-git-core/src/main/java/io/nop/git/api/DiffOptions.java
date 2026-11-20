package io.nop.git.api;

/**
 * Options for {@link IGitRepositoryService#diff(DiffOptions)}, mirroring the
 * {@code git diff} invocation shapes used by AI tools.
 *
 * @param cached when true, diff HEAD against the index ({@code git diff --cached})
 * @param rev    when non-null, diff the given rev against the working tree
 *               ({@code git diff <rev>}); ignored when cached is true
 * @param stat   when true the consumer wants diffstat semantics
 *               ({@code git diff --stat}); counts are always computed
 */
public record DiffOptions(boolean cached, String rev, boolean stat) {

    public static DiffOptions forCached() {
        return new DiffOptions(true, null, false);
    }

    public static DiffOptions against(String rev) {
        return new DiffOptions(false, rev, false);
    }

    public static DiffOptions worktree() {
        return new DiffOptions(false, null, false);
    }
}
