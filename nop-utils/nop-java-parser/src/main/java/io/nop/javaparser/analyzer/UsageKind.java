package io.nop.javaparser.analyzer;

/**
 * 符号引用类型枚举
 */
public enum UsageKind {
    /**
     * 读取
     */
    READ,

    /**
     * 写入
     */
    WRITE,

    /**
     * 调用
     */
    CALL,

    /**
     * 类型引用
     */
    TYPE_REFERENCE,

    /**
     * 继承
     */
    EXTENDS,

    /**
     * 实现
     */
    IMPLEMENTS,

    /**
     * 注解
     */
    ANNOTATES,

    /**
     * 导入
     */
    IMPORTS,

    /**
     * 重写
     */
    OVERRIDES
}
