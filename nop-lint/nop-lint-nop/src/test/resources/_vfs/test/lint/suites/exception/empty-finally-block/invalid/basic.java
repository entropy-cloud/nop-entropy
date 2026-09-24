package demo;

class Bad {
    void run() throws Exception {
        try {
            risky();
        } finally {
        }
    }

    void risky() throws Exception {
    }
}
