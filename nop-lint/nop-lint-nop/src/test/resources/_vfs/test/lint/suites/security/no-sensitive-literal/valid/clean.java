class Clean {
    void m(org.slf4j.Logger log, RuntimeException ex) {
        log.info("count {}", 1);
        throw ex.param("sqlHash", "abc123");
    }
}
