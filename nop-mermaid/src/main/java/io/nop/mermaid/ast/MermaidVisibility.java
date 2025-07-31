package io.nop.mermaid.ast;

public enum MermaidVisibility {
    PUBLIC("+"),
    PRIVATE("-"),
    PROTECTED("#"),
    PACKAGE("~");

    private final String symbol;

    MermaidVisibility(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    public static MermaidVisibility fromSymbol(String symbol) {
        for (MermaidVisibility v : values()) {
            if (v.symbol.equals(symbol))
                return v;
        }
        return PUBLIC; // default
    }
}