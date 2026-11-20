package io.nop.git.api;

/**
 * {@code git clean} options.
 *
 * @param force        remove files for real ({@code -f}); without it only
 *                     dry-run listing happens ({@code -n})
 * @param directories  also remove untracked directories ({@code -d})
 * @param dryRun       explicit {@code -n}: list what would be removed
 */
public record CleanOptions(boolean force, boolean directories, boolean dryRun) {

    public static CleanOptions dryRun(boolean directories) {
        return new CleanOptions(false, directories, true);
    }

    public static CleanOptions remove(boolean directories) {
        return new CleanOptions(true, directories, false);
    }
}
