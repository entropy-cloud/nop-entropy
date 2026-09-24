package demo;

class Clean {
    void run(boolean bad) {
        try {
            if (bad) {
                throw new IllegalStateException("input");
            }
        } finally {
            cleanup();
        }
    }

    void cleanup() {
    }
}
