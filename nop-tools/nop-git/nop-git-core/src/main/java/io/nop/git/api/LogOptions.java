package io.nop.git.api;

/**
 * Options for {@link IGitRepositoryService#log(LogOptions)}, mirroring the
 * {@code git log} shapes used by AI tools ({@code --oneline}, {@code -n}).
 *
 * @param rev       start revision, defaults to {@code HEAD}
 * @param maxCount  maximum number of commits, or -1 for unlimited
 */
public record LogOptions(String rev, int maxCount) {

    public static LogOptions defaults() {
        return new LogOptions("HEAD", -1);
    }

    public static LogOptions maxCount(int maxCount) {
        return new LogOptions("HEAD", maxCount);
    }
}
