package demo;

class Clean {
    void drain(boolean running) {
        while (running) {
            running = step();
        }
    }

    boolean step() {
        return false;
    }
}
