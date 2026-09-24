package demo;

class Clean {
    void run(boolean flag, boolean other) {
        if (flag) {
            work();
        }
        if (other) {
            // intentionally empty: handled upstream
        }
    }

    void work() {
    }
}
