package demo;

class Basic {

    void load(String id) {
        if (id == null) {
            throw new RuntimeException("id is null");
        }
        throw new Exception();
    }

}
