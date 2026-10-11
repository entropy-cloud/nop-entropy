package io.nop.bytecode.collect;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One collection input: either a class directory (Maven reactor {@code target/classes} layout)
 * or a jar file. The collection layer never compiles anything; a missing input is a loud
 * failure ({@link MissingInputException}), never a silent skip.
 */
public final class CollectInput {
    private final Path path;
    private final Kind kind;

    public enum Kind { DIRECTORY, JAR }

    private CollectInput(Path path, Kind kind) {
        this.path = Objects.requireNonNull(path, "path");
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    public static CollectInput directory(Path path) {
        return new CollectInput(path, Kind.DIRECTORY);
    }

    public static CollectInput jar(Path path) {
        return new CollectInput(path, Kind.JAR);
    }

    public Path path() {
        return path;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public String toString() {
        return kind + ":" + path;
    }
}
