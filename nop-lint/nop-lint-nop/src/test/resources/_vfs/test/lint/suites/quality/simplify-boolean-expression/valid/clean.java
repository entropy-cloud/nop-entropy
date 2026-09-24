package demo;

class Clean {
    void run(boolean flag, int a, int b) {
        if (flag) {
            doIt();
        }
        if (!flag) {
            doIt();
        }
        if (a == b) {
            doIt();
        }
    }

    void doIt() {
    }
}
