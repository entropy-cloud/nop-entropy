package io.nop.treesitter.util;

import io.nop.treesitter.TreeSitterException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * String ↔ int interning table for grammar symbol names.
 *
 * <p>{@link #intern} returns a stable id per distinct string (same string always
 * maps to the same id); ids are assigned sequentially starting at 0 and never
 * collide. The backing list grows on demand. {@link #resolve} throws
 * {@link TreeSitterException} for an id that was never interned instead of
 * returning null.</p>
 */
public final class SymbolTable {

    private final Map<String, Integer> ids = new HashMap<>();
    private final List<String> symbols = new ArrayList<>();

    /**
     * Returns the id for {@code symbol}, interning it on first sight.
     */
    public int intern(String symbol) {
        if (symbol == null) {
            throw new IllegalArgumentException("symbol must not be null");
        }
        Integer existing = ids.get(symbol);
        if (existing != null) {
            return existing;
        }
        int id = symbols.size();
        ids.put(symbol, id);
        symbols.add(symbol);
        return id;
    }

    /**
     * Returns the string interned at {@code id}, or throws if never interned.
     */
    public String resolve(int id) {
        if (id < 0 || id >= symbols.size()) {
            throw new TreeSitterException("cannot resolve symbol id " + id + ": not interned");
        }
        return symbols.get(id);
    }

    /**
     * Number of distinct symbols interned so far.
     */
    public int size() {
        return symbols.size();
    }
}
