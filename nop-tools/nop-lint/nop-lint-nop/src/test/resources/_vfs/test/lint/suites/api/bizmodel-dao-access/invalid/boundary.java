package demo;

class NopRepoBizModel extends CrudBizModel<RepoEntity> {

    void sync(java.util.List<String> ids) {
        if (ids.isEmpty()) {
            dao().saveEntity(buildDefault());
        } else {
            for (String id : ids) {
                dao().removeEntity(id);
            }
        }
    }

    // nested class boundary: the nearest enclosing class_declaration is
    // Inner (no CrudBizModel superclass), so the call is filtered
    static class Inner {
        void touch() {
            dao().clearAll();
        }
    }

}
