package io.nop.code.dao;

@SuppressWarnings({"PMD","java:S116"})
public interface _NopCodeDaoConstants {
    
    /**
     * 符号类型: 类 
     */
    String SYMBOL_KIND_CLASS = "10";
                    
    /**
     * 符号类型: 接口 
     */
    String SYMBOL_KIND_INTERFACE = "20";
                    
    /**
     * 符号类型: 枚举 
     */
    String SYMBOL_KIND_ENUM = "30";
                    
    /**
     * 符号类型: 注解类型 
     */
    String SYMBOL_KIND_ANNOTATION_TYPE = "40";
                    
    /**
     * 符号类型: 类型别名 
     */
    String SYMBOL_KIND_TYPE_ALIAS = "45";
                    
    /**
     * 符号类型: Mixin 
     */
    String SYMBOL_KIND_MIXIN = "46";
                    
    /**
     * 符号类型: 装饰器 
     */
    String SYMBOL_KIND_DECORATOR = "47";
                    
    /**
     * 符号类型: 方法 
     */
    String SYMBOL_KIND_METHOD = "50";
                    
    /**
     * 符号类型: 函数 
     */
    String SYMBOL_KIND_FUNCTION = "55";
                    
    /**
     * 符号类型: 构造器 
     */
    String SYMBOL_KIND_CONSTRUCTOR = "60";
                    
    /**
     * 符号类型: 字段 
     */
    String SYMBOL_KIND_FIELD = "70";
                    
    /**
     * 符号类型: 常量 
     */
    String SYMBOL_KIND_CONSTANT = "80";
                    
    /**
     * 符号类型: 命名空间 
     */
    String SYMBOL_KIND_NAMESPACE = "90";
                    
    /**
     * 符号类型: 参数 
     */
    String SYMBOL_KIND_PARAMETER = "95";
                    
    /**
     * 符号类型: 局部变量 
     */
    String SYMBOL_KIND_LOCAL_VARIABLE = "96";
                    
    /**
     * 符号类型: 类型参数 
     */
    String SYMBOL_KIND_TYPE_PARAMETER = "97";
                    
    /**
     * 符号类型: 导入 
     */
    String SYMBOL_KIND_IMPORT = "98";
                    
    /**
     * 符号类型: 路由 
     */
    String SYMBOL_KIND_ROUTE = "100";
                    
    /**
     * 访问修饰符: 公开 
     */
    String ACCESS_MODIFIER_PUBLIC = "10";
                    
    /**
     * 访问修饰符: 受保护 
     */
    String ACCESS_MODIFIER_PROTECTED = "20";
                    
    /**
     * 访问修饰符: 私有 
     */
    String ACCESS_MODIFIER_PRIVATE = "30";
                    
    /**
     * 访问修饰符: 包私有 
     */
    String ACCESS_MODIFIER_PACKAGE_PRIVATE = "40";
                    
    /**
     * 访问修饰符: 内部 
     */
    String ACCESS_MODIFIER_INTERNAL = "41";
                    
    /**
     * 访问修饰符: 无修饰符 
     */
    String ACCESS_MODIFIER_NO_MODIFIER = "50";
                    
    /**
     * 引用类型: 读取 
     */
    String REFERENCE_KIND_READ = "10";
                    
    /**
     * 引用类型: 写入 
     */
    String REFERENCE_KIND_WRITE = "20";
                    
    /**
     * 引用类型: 调用 
     */
    String REFERENCE_KIND_CALL = "30";
                    
    /**
     * 引用类型: 类型引用 
     */
    String REFERENCE_KIND_TYPE_REFERENCE = "40";
                    
    /**
     * 引用类型: 继承 
     */
    String REFERENCE_KIND_EXTENDS = "50";
                    
    /**
     * 引用类型: 实现 
     */
    String REFERENCE_KIND_IMPLEMENTS = "60";
                    
    /**
     * 引用类型: 注解 
     */
    String REFERENCE_KIND_ANNOTATES = "70";
                    
    /**
     * 引用类型: 导入 
     */
    String REFERENCE_KIND_IMPORTS = "80";
                    
    /**
     * 引用类型: 重写 
     */
    String REFERENCE_KIND_OVERRIDES = "90";
                    
    /**
     * 引用类型: 类型引用 
     */
    String REFERENCE_KIND_TYPE_OF = "100";
                    
    /**
     * 引用类型: 实例化 
     */
    String REFERENCE_KIND_INSTANTIATES = "110";
                    
    /**
     * 索引状态: 已创建 
     */
    String INDEX_STATUS_CREATED = "10";
                    
    /**
     * 索引状态: 索引中 
     */
    String INDEX_STATUS_INDEXING = "20";
                    
    /**
     * 索引状态: 就绪 
     */
    String INDEX_STATUS_READY = "30";
                    
    /**
     * 索引状态: 错误 
     */
    String INDEX_STATUS_ERROR = "40";
                    
    /**
     * 索引状态: 已完成 
     */
    String INDEX_STATUS_COMPLETED = "50";
                    
    /**
     * 索引状态: 已检测 
     */
    String INDEX_STATUS_DETECTED = "60";
                    
    /**
     * 编程语言: Java 
     */
    String LANGUAGE_JAVA = "10";
                    
    /**
     * 编程语言: Python 
     */
    String LANGUAGE_PYTHON = "20";
                    
    /**
     * 编程语言: TypeScript 
     */
    String LANGUAGE_TYPESCRIPT = "30";
                    
    /**
     * 编程语言: JavaScript 
     */
    String LANGUAGE_JAVASCRIPT = "40";
                    
    /**
     * 调用类型: 构造函数调用 
     */
    String CALL_TYPE_CONSTRUCTOR = "10";
                    
    /**
     * 继承关系类型: 继承 
     */
    String RELATION_TYPE_EXTENDS = "10";
                    
    /**
     * 继承关系类型: 实现 
     */
    String RELATION_TYPE_IMPLEMENTS = "20";
                    
}
