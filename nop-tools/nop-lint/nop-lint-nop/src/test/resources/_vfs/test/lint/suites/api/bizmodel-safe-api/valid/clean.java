package demo;

// filter path: CrudBizModel subclass, but dao() chain calls a non-bypass method
class NopOrderBizModel extends CrudBizModel<OrderEntity> {

    OrderEntity load(String id) {
        return dao().otherMethod(id);
    }

}

// filter path: bypass method shape, but no CrudBizModel superclass
class PlainService {

    OrderEntity load(String id) {
        return dao().saveEntity(id);
    }

}
