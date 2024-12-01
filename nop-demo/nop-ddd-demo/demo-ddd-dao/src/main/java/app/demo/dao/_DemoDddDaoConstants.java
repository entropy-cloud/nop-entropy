package app.demo.dao;

@SuppressWarnings({"PMD","java:S116"})
public interface _DemoDddDaoConstants {
    
    /**
     * 路由状态: 未路由 
     */
    String ROUTING_STATUS_NOT_ROUTED = "NOT_ROUTED";
                    
    /**
     * 路由状态: 已路由 
     */
    String ROUTING_STATUS_ROUTED = "ROUTED";
                    
    /**
     * 路由状态: 错误路由 
     */
    String ROUTING_STATUS_MISROUTED = "MISROUTED";
                    
    /**
     * 运输状态: 未接收 
     */
    String TRANSPORT_STATUS_NOT_RECEIVED = "NOT_RECEIVED";
                    
    /**
     * 运输状态: 已到港 
     */
    String TRANSPORT_STATUS_IN_PORT = "IN_PORT";
                    
    /**
     * 运输状态: 已装货 
     */
    String TRANSPORT_STATUS_ONBOARD_CARRIER = "ONBOARD_CARRIER";
                    
    /**
     * 运输状态: 已认领 
     */
    String TRANSPORT_STATUS_CLAIMED = "CLAIMED";
                    
    /**
     * 运输状态: 未知 
     */
    String TRANSPORT_STATUS_UNKNOWN = "UNKNOWN";
                    
}
