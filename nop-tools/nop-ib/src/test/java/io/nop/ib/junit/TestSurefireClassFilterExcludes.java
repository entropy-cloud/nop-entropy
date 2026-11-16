package io.nop.ib.junit;

import io.nop.ib.model.IbModule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Defect 30 regression pin: surefire &lt;excludes&gt; must drop excluded classes
 * from the dispatch surface. spring-batch's surefire excludes
 * **&#47;*IntegrationTests.java and **&#47;*FunctionalTests.java (they run via
 * failsafe at the integration-test phase); without the exclude the ib sweep
 * over-ran mvn's test phase by 75 classes / 249 tests on spring-batch-core.
 */
class TestSurefireClassFilterExcludes {

    private IbModule module(List<String> excludes) {
        IbModule m = new IbModule();
        m.getSurefireExcludes().addAll(excludes);
        return m;
    }

    @Test
    void excludesDropIntegrationAndFunctionalTests() {
        IbModule m = module(List.of("**/*IntegrationTests.java", "**/*FunctionalTests.java"));
        List<String> out = SurefireClassFilter.filter(List.of(
                "a.SimpleJobTests",
                "a.ChunkOrientedStepScanModeRestartIntegrationTests",
                "a.SystemCommandTaskletIntegrationTests",
                "b.SomethingFunctionalTests",
                "b.Helper"), m);
        assertTrue(out.contains("a.SimpleJobTests"), "non-excluded test kept: " + out);
        assertFalse(out.contains("a.ChunkOrientedStepScanModeRestartIntegrationTests"),
                "*IntegrationTests must be excluded, got " + out);
        assertFalse(out.contains("a.SystemCommandTaskletIntegrationTests"),
                "*IntegrationTests must be excluded, got " + out);
        assertFalse(out.contains("b.SomethingFunctionalTests"),
                "*FunctionalTests must be excluded, got " + out);
        assertFalse(out.contains("b.Helper"),
                "helper not matching default patterns stays out, got " + out);
    }
}
