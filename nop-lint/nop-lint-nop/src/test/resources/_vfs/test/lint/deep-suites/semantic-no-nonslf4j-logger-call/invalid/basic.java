class Bad {
    void run(MySink sink) {
        String payload = "data";
        payload.error("boom");
        sink.log("custom receiver resolves in the same compilation unit");
    }

    static class MySink {
        void log(String message) {
        }
    }
}
