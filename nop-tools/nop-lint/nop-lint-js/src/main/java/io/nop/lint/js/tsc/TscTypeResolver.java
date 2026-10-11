package io.nop.lint.js.tsc;

import io.nop.lint.core.semantic.TypeResolutionException;
import io.nop.lint.core.semantic.TypeResolver;

import java.nio.file.Path;
import java.util.Objects;

/**
 * The L2 {@link TypeResolver} backed by the resident tsc bridge (roadmap
 * item 20, design 06 §5.3 方案 A): adapts the engine's resolver contract to
 * {@link NodeTscBridge}'s process protocol. The bridge starts lazily —
 * {@link #isAvailable()} probes the environment without spawning, and the
 * Node process exists only from the first real query (design 11 §3).
 *
 * <p>Every bridge failure maps to {@link TypeResolutionException} so the
 * engine can degrade the querying rule: a structured query failure
 * ({@link TscQueryException}) and an unavailable environment ({@link
 * TscBridgeUnavailableException}) are both degrade signals, never reasons
 * to fabricate a type-level answer. The resolver owns the bridge lifecycle;
 * callers that lint in bulk should {@link #close()} when done.</p>
 */
public final class TscTypeResolver implements TypeResolver, AutoCloseable {

    private final TscBridgeConfig config;
    private final Path defaultTsConfigPath;
    private final Object lock = new Object();

    private NodeTscBridge bridge;
    private boolean projectInitialized;

    /**
     * @param config the bridge environment (spawn command, helper script,
     *               budgets)
     */
    public TscTypeResolver(TscBridgeConfig config) {
        this(config, null);
    }

    /**
     * @param config              the bridge environment
     * @param defaultTsConfigPath the project queries bind to when the caller
     *                            has not called {@link #initProject(Path)};
     *                            null forces explicit initialization
     */
    public TscTypeResolver(TscBridgeConfig config, Path defaultTsConfigPath) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.defaultTsConfigPath = defaultTsConfigPath;
    }

    @Override
    public boolean isAvailable() {
        NodeTscBridge current = bridge;
        if (current != null) {
            return current.isAvailable();
        }
        // No process yet: the probe must stay side-effect free (design 11 §3
        // lazy principle), so only the static environment is checked.
        return config.isEnvironmentUsable();
    }

    @Override
    public void initProject(Path tsConfigPath) {
        Objects.requireNonNull(tsConfigPath, "tsConfigPath must not be null");
        try {
            bridge().initProject(tsConfigPath);
            projectInitialized = true;
        } catch (TscBridgeUnavailableException | TscQueryException e) {
            throw new TypeResolutionException("tsc bridge initProject failed for " + tsConfigPath
                    + ": " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isAssignableTo(String filePath, int line, int col, String expectedType) {
        try {
            return bridge().isTypeAssignableTo(filePath, line, col, expectedType);
        } catch (TscBridgeUnavailableException | TscQueryException e) {
            throw new TypeResolutionException("tsc bridge isTypeAssignableTo failed for " + filePath
                    + ":" + line + ":" + col + " (expected '" + expectedType + "'): " + e.getMessage(), e);
        }
    }

    @Override
    public String typeNameAt(String filePath, int line, int col) {
        try {
            return bridge().getTypeAtLocation(filePath, line, col);
        } catch (TscBridgeUnavailableException | TscQueryException e) {
            throw new TypeResolutionException("tsc bridge getTypeAtLocation failed for " + filePath
                    + ":" + line + ":" + col + ": " + e.getMessage(), e);
        }
    }

    /**
     * Stops the resident Node process; the resolver is permanently
     * unavailable afterwards (a closed resolver must not silently respawn).
     */
    @Override
    public void close() {
        synchronized (lock) {
            if (bridge != null) {
                bridge.close();
            }
        }
    }

    private NodeTscBridge bridge() {
        synchronized (lock) {
            if (bridge == null) {
                bridge = new NodeTscBridge(config);
            }
            if (!projectInitialized && defaultTsConfigPath != null) {
                bridge.initProject(defaultTsConfigPath);
                projectInitialized = true;
            }
            return bridge;
        }
    }
}
