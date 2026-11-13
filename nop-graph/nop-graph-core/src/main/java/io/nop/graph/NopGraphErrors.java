package io.nop.graph;

import io.nop.api.core.exceptions.ErrorCode;

/**
 * Error codes for the nop-graph-core algorithm package. Descriptions are in English.
 *
 * <p>Namespace split (plan 2529 ruling 6.5): {@code io.nop.core.CoreErrors} carries the
 * built-in graph utility codes of nop-core ({@code nop.err.graph.duplicate-vertex} and
 * friends); this registry carries the nop-graph-core algorithm codes. The two sets are
 * disjoint; the {@code nop.err.graph.export-failed} literal in {@code GraphExporter} stays
 * untouched (optional convergence follow-up, not this batch).
 */
public interface NopGraphErrors {
    String ARG_TIMEOUT_MS = "timeoutMs";
    String ARG_NODE_COUNT = "nodeCount";

    ErrorCode ERR_GRAPH_COMPUTE_TIMEOUT = ErrorCode.define(
            "nop.err.graph.compute-timeout",
            "Betweenness centrality computation exceeded the wall-clock envelope of {timeoutMs} ms "
                    + "for {nodeCount} nodes and was abandoned. The underlying Brandes computation is "
                    + "not interruptible: it keeps running in the background on a daemon thread until "
                    + "it finishes naturally (JVM exit is never blocked), and its own failure after "
                    + "abandonment is not reported. Retrying with the same input will fail again "
                    + "(bizFatal): increase nop.graph.betweenness-timeout-ms, pass an explicit timeout "
                    + "to compute(graph, nodes, timeoutMs), or reduce the node set.",
            ARG_TIMEOUT_MS, ARG_NODE_COUNT);

    ErrorCode ERR_GRAPH_COMPUTE_INTERRUPTED = ErrorCode.define(
            "nop.err.graph.compute-interrupted",
            "Thread was interrupted while waiting for the betweenness centrality computation. "
                    + "The interrupt flag is restored and the worker task is cancelled (the underlying "
                    + "Brandes computation itself is not interruptible and finishes in the background "
                    + "on a daemon thread).");
}
