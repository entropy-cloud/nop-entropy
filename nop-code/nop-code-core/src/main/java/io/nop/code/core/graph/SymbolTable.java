package io.nop.code.core.graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.nop.code.core.model.CodeSymbol;
/**
 * 符号查找表，按全限定名和ID索引
 */
public class SymbolTable {
    private final Map<String, CodeSymbol> byQualifiedName = new HashMap<>();
    private final Map<String, CodeSymbol> byId = new HashMap<>();
    private boolean truncated;

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    public synchronized void add(CodeSymbol symbol) {
        if (symbol.getQualifiedName() != null) {
            byQualifiedName.put(symbol.getQualifiedName(), symbol);
        }
        if (symbol.getId() != null) {
            byId.put(symbol.getId(), symbol);
        }
    }

    public CodeSymbol getByQualifiedName(String qualifiedName) {
        return byQualifiedName.get(qualifiedName);
    }

    public CodeSymbol getById(String id) {
        return byId.get(id);
    }

    // WP-5 AR-155/158: return a defensive snapshot so callers iterating a cached table are immune
    // to later mutations (e.g. addToSymbolTableCache adding symbols) and to concurrent CME.
    public synchronized Collection<CodeSymbol> getAll() {
        return new ArrayList<>(byId.values());
    }

    public int size() {
        return byId.size();
    }

    public synchronized List<CodeSymbol> findAllByQualifiedNamePrefix(String prefix) {
        List<CodeSymbol> result = new ArrayList<>();
        for (Map.Entry<String, CodeSymbol> entry : byQualifiedName.entrySet()) {
            if (entry.getKey() != null && entry.getKey().startsWith(prefix)) {
                result.add(entry.getValue());
            }
        }
        return result;
    }
}
