package demo;

class Clean {
    void fail(RuntimeException ex) {
        LOG.error("failed", ex);
    }
}
