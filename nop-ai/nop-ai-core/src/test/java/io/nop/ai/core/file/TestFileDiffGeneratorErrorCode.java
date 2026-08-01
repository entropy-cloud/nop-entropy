package io.nop.ai.core.file;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_FILE_INVALID_EDIT_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused value-level test for the error code introduced by plan
 * 2026-08-01-0936-2: {@code FileDiffGenerator.determineDiffType} defensive
 * default branch. The branch is unreachable through the public API today
 * (KEEP edits are filtered before the call), so it is exercised via
 * reflection to prove the converted exception is runtime-reachable.
 */
public class TestFileDiffGeneratorErrorCode {

    @Test
    public void testInvalidEditTypeRejectedWithErrorCode() throws Exception {
        Class<?> editTypeClass = Class.forName("io.nop.ai.core.file.FileDiffGenerator$EditType");
        Object keep = Enum.valueOf((Class<Enum>) editTypeClass, "KEEP");

        Method method = FileDiffGenerator.class.getDeclaredMethod("determineDiffType", editTypeClass);
        method.setAccessible(true);

        InvocationTargetException ite = assertThrows(InvocationTargetException.class,
                () -> method.invoke(new FileDiffGenerator(), keep));
        Throwable cause = ite.getCause();
        assertTrue(cause instanceof NopException,
                "defensive branch must throw NopException, got " + cause.getClass());
        NopException ex = (NopException) cause;
        assertEquals(ERR_AI_FILE_INVALID_EDIT_TYPE.getErrorCode(), ex.getErrorCode(),
                "invalid edit type must carry ERR_AI_FILE_INVALID_EDIT_TYPE");
        assertTrue(ex.getMessage().contains("Invalid edit type"),
                "message must preserve the original verbatim text");
    }
}
