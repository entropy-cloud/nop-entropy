package io.nop.refactor.core.symbol;

/**
 * The language-neutral declaration kinds the rename ladder addresses
 * (roadmap WI9 adjudication 3): the WI10 first rung addresses locals and
 * parameters (single file), the WI11 second rung fields, non-virtual
 * methods and types (module scope).
 */
public enum SymbolKind {
    LOCAL_VARIABLE,
    PARAMETER,
    FIELD,
    METHOD,
    TYPE
}
