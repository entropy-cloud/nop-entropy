package demo;

class Clean {
    void run() {
        try {
            work();
        } catch (RuntimeException e) {
            work();
        }
    }

    void work() {
    }
}
