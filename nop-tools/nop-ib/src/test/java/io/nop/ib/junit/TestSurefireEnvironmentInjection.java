package io.nop.ib.junit;

import io.nop.ib.model.IbModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestSurefireEnvironmentInjection {

    /** defect 31: surefire environmentVariables must reach the fork process env. */
    @Test
    void injectsEnvironmentVariablesIntoProcessBuilder() {
        IbModule module = new IbModule();
        module.getSurefireEnvironmentVariables().put("DB_PASS", "foopass");
        module.getSurefireEnvironmentVariables().put("FOO", "foo");

        ProcessBuilder pb = new ProcessBuilder("true");
        JUnitForkExecutor.applySurefireEnvironment(pb, module);

        assertEquals("foopass", pb.environment().get("DB_PASS"));
        assertEquals("foo", pb.environment().get("FOO"));
    }
}
