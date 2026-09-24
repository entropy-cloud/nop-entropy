package demo;

class Bad {
    void run(boolean flag) {
        if (flag == true) {
            doIt();
        }
        while (flag == false) {
            wait_();
        }
    }

    void doIt() {
    }

    void wait_() {
    }
}
