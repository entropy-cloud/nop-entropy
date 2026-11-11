package io.nop.bytecode.cli;

import io.nop.bytecode.NopBytecodeException;

/**
 * Per-input read-back byte source (plan 2564 W-14, adjudication D): the analysis read-back
 * phase reads each artifact's bytes once per input; JAR inputs open the underlying
 * {@link java.util.jar.JarFile} exactly once (mirroring the correct collectJar shape,
 * {@code ClassArtifactCollector#collectJar}) instead of once per artifact
 * (O(entries × central-directory) on fat jars).
 *
 * <p>Lifecycle: constructed once per input, closed via try-with-resources in the per-input
 * outer loop after the artifact inner loop finishes. Implementations wrap I/O failures in
 * {@link NopBytecodeException} (loud failure, exit 2).
 */
interface ClassByteSource extends AutoCloseable {

    /**
     * Read back the full content of the entry at the given input-relative path.
     *
     * @throws NopBytecodeException if the entry disappeared or is unreadable (TOCTOU surface,
     *                              loud failure)
     */
    byte[] read(String relative);

    @Override
    void close();
}
