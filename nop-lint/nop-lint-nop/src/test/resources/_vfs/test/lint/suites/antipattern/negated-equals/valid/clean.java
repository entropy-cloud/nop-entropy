package demo;

class Clean {
    void run(int a, int b, boolean flag) {
        if (a != b) {
            work();
        }
        if (!(flag && a > b)) {
            work();
        }
    }

    void work() {
    }
}
