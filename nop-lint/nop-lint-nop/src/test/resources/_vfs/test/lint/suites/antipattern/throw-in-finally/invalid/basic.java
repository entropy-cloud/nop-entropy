package demo;

class Bad {
    void run() {
        try {
            work();
        } finally {
            throw new IllegalStateException("cleanup failed");
        }
    }

    void work() {
    }
}
