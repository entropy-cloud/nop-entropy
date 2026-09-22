package io.nop.lint.core.pattern;

import io.nop.lint.core.node.LintNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Capture environment for one candidate match: named single captures,
 * ordered multi captures, and the same-name consistency rule (a second
 * binding of one name must re-match the first node exactly, design 04 §2).
 *
 * <p>Insertion failures return {@code false} — never silently succeed — and
 * leave the env unchanged. {@link #clone()} produces a deep copy so the
 * ellipsis matcher's lookahead probes cannot leak bindings into the real
 * environment.</p>
 */
public final class MetaVarEnv {

    private Map<String, LintNode> singles;
    private Map<String, List<LintNode>> multis;

    /**
     * Binds {@code name} to {@code node}; a re-binding succeeds only when the
     * new node matches the existing binding exactly.
     */
    public boolean insert(String name, LintNode node) {
        if (name == null || node == null) {
            return false;
        }
        if (singles == null) {
            singles = new HashMap<>();
        }
        LintNode existing = singles.get(name);
        if (existing != null) {
            return NodeExactEquality.isExact(existing, node);
        }
        singles.put(name, node);
        return true;
    }

    /**
     * Appends {@code nodes} to {@code name}'s ordered capture (ellipsis
     * accumulation across a lockstep scan). Non-capturing names (null, or
     * starting with {@code _} by MetaVarNode convention) are consumed
     * silently: the sequence matches but nothing is recorded.
     */
    public void insertMulti(String name, List<LintNode> nodes) {
        if (name == null || name.startsWith("_")) {
            return;
        }
        if (multis == null) {
            multis = new HashMap<>();
        }
        // An empty insertion still registers the name: matching zero nodes is
        // a live capture, distinct from an unbound name.
        multis.computeIfAbsent(name, k -> new ArrayList<>()).addAll(nodes);
    }

    /**
     * The node bound to {@code name}, or null when unbound.
     */
    public LintNode getCapture(String name) {
        return singles != null ? singles.get(name) : null;
    }

    /**
     * The ordered capture list for {@code name}, or null when unbound (an
     * empty list is a live "matched zero nodes" capture and is returned as
     * such).
     */
    public List<LintNode> getMultiCapture(String name) {
        return multis != null ? multis.get(name) : null;
    }

    /**
     * Read-only snapshot of the single captures (name → node), used by the
     * xscript binding layer to wrap one match's captures; empty when none.
     */
    public Map<String, LintNode> singleCaptures() {
        return singles != null ? Map.copyOf(singles) : Map.of();
    }

    /**
     * Read-only snapshot of the multi captures (name → ordered sequence),
     * used by the xscript binding layer to wrap one match's captures; empty
     * when none.
     */
    public Map<String, List<LintNode>> multiCaptures() {
        if (multis == null) {
            return Map.of();
        }
        Map<String, List<LintNode>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<LintNode>> entry : multis.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }

    /**
     * Deep copy for lookahead probes: probe writes never leak into the
     * original environment.
     */
    public MetaVarEnv clone() {
        MetaVarEnv copy = new MetaVarEnv();
        if (singles != null) {
            copy.singles = new HashMap<>(singles);
        }
        if (multis != null) {
            copy.multis = new HashMap<>();
            for (Map.Entry<String, List<LintNode>> e : multis.entrySet()) {
                copy.multis.put(e.getKey(), new ArrayList<>(e.getValue()));
            }
        }
        return copy;
    }

    /**
     * Replaces this environment's contents with {@code other}'s — how the
     * matcher commits a successful lookahead probe's bindings.
     */
    public void adopt(MetaVarEnv other) {
        this.singles = other.singles;
        this.multis = other.multis;
    }
}
