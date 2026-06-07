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

    public void add(CodeSymbol symbol) {
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

    public Collection<CodeSymbol> getAll() {
        return byId.values();
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
