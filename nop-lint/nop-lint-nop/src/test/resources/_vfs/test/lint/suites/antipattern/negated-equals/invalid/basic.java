package demo;

class Bad {
    void run(int a, int b) {
        if (!(a == b)) {
            work();
        }
        if (!(a == b)) {
            work();
        }
    }

    void work() {
    }
}
