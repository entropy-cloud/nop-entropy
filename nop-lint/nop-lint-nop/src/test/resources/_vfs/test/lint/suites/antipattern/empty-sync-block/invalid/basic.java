package demo;

class Bad {
    private final Object lock = new Object();
    private final Object other = new Object();

    void run() {
        synchronized (lock) {
        }
        synchronized (other) {
            work();
        }
    }

    void work() {
    }
}
