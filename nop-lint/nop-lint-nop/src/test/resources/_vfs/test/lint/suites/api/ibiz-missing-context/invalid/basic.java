package demo;

interface IOrderBiz extends ICrudBiz<OrderEntity> {

    OrderEntity get(String id);

    OrderEntity find(String id, IServiceContext ctx);

    void reload();

}
