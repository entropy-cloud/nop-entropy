package io.nop.javaparser.analyzer;

/**
 * 符号类型枚举
 */
public enum SymbolKind {
    /**
     * 类
     */
    CLASS,

    /**
     * 接口
     */
    INTERFACE,

    /**
     * 枚举
     */
    ENUM,

    /**
     * 枚举常量
     */
    ENUM_CONSTANT,

    /**
     * 注解类型
     */
    ANNOTATION_TYPE,

    /**
     * 方法
     */
    METHOD,

    /**
     * 构造器
     */
    CONSTRUCTOR,

    /**
     * 字段
     */
    FIELD
}
