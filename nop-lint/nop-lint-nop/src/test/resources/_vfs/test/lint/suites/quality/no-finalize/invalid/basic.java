package demo;

class Bad {
    @Override
    protected void finalize() throws Throwable {
        cleanup();
    }

    void cleanup() {
    }
}
