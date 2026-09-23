class Basic {
    void m(org.slf4j.Logger log, RuntimeException ex) {
        log.info("jdbc:mysql://user:pass@host/db");
        throw ex.param("sql", "select * from users");
    }
}
