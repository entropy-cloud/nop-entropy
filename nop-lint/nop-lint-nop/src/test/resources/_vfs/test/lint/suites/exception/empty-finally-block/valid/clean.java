package demo;

class Clean {
    void run() throws Exception {
        try {
            risky();
        } finally {
            cleanup();
        }
    }

    void risky() throws Exception {
    }

    void cleanup() {
    }
}
