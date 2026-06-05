package io.nop.code.service.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.code.service.NopCodeErrors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestServiceLayerErrorPaths {

    @Test
    void testNopCodeErrors_invalidPath_hasCorrectCode() {
        NopException ex = new NopException(NopCodeErrors.ERR_CODE_INVALID_PATH).param("path", "../etc/passwd");
        assertEquals("nop.err.code.invalid-path", ex.getErrorCode());
        assertTrue(ex.getParam("path").equals("../etc/passwd"));
    }

    @Test
    void testNopCodeErrors_noAnalyzer_hasCorrectCode() {
        NopException ex = new NopException(NopCodeErrors.ERR_NO_ANALYZER_FOR_FILE)
                .param("indexId", "").param("filePath", "test.xyz");
        assertEquals("nop.err.code.no-analyzer-for-file", ex.getErrorCode());
    }

    @Test
    void testNopCodeErrors_deadCodeDetector_hasCorrectCode() {
        NopException ex = new NopException(NopCodeErrors.ERR_CODE_DEAD_CODE_DETECTOR_NOT_AVAILABLE)
                .param("indexId", "no-idx");
        assertEquals("nop.err.code.dead-code-detector-not-available", ex.getErrorCode());
    }

    @Test
    void testNopCodeErrors_incrementalFailed_hasCorrectCode() {
        NopException ex = new NopException(NopCodeErrors.ERR_INCREMENTAL_FAILED)
                .param("indexId", "no-idx");
        assertEquals("nop.err.code.incremental-failed", ex.getErrorCode());
    }

    @Test
    void testNopCodeErrors_flowDetector_hasCorrectCode() {
        NopException ex = new NopException(NopCodeErrors.ERR_CODE_FLOW_DETECTOR_NOT_AVAILABLE);
        assertEquals("nop.err.code.flow-detector-not-available", ex.getErrorCode());
    }

    @Test
    void testNopCodeErrors_sourceCodeTooLarge_hasCorrectCode() {
        NopException ex = new NopException(NopCodeErrors.ERR_CODE_SOURCE_CODE_TOO_LARGE)
                .param("filePath", "Test.java");
        assertEquals("nop.err.code.source-code-too-large", ex.getErrorCode());
        assertEquals("Test.java", ex.getParam("filePath"));
    }
}
