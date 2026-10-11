package demo;

interface IOrderBiz extends ICrudBiz<OrderEntity> {

    OrderEntity get(String id, IServiceContext ctx);

    @BizQuery
    OrderEntity find(String id, IServiceContext ctx);

    void remove(String id, IServiceContext ctx);

}
