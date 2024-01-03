package io.nop.dyn.dao;

@SuppressWarnings({"PMD","java:S116"})
public interface _NopDynDaoConstants {
    
    /**
     * 应用状态: 未发布 
     */
    int APP_STATUS_UNPUBLISHED = 0;
                    
    /**
     * 应用状态: 已发布 
     */
    int APP_STATUS_PUBLISHED = 10;
                    
    /**
     * 模块状态: 未发布 
     */
    int MODULE_STATUS_UNPUBLISHED = 0;
                    
    /**
     * 模块状态: 已发布 
     */
    int MODULE_STATUS_PUBLISHED = 10;
                    
    /**
     * 页面类型: 百度AMIS 
     */
    String PAGE_SCHEMA_TYPE_AMIS = "AMIS";
                    
    /**
     * 页面类型: 华为OpenTiny 
     */
    String PAGE_SCHEMA_TYPE_OPEN_TINY = "OpenTiny";
                    
    /**
     * 页面类型: 阿里Formily 
     */
    String PAGE_SCHEMA_TYPE_FORMILY = "Formily";
                    
    /**
     * 实体存储类型: 虚拟表 
     */
    int ENTITY_STORE_TYPE_VIRTUAL = 0;
                    
    /**
     * 实体存储类型: 实体表 
     */
    int ENTITY_STORE_TYPE_REAL = 10;
                    
    /**
     * 函数类型: 查询 
     */
    String FUNCTION_TYPE_QUERY = "query";
                    
    /**
     * 函数类型: 修改 
     */
    String FUNCTION_TYPE_MUTATION = "mutation";
                    
    /**
     * 函数类型: 订阅 
     */
    String FUNCTION_TYPE_SUBSCRIPTION = "subscription";
                    
    /**
     * 函数类型: 加载器 
     */
    String FUNCTION_TYPE_LOADER = "loader";
                    
}
