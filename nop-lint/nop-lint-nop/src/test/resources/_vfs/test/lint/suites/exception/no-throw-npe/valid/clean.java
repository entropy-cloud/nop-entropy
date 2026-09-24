package demo;

class Clean {
    void require(String msg) {
        if (msg == null) {
            throw new IllegalArgumentException("msg required");
        }
        throw new IllegalStateException("unreachable");
    }
}
