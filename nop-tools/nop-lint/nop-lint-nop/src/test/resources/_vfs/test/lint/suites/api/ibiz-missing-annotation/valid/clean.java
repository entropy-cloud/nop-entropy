package demo;

interface IOrderBiz extends ICrudBiz<OrderEntity> {

    @BizQuery
    OrderEntity get(@Name String id, IServiceContext ctx);

    @BizMutation
    OrderEntity save(@Name OrderEntity entity, IServiceContext ctx);

    @BizAction("approve")
    void approve(@Name String id, IServiceContext ctx);

}

// filter path: interface name does not match the I*Biz convention
interface NotBizLike {

    void plain(String id);

}

// filter path: I*Biz name but no ICrudBiz extension
interface IPlainBiz {

    void noContract(String id);

}

// filter path: biz-like class name, but not an interface
class IWeirdBiz {

    public void decoy(String id) {
    }

}
