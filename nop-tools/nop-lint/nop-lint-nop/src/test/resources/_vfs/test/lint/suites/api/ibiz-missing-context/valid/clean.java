package demo;

interface IOrderBiz extends ICrudBiz<OrderEntity> {

    OrderEntity get(String id, IServiceContext ctx);

    OrderEntity require(String id, FieldSelectionBean selection, IServiceContext ctx);

}

// filter path: interface name does not match the I*Biz convention
interface NotBizLike {

    void plain(String id);

}
