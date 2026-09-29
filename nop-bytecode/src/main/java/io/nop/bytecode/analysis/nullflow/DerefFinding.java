package io.nop.bytecode.analysis.nullflow;

/**
 * One dereference finding: a field access / array access / instance method call / monitor-enter
 * whose operand is MAYNULL or NULL on some incoming CFG state, deduplicated per
 * (class, method, instruction).
 */
public record DerefFinding(String className, String methodName, int insnIndex, int opcode, String ref) {
}
