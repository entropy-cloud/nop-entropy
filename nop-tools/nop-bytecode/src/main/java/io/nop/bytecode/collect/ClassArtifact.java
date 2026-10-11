package io.nop.bytecode.collect;

import java.util.Objects;

/**
 * One class file artifact discovered by the collection layer.
 *
 * @param relativePath artifact identity within its input: path relative to the input directory,
 *                     or {@code <jarPath>!<entryName>} for jar entries
 * @param className    fully qualified class name parsed from the class file (binary name with
 *                     {@code .} separators); {@code module-info} for module descriptors
 * @param majorVersion class file major version (61 = Java 17, 65 = Java 21)
 * @param size         class file size in bytes
 * @param sha256       hex-encoded SHA-256 of the class file bytes
 */
public final class ClassArtifact {
    private final String relativePath;
    private final String className;
    private final int majorVersion;
    private final long size;
    private final String sha256;

    public ClassArtifact(String relativePath, String className, int majorVersion, long size, String sha256) {
        this.relativePath = Objects.requireNonNull(relativePath, "relativePath");
        this.className = Objects.requireNonNull(className, "className");
        this.majorVersion = majorVersion;
        this.size = size;
        this.sha256 = Objects.requireNonNull(sha256, "sha256");
    }

    public String relativePath() {
        return relativePath;
    }

    public String className() {
        return className;
    }

    public int majorVersion() {
        return majorVersion;
    }

    public long size() {
        return size;
    }

    public String sha256() {
        return sha256;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClassArtifact)) return false;
        ClassArtifact that = (ClassArtifact) o;
        return majorVersion == that.majorVersion && size == that.size
                && relativePath.equals(that.relativePath) && className.equals(that.className)
                && sha256.equals(that.sha256);
    }

    @Override
    public int hashCode() {
        return relativePath.hashCode();
    }

    @Override
    public String toString() {
        return "ClassArtifact[" + relativePath + ", " + className + ", v" + majorVersion + "]";
    }
}
