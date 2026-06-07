package io.nop.code.core.model;

/**
 * 代码使用类型枚举
 */
public enum CodeUsageKind {
    READ,
    WRITE,
    CALL,
    TYPE_REFERENCE,
    EXTENDS,
    IMPLEMENTS,
    ANNOTATES,
    IMPORTS,
    OVERRIDES,
    TYPE_OF,
    INSTANTIATES
}
