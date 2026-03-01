package io.nop.javaparser.analyzer;

/**
 * 访问修饰符枚举
 */
public enum AccessModifier {
    /**
     * 公开
     */
    PUBLIC,

    /**
     * 保护
     */
    PROTECTED,

    /**
     * 私有
     */
    PRIVATE,

    /**
     * 包级私有（默认）
     */
    PACKAGE_PRIVATE
}
