package io.nop.record.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.record.RecordErrors;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestFailFastAttributes extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testTerminatorFailsFast() {
        NopException e = assertThrows(NopException.class, () -> new DslModelParser()
                .parseFromVirtualPath("/test/record/test-fail-fast-attrs.record-file.xml"));
        assertTrue(e.getErrorCode().equals(RecordErrors.ERR_RECORD_ATTRIBUTE_NOT_IMPLEMENTED.getErrorCode()));
        assertEquals("terminator", e.getParam(RecordErrors.ARG_ATTRIBUTE_NAME));
        assertEquals("a", e.getParam(RecordErrors.ARG_FIELD_NAME));
    }
}
