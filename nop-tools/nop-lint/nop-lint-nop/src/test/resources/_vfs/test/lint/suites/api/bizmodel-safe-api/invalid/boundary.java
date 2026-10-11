package demo;

class NopRepoBizModel extends CrudBizModel<RepoEntity> {

    java.util.List<RepoEntity> search(String q) {
        return dao().findAllByQuery(newQuery(q));
    }

    OrderEntity proxy(String id) {
        return dao().getEntityById(translate(id));
    }

}
