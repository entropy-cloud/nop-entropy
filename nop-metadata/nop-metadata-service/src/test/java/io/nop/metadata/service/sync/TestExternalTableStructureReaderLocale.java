package io.nop.metadata.service.sync;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * INV-LOCALE（Cycle 2 / P1-B，adjudication-table-cycle2 §2 #40）：DB 产品名归一化（方言路由输入）的
 * tr-TR 默认 locale 回归（latent 形态钉住：mysql/postgresql/h2 产品名域现无 i/I 字形，
 * 机械修复 Locale.ROOT 后路由语义不漂移）。
 */
public class TestExternalTableStructureReaderLocale {

    @Test
    public void turkishLocaleDialectRoutingUnchanged() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertTrue(ExternalTableStructureReader.isSupportedDialect("MySQL"));
            assertTrue(ExternalTableStructureReader.isSupportedDialect("PostgreSQL"));
            assertTrue(ExternalTableStructureReader.isSupportedDialect("H2"));
            assertTrue(ExternalTableStructureReader.isSupportedDialect("TDSQL for MySQL"),
                    "mysql-compatible family must route to mysql dialect under tr-TR");
            assertFalse(ExternalTableStructureReader.isSupportedDialect("Oracle"));
            assertFalse(ExternalTableStructureReader.isSupportedDialect("DB2"));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    public void defaultLocaleDialectRoutingBaseline() {
        assertTrue(ExternalTableStructureReader.isSupportedDialect("MySQL"));
        assertFalse(ExternalTableStructureReader.isSupportedDialect("Oracle"));
    }
}
