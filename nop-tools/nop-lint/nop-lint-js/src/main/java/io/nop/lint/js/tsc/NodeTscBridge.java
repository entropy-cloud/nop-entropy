package io.nop.lint.js.tsc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * The Java half of the tsc bridge (design 06 §5.3 方案 A, design 11 §3): owns
 * the resident Node process — spawn, handshake, request/response reuse with
 * per-query deadlines, crash detection with bounded restarts, and the
 * explicit terminal-unavailable state. Lazy by design (design 11 §3): no
 * process exists until the first real query; {@link #isAvailable()} probes
 * the environment (binary + helper script) without spawning.
 *
 * <p>Failure semantics are fail-visible everywhere: a handshake failure is an
 * unavailable bridge; a query deadline that expires or a process that dies
 * mid-conversation is a structured {@link TscQueryException} and recycles the
 * process through the restart budget; once the budget is exhausted the bridge
 * reports {@link TscBridgeUnavailableException} forever — it never retries
 * infinitely and never fabricates a type-level answer. The program cache and
 * its tsconfig-hash invalidation live peer-side (design 11 §4); this class
 * surfaces the cache key each {@link #initProject(Path)} reports.</p>
 */
public final class NodeTscBridge implements AutoCloseable {

    /**
     * The initProject outcome: the peer's program cache key (a hash over the
     * tsconfig fields that shape the program), whether the rebuild actually
     * happened, and the program's source-file count.
     */
    public record ProjectInfo(String cacheKey, boolean rebuilt, int fileCount) {
    }

    /**
     * A node reference for type queries: 0-based line and 0-based column
     * (UTF-16 code units), the TypeScript-internal convention the wire
     * protocol uses verbatim.
     */
    public record NodeRef(String file, int line, int col) {
    }

    private enum State {
        NEW, READY, EXHAUSTED
    }

    private static final Object EOF = new Object();

    private final TscBridgeConfig config;
    private final LinkedBlockingQueue<Object> inbound = new LinkedBlockingQueue<>();

    private Process process;
    private BufferedWriter peerInput;
    private State state = State.NEW;
    private int restartsLeft;
    private int nextId;
    private boolean closed;

    public NodeTscBridge(TscBridgeConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.restartsLeft = config.maxRestarts();
    }

    /**
     * Environment probe without side effects (design 11 §3 lazy principle):
     * true when the peer command and helper script exist and the bridge has
     * not exhausted its restart budget. Spawning happens only on the first
     * real query.
     */
    public synchronized boolean isAvailable() {
        return state != State.EXHAUSTED && !closed && config.isEnvironmentUsable();
    }

    /**
     * Builds (or refreshes) the peer's program for the tsconfig project; the
     * peer rebuilds only when its tsconfig hash changed (design 11 §4).
     */
    public synchronized ProjectInfo initProject(Path tsConfigPath) {
        Objects.requireNonNull(tsConfigPath, "tsConfigPath must not be null");
        Map<String, Object> result = request("initProject",
                "tsConfigPath", tsConfigPath.toAbsolutePath().normalize().toString());
        ProjectInfo info = new ProjectInfo(TscProtocol.text(result, "cacheKey"),
                boolOf(result, "rebuilt"), TscProtocol.intValue(result, "fileCount"));
        if (info.cacheKey() == null) {
            throw new TscQueryException("BAD_FRAME", "initProject result carries no cacheKey: " + result);
        }
        return info;
    }

    /**
     * The rendered type of the node at the file position (0-based line and
     * column), per the {@code getTypeAtLocation} contract of design 06 §5.3.
     */
    public synchronized String getTypeAtLocation(String file, int line, int col) {
        Map<String, Object> result = request("getTypeAtLocation",
                "file", file, "line", line, "col", col);
        String type = TscProtocol.text(result, "type");
        if (type == null) {
            throw new TscQueryException("BAD_FRAME", "getTypeAtLocation result carries no type: " + result);
        }
        return type;
    }

    /**
     * Assignability against an expected type expression (resolved in the
     * project's own checker context), per the {@code isTypeAssignableTo}
     * contract of design 06 §5.3.
     */
    public synchronized boolean isTypeAssignableTo(String file, int line, int col, String expectedType) {
        Map<String, Object> result = request("isTypeAssignableTo",
                "file", file, "line", line, "col", col, "expectedType", expectedType);
        return boolOf(result, "assignable");
    }

    /**
     * Assignability between two node references of the same project.
     */
    public synchronized boolean isTypeAssignableTo(NodeRef from, NodeRef to) {
        Map<String, Object> result = request("isTypeAssignableTo",
                "from", refMap(from), "to", refMap(to));
        return boolOf(result, "assignable");
    }

    /**
     * Stops the resident process; the bridge becomes permanently unavailable
     * afterwards (a closed bridge must never silently respawn).
     */
    @Override
    public synchronized void close() {
        closed = true;
        destroyProcess();
        state = State.EXHAUSTED;
    }

    // ==================== process lifecycle ====================

    private Map<String, Object> request(String op, Object... params) {
        ensureReady();
        int id = ++nextId;
        Map<String, Object> request = TscProtocol.request(id, op, params);
        try {
            peerInput.write(TscProtocol.encode(request));
            peerInput.flush();
        } catch (IOException e) {
            handleProcessFailure("write failed: " + e.getMessage());
            throw new TscQueryException("BRIDGE_CRASHED", "tsc bridge peer rejected request " + id, e);
        }
        Object answer = pollWithDeadline(config.queryTimeout(),
                "query " + op + " (id=" + id + ")");
        if (answer == EOF) {
            handleProcessFailure("peer exited while query " + id + " was in flight");
            throw new TscQueryException("BRIDGE_CRASHED",
                    "tsc bridge peer died before answering request " + id + " (" + op + ")");
        }
        Map<String, Object> frame = TscProtocol.decode((String) answer);
        if (TscProtocol.isOk(frame)) {
            Map<String, Object> result = TscProtocol.resultOf(id, frame);
            Object answerId = frame.get("id");
            if (!Integer.valueOf(id).equals(answerId)) {
                handleProcessFailure("correlation id mismatch");
                throw new TscQueryException("BAD_FRAME", "response id " + answerId
                        + " does not match request " + id);
            }
            return result;
        }
        // A structured error frame is a healthy peer's answer: the process
        // stays up, only this query failed.
        throw TscProtocol.errorOf(id, frame);
    }

    private void ensureReady() {
        if (closed) {
            throw new TscBridgeUnavailableException("tsc bridge is closed");
        }
        if (state == State.EXHAUSTED) {
            throw new TscBridgeUnavailableException("tsc bridge is unavailable: restart budget exhausted "
                    + "(maxRestarts=" + config.maxRestarts() + "); fix the Node/typescript environment "
                    + "and create a fresh bridge");
        }
        if (state == State.READY) {
            return;
        }
        spawn();
    }

    private void spawn() {
        destroyProcess();
        inbound.clear();
        ProcessBuilder builder = new ProcessBuilder(config.spawnCommand());
        builder.redirectErrorStream(false);
        try {
            process = builder.start();
        } catch (IOException e) {
            state = State.EXHAUSTED;
            throw new TscBridgeUnavailableException("tsc bridge cannot spawn " + config.spawnCommand()
                    + ": " + e.getMessage(), e);
        }
        peerInput = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        Thread reader = new Thread(this::drainPeerOutput, "nop-lint-tsc-bridge-reader");
        reader.setDaemon(true);
        reader.start();

        Object answer = pollWithDeadline(config.handshakeTimeout(), "handshake");
        if (answer == EOF) {
            state = State.EXHAUSTED;
            throw new TscBridgeUnavailableException("tsc bridge peer exited before the ready frame "
                    + "(is the helper script importable and does node run it? command=" + config.spawnCommand() + ")");
        }
        try {
            TscProtocol.validateReady(TscProtocol.decode((String) answer));
        } catch (RuntimeException e) {
            // A peer that cannot present a usable typescript binding is a
            // broken environment: retrying changes nothing, so the bridge
            // goes terminal-unavailable instead of burning restarts.
            destroyProcess();
            state = State.EXHAUSTED;
            throw e;
        }
        state = State.READY;
    }

    private void drainPeerOutput() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                inbound.put(line);
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            inbound.offer(EOF);
        }
    }

    private Object pollWithDeadline(Duration budget, String what) {
        long remainingNs = budget.toNanos();
        long startedAt = System.nanoTime();
        while (true) {
            try {
                Object answer = inbound.poll(remainingNs, TimeUnit.NANOSECONDS);
                if (answer == null) {
                    // Deadline exhausted: a hung peer is useless — recycle it
                    // through the restart budget as a structured failure.
                    handleProcessFailure(what + " deadline exceeded");
                    throw new TscQueryException("TIMEOUT", what + " exceeded its deadline budget of "
                            + budget.toMillis() + "ms");
                }
                return answer;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                long elapsed = System.nanoTime() - startedAt;
                remainingNs = Math.max(1, budget.toNanos() - elapsed);
            }
        }
    }

    private void handleProcessFailure(String reason) {
        destroyProcess();
        if (restartsLeft > 0) {
            restartsLeft--;
            state = State.NEW;
        } else {
            state = State.EXHAUSTED;
        }
    }

    private void destroyProcess() {
        if (process != null) {
            process.destroyForcibly();
            process = null;
            peerInput = null;
        }
    }

    private static boolean boolOf(Map<String, Object> frame, String key) {
        Object value = frame.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw new TscQueryException("BAD_FRAME", "frame field '" + key + "' is not a boolean: " + frame);
    }

    private static Map<String, Object> refMap(NodeRef ref) {
        return TscProtocol.request(0, "", "file", ref.file(), "line", ref.line(), "col", ref.col());
    }
}
