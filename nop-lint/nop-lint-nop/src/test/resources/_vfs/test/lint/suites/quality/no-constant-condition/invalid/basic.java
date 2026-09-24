package demo;

class Bad {
    void run() {
        if (true) {
            doIt();
        }
        while (false) {
            doIt();
        }
    }

    void doIt() {
    }
}
