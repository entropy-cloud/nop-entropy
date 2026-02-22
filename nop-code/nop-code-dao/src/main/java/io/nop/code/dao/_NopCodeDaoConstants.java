package io.nop.code.dao;

@SuppressWarnings({"PMD","java:S116"})
public interface _NopCodeDaoConstants {
    
    /**
     * 符号类型: 类 
     */
    int SYMBOL_KIND_CLASS = 10;
                    
    /**
     * 符号类型: 接口 
     */
    int SYMBOL_KIND_INTERFACE = 20;
                    
    /**
     * 符号类型: 枚举 
     */
    int SYMBOL_KIND_ENUM = 30;
                    
    /**
     * 符号类型: 注解类型 
     */
    int SYMBOL_KIND_ANNOTATION_TYPE = 40;
                    
    /**
     * 符号类型: 方法 
     */
    int SYMBOL_KIND_METHOD = 50;
                    
    /**
     * 符号类型: 构造器 
     */
    int SYMBOL_KIND_CONSTRUCTOR = 60;
                    
    /**
     * 符号类型: 字段 
     */
    int SYMBOL_KIND_FIELD = 70;
                    
    /**
     * 符号类型: 枚举常量 
     */
    int SYMBOL_KIND_ENUM_CONSTANT = 80;
                    
    /**
     * 访问修饰符: 公开 
     */
    int ACCESS_MODIFIER_PUBLIC = 10;
                    
    /**
     * 访问修饰符: 受保护 
     */
    int ACCESS_MODIFIER_PROTECTED = 20;
                    
    /**
     * 访问修饰符: 私有 
     */
    int ACCESS_MODIFIER_PRIVATE = 30;
                    
    /**
     * 访问修饰符: 包私有 
     */
    int ACCESS_MODIFIER_PACKAGE_PRIVATE = 40;
                    
    /**
     * 引用类型: 读取 
     */
    int REFERENCE_KIND_READ = 10;
                    
    /**
     * 引用类型: 写入 
     */
    int REFERENCE_KIND_WRITE = 20;
                    
    /**
     * 引用类型: 调用 
     */
    int REFERENCE_KIND_CALL = 30;
                    
    /**
     * 引用类型: 类型引用 
     */
    int REFERENCE_KIND_TYPE_REFERENCE = 40;
                    
    /**
     * 引用类型: 继承 
     */
    int REFERENCE_KIND_EXTENDS = 50;
                    
    /**
     * 引用类型: 实现 
     */
    int REFERENCE_KIND_IMPLEMENTS = 60;
                    
    /**
     * 引用类型: 注解 
     */
    int REFERENCE_KIND_ANNOTATES = 70;
                    
    /**
     * 引用类型: 导入 
     */
    int REFERENCE_KIND_IMPORTS = 80;
                    
    /**
     * 引用类型: 重写 
     */
    int REFERENCE_KIND_OVERRIDES = 90;
                    
    /**
     * 索引状态: 已创建 
     */
    int INDEX_STATUS_CREATED = 10;
                    
    /**
     * 索引状态: 索引中 
     */
    int INDEX_STATUS_INDEXING = 20;
                    
    /**
     * 索引状态: 就绪 
     */
    int INDEX_STATUS_READY = 30;
                    
    /**
     * 索引状态: 错误 
     */
    int INDEX_STATUS_ERROR = 40;
                    
}
