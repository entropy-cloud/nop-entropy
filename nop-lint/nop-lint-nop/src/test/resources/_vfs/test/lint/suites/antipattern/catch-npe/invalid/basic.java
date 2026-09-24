package demo;

class Bad {
    void run() {
        try {
            work();
        } catch (NullPointerException e) {
            work();
        }
    }

    void work() {
    }
}
