package io.nop.ioc.loader;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.ioc.api.IBeanContainerImplementor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Regression test: addResource should deduplicate with import.
 *
 * <p>Scenario: two app-*.beans.xml files are auto-discovered and loaded via
 * addResource. One file imports the other. Without dedup, the imported file
 * is loaded twice, causing ERR_IOC_DUPLICATE_BEAN_DEFINITION.
 */
public class TestImportDedup extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testAddResourceDedupWithImport() {
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(attachmentResource("test_import_dedup_base.beans.xml"));
        builder.addResource(attachmentResource("test_import_dedup_importer.beans.xml"));
        IBeanContainerImplementor container = builder.build("test");
        container.start();
        assertNotNull(container.getBeanClass("dedupTestBean"));
        container.stop();
    }
}
