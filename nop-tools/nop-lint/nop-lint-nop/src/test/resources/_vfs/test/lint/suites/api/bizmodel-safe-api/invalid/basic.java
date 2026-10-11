package demo;

class NopOrderBizModel extends CrudBizModel<OrderEntity> {

    OrderEntity load(String id) {
        return dao().getEntityById(id);
    }

    void store(OrderEntity entity) {
        dao().saveEntity(entity);
    }

}
