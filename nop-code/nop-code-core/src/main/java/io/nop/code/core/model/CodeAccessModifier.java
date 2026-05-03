package io.nop.code.core.model;

/**
 * 访问修饰符枚举
 */
public enum CodeAccessModifier {
    PUBLIC(10, "公开"),
    PROTECTED(20, "受保护"),
    PRIVATE(30, "私有"),
    PACKAGE_PRIVATE(40, "包私有"),
    INTERNAL(41, "内部"),
    NO_MODIFIER(50, "无修饰符");

    private final int value;
    private final String label;

    CodeAccessModifier(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public int getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }
}
