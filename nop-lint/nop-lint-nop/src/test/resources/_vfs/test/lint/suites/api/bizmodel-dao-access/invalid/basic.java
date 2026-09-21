package demo;

class NopOrderBizModel extends CrudBizModel<OrderEntity> {

    OrderEntity load(String id) {
        return dao().getEntityById(id);
    }

}
