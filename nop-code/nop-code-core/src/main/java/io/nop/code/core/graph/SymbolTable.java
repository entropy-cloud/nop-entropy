package io.nop.code.core.graph;

import io.nop.code.core.model.CodeSymbol;

import java.util.*;

/**
 * 符号查找表，按全限定名和ID索引
 */
public class SymbolTable {
    private final Map<String, CodeSymbol> byQualifiedName = new HashMap<>();
    private final Map<String, CodeSymbol> byId = new HashMap<>();

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
}
