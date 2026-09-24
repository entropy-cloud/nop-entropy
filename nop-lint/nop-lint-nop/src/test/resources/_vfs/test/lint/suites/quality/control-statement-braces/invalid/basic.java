package demo;

class Bad {
    void run(boolean flag) {
        if (flag)
            work();
        if (!flag) work();
    }

    void work() {
    }
}
