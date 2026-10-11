package io.nop.refactor.core.symbol;

/**
 * The language-neutral declaration kinds the rename ladder addresses
 * (roadmap WI9 adjudication 3): the WI10 first rung addresses locals and
 * parameters (single file), the WI11 second rung fields, non-virtual
 * methods and types (module scope). {@code CONSTRUCTOR} is its own kind
 * since WI11 (plan 11 adjudication 8): a constructor rename IS a class
 * rename, so it refuses with OUT_OF_SCOPE instead of masquerading as a
 * METHOD and corrupting the file.
 */
public enum SymbolKind {
    LOCAL_VARIABLE,
    PARAMETER,
    FIELD,
    METHOD,
    CONSTRUCTOR,
    TYPE
}
