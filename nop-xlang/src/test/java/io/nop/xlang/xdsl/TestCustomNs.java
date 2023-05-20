package io.nop.xlang.xdsl;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.XLangErrors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCustomNs extends BaseTestCase {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testValidate() {
        IResource resource = attachmentResource("test.xmeta");
        try {
            DslNodeLoader.INSTANCE.loadFromResource(resource);
            assertTrue(false);
        } catch (NopException e) {
            assertEquals(XLangErrors.ERR_XDSL_ATTR_NOT_ALLOWED.getErrorCode(), e.getErrorCode());
        }
    }
}