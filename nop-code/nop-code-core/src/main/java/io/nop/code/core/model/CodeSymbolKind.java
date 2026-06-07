package io.nop.code.core.model;

/**
 * 代码符号类型枚举
 */
public enum CodeSymbolKind {
    CLASS(10, "类"),
    INTERFACE(20, "接口"),
    ENUM(30, "枚举"),
    ANNOTATION_TYPE(40, "注解类型"),
    TYPE_ALIAS(45, "类型别名"),
    MIXIN(46, "Mixin"),
    DECORATOR(47, "装饰器"),
    METHOD(50, "方法"),
    FUNCTION(55, "函数"),
    CONSTRUCTOR(60, "构造器"),
    FIELD(70, "字段"),
    CONSTANT(80, "常量"),
    NAMESPACE(90, "命名空间"),
    PARAMETER(95, "参数"),
    LOCAL_VARIABLE(96, "局部变量"),
    TYPE_PARAMETER(97, "类型参数"),
    IMPORT(98, "导入"),
    ROUTE(100, "路由");

    private final int value;
    private final String label;

    CodeSymbolKind(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public int getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public static boolean isTypeKind(CodeSymbolKind kind) {
        return kind == CLASS || kind == INTERFACE || kind == ENUM || kind == ANNOTATION_TYPE;
    }
}
