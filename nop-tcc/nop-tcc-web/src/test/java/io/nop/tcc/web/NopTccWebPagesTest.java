
package io.nop.tcc.web;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.web.page.PageProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class NopTccWebPagesTest extends JunitBaseTestCase {

    @Inject
    PageProvider pageProvider;

    @Test
    public void testValidateAllPages() {
        pageProvider.validateAllPages();
    }
}
