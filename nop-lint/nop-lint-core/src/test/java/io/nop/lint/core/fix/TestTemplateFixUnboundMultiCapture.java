package io.nop.lint.core.fix;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.pattern.MetaVarEnv;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The multi-capture render guard (plan 11 Phase 2, audit finding C2): a
 * sequence capture bound on a DIFFERENT any-branch is unbound at apply time —
 * the render must raise the module exception naming rule and capture, never
 * a bare NPE (the single-capture path has had this guard since item 25).
 */
class TestTemplateFixUnboundMultiCapture {

    @Test
    void unboundMultiCaptureFailsClosedNamingRuleAndCapture() {
        TemplateFix fix = TemplateFix.compile("demo/x", "m2($$$A);", Set.of(), Set.of("A"));

        NopLintException ex = assertThrows(NopLintException.class,
                () -> fix.apply(new MetaVarEnv(), "m2();".getBytes(StandardCharsets.UTF_8)));
        assertTrue(ex.getMessage().contains("sequence capture 'A'"), ex.getMessage());
        assertTrue(ex.getMessage().contains("demo/x"), ex.getMessage());
    }
}
