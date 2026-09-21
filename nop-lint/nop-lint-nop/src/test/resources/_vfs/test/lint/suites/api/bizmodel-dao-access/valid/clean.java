package demo;

// filter path: plain class without a CrudBizModel superclass
class PlainService {

    OrderEntity load(String id) {
        return dao().getEntityById(id);
    }

}

// filter path: CrudBizModel subclass but no dao() call at all
class NopOtherBizModel {

    OrderEntity load(String id) {
        return get(id);
    }

}
