package io.nop.code.core.graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.nop.code.core.model.CodeSymbol;
/**
 * 符号查找表，按全限定名和ID索引
 *
 * W-11 concurrency contract (plan 2565, adjudication A / facility F3; source anchor
 * CODEINDEX-RT-003): both index maps are {@link ConcurrentHashMap} and {@code truncated} is
 * volatile, so {@link #add}, point reads ({@link #getByQualifiedName}, {@link #getById},
 * {@link #size}) and {@link #findAllByQualifiedNamePrefix} are safe without external
 * synchronization (CHM weakly-consistent reads/traversal). Dedup must use
 * {@link #putIfAbsent(CodeSymbol)} — a get-then-add check-then-act sequence is not atomic.
 * {@link #getAll()} deliberately keeps its synchronized defensive snapshot: CHM iteration is
 * only weakly consistent and does not satisfy the WP-5 AR-155/158 immunity-to-later-mutation
 * strong isolation contract.
 */
public class SymbolTable {
    private final Map<String, CodeSymbol> byQualifiedName = new ConcurrentHashMap<>();
    private final Map<String, CodeSymbol> byId = new ConcurrentHashMap<>();
    private volatile boolean truncated;

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    public void add(CodeSymbol symbol) {
        if (symbol.getQualifiedName() != null) {
            byQualifiedName.put(symbol.getQualifiedName(), symbol);
        }
        if (symbol.getId() != null) {
            byId.put(symbol.getId(), symbol);
        }
    }

    /**
     * Atomic add-if-absent on both index maps (per-map atomicity; cross-map last-write-wins is
     * idempotent and equivalent to the previous get-then-add semantics). Null keys are guarded —
     * ConcurrentHashMap.putIfAbsent(null, ...) would throw NPE.
     *
     * @return true if the symbol was inserted; false if a symbol with the same qualified name
     *         was already present (dedup key = qualified name; for symbols without a qualified
     *         name the id map decides)
     */
    public boolean putIfAbsent(CodeSymbol symbol) {
        if (symbol.getQualifiedName() != null) {
            boolean inserted = byQualifiedName.putIfAbsent(symbol.getQualifiedName(), symbol) == null;
            if (symbol.getId() != null) {
                byId.putIfAbsent(symbol.getId(), symbol);
            }
            return inserted;
        }
        if (symbol.getId() != null) {
            return byId.putIfAbsent(symbol.getId(), symbol) == null;
        }
        return false;
    }

    public CodeSymbol getByQualifiedName(String qualifiedName) {
        return byQualifiedName.get(qualifiedName);
    }

    public CodeSymbol getById(String id) {
        return byId.get(id);
    }

    // WP-5 AR-155/158: return a defensive snapshot so callers iterating a cached table are immune
    // to later mutations (e.g. addToSymbolTableCache adding symbols) and to concurrent CME.
    // Frozen contract: keeps the monitor + copy even after W-11 CHM convergence (CHM weakly
    // consistent iteration does NOT satisfy this strong isolation guarantee).
    public synchronized Collection<CodeSymbol> getAll() {
        return new ArrayList<>(byId.values());
    }

    public int size() {
        return byId.size();
    }

    public List<CodeSymbol> findAllByQualifiedNamePrefix(String prefix) {
        List<CodeSymbol> result = new ArrayList<>();
        for (Map.Entry<String, CodeSymbol> entry : byQualifiedName.entrySet()) {
            if (entry.getKey() != null && entry.getKey().startsWith(prefix)) {
                result.add(entry.getValue());
            }
        }
        return result;
    }
}
