class Demo {
    void run(java.sql.Connection conn) {
        IoHelper.safeClose(conn.close());
    }
}
