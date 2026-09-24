package demo;

class Bad {
    void fail(RuntimeException ex) {
        ex.printStackTrace();
        ex.printStackTrace(System.out);
    }
}
