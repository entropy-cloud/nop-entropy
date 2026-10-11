package demo;

class Clean {
    void run(boolean flag, java.util.Iterator<String> it) {
        if (flag) {
            doIt();
        }
        while (it.hasNext()) {
            step();
        }
    }

    void doIt() {
    }

    void step() {
    }
}
