package demo;

class Clean {
    void run() {
        try {
            work();
        } catch (IllegalStateException e) {
            work();
        }
    }

    void work() {
    }
}
