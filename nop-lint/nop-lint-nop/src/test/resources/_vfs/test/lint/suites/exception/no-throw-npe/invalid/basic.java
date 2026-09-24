package demo;

class Bad {
    void require(String msg) {
        if (msg == null) {
            throw new NullPointerException();
        }
        throw new NullPointerException("msg was null");
    }
}
