package io.nop.batch.exp;

import io.nop.batch.exp.config.ExportDbConfig;
import io.nop.batch.exp.config.ImportDbConfig;
import io.nop.batch.exp.config.JdbcConnectionConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ExportDbTool/ImportDbTool会把完整config序列化后打到INFO日志，
 * 其中包含jdbcConnection的明文password。序列化输出前必须脱敏。
 */
public class TestDbToolConfigMasking {

    private JdbcConnectionConfig newConn() {
        JdbcConnectionConfig conn = new JdbcConnectionConfig();
        conn.setJdbcUrl("jdbc:mysql://localhost:3306/test");
        conn.setUsername("root");
        conn.setPassword("secret-pass-123");
        conn.setDriverClassName("com.mysql.cj.jdbc.Driver");
        return conn;
    }

    @Test
    public void testExportConfigJsonMasksPassword() {
        ExportDbConfig config = new ExportDbConfig();
        config.setJdbcConnection(newConn());

        String json = DbToolHelper.toMaskedConfigJson(config);
        assertTrue(json.contains("root"), "username should remain visible for troubleshooting");
        assertTrue(json.contains("***"), "password should be replaced by mask");
        assertFalse(json.contains("secret-pass-123"), "plaintext password must not leak into log output");
    }

    @Test
    public void testImportConfigJsonMasksPassword() {
        ImportDbConfig config = new ImportDbConfig();
        config.setJdbcConnection(newConn());

        String json = DbToolHelper.toMaskedConfigJson(config);
        assertTrue(json.contains("root"));
        assertTrue(json.contains("***"));
        assertFalse(json.contains("secret-pass-123"));
    }

    @Test
    public void testMaskingDoesNotMutateOriginalConfig() {
        ExportDbConfig config = new ExportDbConfig();
        config.setJdbcConnection(newConn());

        DbToolHelper.toMaskedConfigJson(config);
        assertTrue(config.getJdbcConnection().getPassword().equals("secret-pass-123"),
                "masking must work on a copy, the config itself keeps the real password for building DataSource");
    }
}
