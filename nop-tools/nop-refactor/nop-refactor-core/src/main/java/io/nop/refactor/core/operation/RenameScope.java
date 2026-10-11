package io.nop.refactor.core.operation;

/**
 * The rename symbol-domain scope (plan 09 adjudication 5, WI2 adjudication
 * 1): v1 ships exactly one domain — the single module. The ladder widens
 * the *symbol kinds* (WI10 locals/parameters, WI11 fields/methods/types),
 * not the domain; a wider domain was rejected out of budget and would need
 * a design-layer decision.
 */
public enum RenameScope {
    MODULE
}
