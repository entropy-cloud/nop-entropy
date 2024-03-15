package demo.orm.test;

import com.zaxxer.hikari.HikariDataSource;
import io.nop.commons.metrics.GlobalMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;

public class TestH2DB {

    private HikariDataSource dataSource;

    @BeforeEach
    public void setUp() {
        dataSource = createDataSource();
    }

    @AfterEach
    public void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private HikariDataSource createDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setMetricRegistry(GlobalMeterRegistry.instance());
        ds.setDriverClassName("org.h2.Driver");
        ds.setJdbcUrl("jdbc:h2:mem:test");
        return ds;
    }

    @Test
    public void testH2Connection() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            Assertions.assertFalse(conn.isClosed());
        }
    }

}
