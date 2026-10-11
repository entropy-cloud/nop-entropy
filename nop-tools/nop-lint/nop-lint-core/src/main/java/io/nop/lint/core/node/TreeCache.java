package io.nop.lint.core.node;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-tree node cache (plan 09, perf-baseline candidate 1): the tree-sitter
 * facade's {@code children()} walks used to rebuild a cursor and materialize
 * a fresh wrapper list on every call, and {@code findMatches} walks the whole
 * tree once per rule — the same node's children were rebuilt once per rule.
 * One {@code TreeCache} instance is owned by each {@link LintTree} and passed
 * to every wrapper it derives, so a node's children list and wrapper are
 * materialized at most once for the tree's lifetime.
 *
 * <p>Lifecycle: the cache is an instance field of the {@code LintTree} — it
 * is reachable only through the tree, so a dropped tree (LSP didChange's old
 * revision, the CLI's next file, a finished GraphQL request) takes its cache
 * with it. There is no static map anywhere in the facade, hence no GC-root
 * path can retain a tree or its wrappers (design 03 增注; plan 09 review F1:
 * a static {@code WeakHashMap<TSTree, cache>} would be a value-holds-key
 * leak — the cache strongly references wrappers which strongly reference the
 * key tree).</p>
 *
 * <p>Thread safety: per-tree concurrent maps. No cross-tree sharing exists,
 * so there is no global lock; concurrent first walks of one tree cooperate
 * through {@code putIfAbsent} semantics. Cache keys are stable arena node
 * ids; the {@code aliasSymbol} component of wrapper value-identity is
 * derivation-canonical (TreeSitterLintNode's "handle derivation is unique"
 * contract), so the id alone is a complete cache key.</p>
 */
final class TreeCache {

    final ConcurrentHashMap<Integer, List<LintNode>> children = new ConcurrentHashMap<>();
    final ConcurrentHashMap<Integer, List<LintNode>> namedChildren = new ConcurrentHashMap<>();
    final ConcurrentHashMap<Integer, TreeSitterLintNode> wrappers = new ConcurrentHashMap<>();
}
