package demo;

class Bad {
    void run(boolean flag, boolean other) {
        if (flag) {
        }
        if (other) {
            work();
        }
    }

    void work() {
    }
}
