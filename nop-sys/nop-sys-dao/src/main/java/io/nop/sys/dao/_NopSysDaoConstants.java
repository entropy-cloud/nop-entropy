package io.nop.sys.dao;

@SuppressWarnings({"PMD","java:S116"})
public interface _NopSysDaoConstants {
    
    /**
     * 事件状态: 待处理 
     */
    int SYS_EVENT_STATUS_WAITING = 0;
                    
    /**
     * 事件状态: 已处理 
     */
    int SYS_EVENT_STATUS_PROCESSED = 10;
                    
    /**
     * 事件状态: 已过期 已超时
     */
    int SYS_EVENT_STATUS_EXPIRED = 20;
                    
    /**
     * 事件状态: 已失败 经过多次尝试后仍然处理失败
     */
    int SYS_EVENT_STATUS_FAILED = 30;
                    
}
