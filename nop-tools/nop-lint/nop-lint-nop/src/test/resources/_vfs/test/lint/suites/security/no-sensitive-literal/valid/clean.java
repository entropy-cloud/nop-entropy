class Clean {
    void cleanup(org.slf4j.Logger log, String entityType, String id) {
        log.warn("delete index cleanup failed for entityType={} id={}", entityType, id);
        log.info("plain message without keywords");
    }
}
