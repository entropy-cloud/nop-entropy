package io.nop.duckdb;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wiring verification: the engine bean is assembled from the module's app-duckdb.beans.xml
 * by the real IoC container, and @cfg: config injection applies engine settings.
 */
public class TestDuckDbBeans extends BaseTestCase {

    @BeforeAll
    public static void initialize() {
        setTestConfig("nop.duckdb.memory-limit", "256MB");
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    @Timeout(60)
    public void testEngineBeanAssembledAndConfigInjected() throws Exception {
        IDuckDbEngine engine = (IDuckDbEngine) BeanContainer.getBeanByType(IDuckDbEngine.class);
        assertTrue(engine instanceof DuckDbEngine);

        try (Connection conn = engine.openMemory();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT current_setting('memory_limit')")) {
            assertTrue(rs.next());
            String limit = rs.getString(1);
            // injected 256MB is normalized by DuckDB (WI0: 256MB -> 244.1 MiB)
            assertTrue(limit.contains("MiB"), "injected memory_limit must be applied, got " + limit);
        }
    }
}
