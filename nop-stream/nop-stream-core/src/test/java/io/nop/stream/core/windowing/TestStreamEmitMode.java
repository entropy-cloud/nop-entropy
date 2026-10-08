/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.windowing;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * WI37 (D11 release): the xdef declared surface is the uppercase constant names
 * {@code UPDATE|CLOSE} (F-1: the xdef enum dict matches constant names); the
 * case-insensitive {@link StreamEmitMode#fromName} serves the typed-binding layer and
 * must accept both case domains while rejecting unknown values with a typed error.
 */
public class TestStreamEmitMode {

    @Test
    void lowercaseFormsResolveAtTypedBindingLayer() {
        assertEquals(StreamEmitMode.UPDATE, StreamEmitMode.fromName("update"));
        assertEquals(StreamEmitMode.CLOSE, StreamEmitMode.fromName("close"));
    }

    @Test
    void uppercaseXdefDefaultResolves() {
        assertEquals(StreamEmitMode.UPDATE, StreamEmitMode.fromName("UPDATE"));
        assertEquals(StreamEmitMode.CLOSE, StreamEmitMode.fromName("CLOSE"));
    }

    @Test
    void mixedCaseResolves() {
        assertEquals(StreamEmitMode.UPDATE, StreamEmitMode.fromName("Update"));
        assertEquals(StreamEmitMode.CLOSE, StreamEmitMode.fromName("cLoSe"));
    }

    @Test
    void nullResolvesToNull() {
        assertNull(StreamEmitMode.fromName(null));
    }

    @Test
    void unknownValueFailsTyped() {
        NopException ex = assertThrows(NopException.class,
                () -> StreamEmitMode.fromName("final"));
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode());
    }
}
