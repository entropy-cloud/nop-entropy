package io.nop.duckdb;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * Child process for cross-JVM lock tests: holds the single-writer lock on a .duckdb file
 * with an open transaction until killed (stdin closes).
 */
public class LockHolderMain {
    public static void main(String[] args) throws Exception {
        String path = args[0];
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + path);
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS holder_t(k INTEGER)");
            conn.setAutoCommit(false);
            st.execute("INSERT INTO holder_t VALUES (1)"); // open transaction holds the write lock
            System.out.println("READY");
            System.out.flush();
            new BufferedReader(new InputStreamReader(System.in)).readLine();
        }
    }
}
