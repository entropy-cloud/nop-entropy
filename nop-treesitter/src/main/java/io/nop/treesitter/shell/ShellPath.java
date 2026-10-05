package io.nop.treesitter.shell;

import java.util.List;

/**
 * A candidate file path mentioned by a script, with the two facts a consumer
 * cannot reconstruct from the string alone: whether it is read or written, and how
 * much is known about it.
 */
public final class ShellPath {

    /**
     * Read or write.
     *
     * <p>For a redirect destination this is the redirect's own direction: {@code >}
     * and {@code >>} write, {@code <} only ever reads. For a command <em>argument</em>
     * it is a uniform {@link #READ}, because whether an argument is read or written
     * is a property of the command's semantics, not of its text — {@code tee
     * /tmp/out} writes and {@code cat /tmp/out} reads, and this capability extracts
     * literals rather than judging intent.</p>
     */
    public enum Access {
        READ,
        WRITE
    }

    /**
     * Where the path came from, and therefore how much is known about it. Mixing
     * the two is a safety problem, not a cosmetic one: a path from a partial
     * harvest is not a path the script provably touches.
     */
    public enum Confidence {
        /** From a {@link ProvenCommandSequence}: the script is fully literal and this path is one it names. */
        PROVEN,
        /**
         * From a {@link LiteralHarvest} whose argv was read in full, on a script that has
         * other constructs this layer could not read.
         */
        HARVESTED,
        /**
         * From a {@link LiteralHarvest} command marked partial: at least one argument could
         * not be read literally, so the reported argv is known to be a <em>subset</em> and this
         * path is a weaker claim than {@link #HARVESTED} — the command may well name more paths
         * that are not in the list at all.
         */
        HARVESTED_PARTIAL
    }

    private final String path;
    private final Access access;
    private final Confidence confidence;

    ShellPath(String path, Access access, Confidence confidence) {
        this.path = path;
        this.access = access;
        this.confidence = confidence;
    }

    public String path() {
        return path;
    }

    public Access access() {
        return access;
    }

    public Confidence confidence() {
        return confidence;
    }

    /** Both fields, so the record survives a GraphQL boundary. */
    public List<String> describe() {
        return List.of(path, access.name(), confidence.name());
    }

    @Override
    public String toString() {
        return path + "[" + access + "/" + confidence + "]";
    }
}
