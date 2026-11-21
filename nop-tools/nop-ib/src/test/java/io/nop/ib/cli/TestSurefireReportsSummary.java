package io.nop.ib.cli;

import io.nop.ib.model.IbModel;
import io.nop.ib.model.IbModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins the maven-full fallback summary source: `-q` suppresses surefire's
 * console "Tests run: ..." lines, so the console-only parse reported run=0
 * while surefire had actually executed the suite. The authoritative source is
 * the module's target/surefire-reports XML.
 */
class TestSurefireReportsSummary {

    @TempDir
    Path repo;

    private IbModel modelWithModule() throws Exception {
        IbModule mod = new IbModule();
        mod.setArtifactId("demo");
        mod.setBasedir("demo");
        IbModel model = new IbModel();
        model.setRepoRoot(repo.toString());
        model.addModule(mod);
        return model;
    }

    @Test
    void aggregatesSurefireXmlTotals() throws Exception {
        IbModel model = modelWithModule();
        Path reports = repo.resolve("demo/target/surefire-reports");
        Files.createDirectories(reports);
        String head = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<testsuite xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                + " version=\"3.0\" name=\"io.demo.A\" time=\"1\" tests=\"12\""
                + " errors=\"1\" skipped=\"2\" failures=\"3\">";
        Files.writeString(reports.resolve("TEST-io.demo.A.xml"), head);
        String head2 = "<?xml version=\"1.0\"?>\n<testsuite name=\"io.demo.B\""
                + " tests=\"8\" errors=\"0\" skipped=\"0\" failures=\"0\">";
        Files.writeString(reports.resolve("TEST-io.demo.B.xml"), head2);

        int[] s = TestCommand.surefireReportsSummary(model, List.of("demo"));
        assertArrayEquals(new int[]{20, 3, 1, 2}, s);
    }

    @Test
    void nullWhenNoReports() throws Exception {
        assertNull(TestCommand.surefireReportsSummary(modelWithModule(), List.of("demo")));
    }
}
