package demo;

class Clean {
    void fail(String msg) {
        if (msg == null) {
            throw new IllegalArgumentException("msg required");
        }
    }
}
