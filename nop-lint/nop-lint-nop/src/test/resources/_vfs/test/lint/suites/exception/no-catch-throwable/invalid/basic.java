package demo;

class Bad {
    void run() {
        try {
            work();
        } catch (Throwable t) {
            work();
        }
    }

    void work() {
    }
}
