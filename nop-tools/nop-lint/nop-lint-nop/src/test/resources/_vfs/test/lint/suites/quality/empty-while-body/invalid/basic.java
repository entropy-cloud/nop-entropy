package demo;

class Bad {
    void drain(boolean running) {
        while (running) {
        }
        while (running) {
            step();
        }
    }

    boolean step() {
        return false;
    }
}
