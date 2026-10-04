package io.nop.treesitter.shell;

import java.util.List;

/**
 * A candidate file path mentioned by a script, with the two facts a consumer
 * cannot reconstruct from the string alone: whether it is read or written, and how
 * much is known about it.
 */
public final class ShellPath {

    /** Read or write. Redirect destinations are always writes. */
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
        /** From a {@link LiteralHarvest}: this script has other constructs this layer could not read. */
        HARVESTED
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
