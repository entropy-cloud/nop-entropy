package io.nop.codegen;

import io.nop.api.core.ApiConfigs;
import io.nop.api.core.config.AppConfig;
import io.nop.commons.util.DateHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.IFile;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static io.nop.api.core.util.ApiStringHelper.strip;
import static io.nop.core.resource.ResourceHelper.readText;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestXCodeGenerator extends BaseTestCase {
    @BeforeAll
    public static void setUp() {
        AppConfig.getConfigProvider().updateConfigValue(ApiConfigs.CFG_EXCEPTION_FILL_STACKTRACE, true);
        System.out.println("setUp");
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void tearDown() {
        CoreInitialization.destroy();
    }

    @Test
    public void testSubDir() {
        IFile targetDir = getTargetResource("/codegen");
        ResourceHelper.deleteAll(targetDir);
        XCodeGenerator gen = new XCodeGenerator("/test/tpls", targetDir.getStdPath());

        IEvalScope scope = XLang.newEvalScope();
        LocalDateTime time = LocalDateTime.now();
        scope.setLocalValue(null, "currentTime", time);

        gen.execute("/{child}", scope);

        assertTrue(!targetDir.getResource("other").exists());
        assertTrue(!targetDir.getResource("@init.xrun").exists());

        assertEquals("child:child1", strip(readText(targetDir.getResource("child1/_child.txt"))));
        assertEquals("child:child2", strip(readText(targetDir.getResource("child2/_child.txt"))));

        assertTrue(!targetDir.getResource("child1/disabled.txt").exists());
        assertTrue(!targetDir.getResource("child2/disabled.txt").exists());

        assertEquals("__XGEN_FORCE_OVERRIDE__\nenabled:child1",
                normalizeCRLF(strip(readText(targetDir.getResource("child1/enabled.txt")))));
        assertEquals("__XGEN_FORCE_OVERRIDE__\nenabled:child2",
                normalizeCRLF(strip(readText(targetDir.getResource("child2/enabled.txt")))));

        assertEquals("data", readText(targetDir.getResource("child1/_gen/data.txt")));
        assertEquals(time, DateHelper.parseDataTime(readText(targetDir.getResource("child1/_gen/date.txt"))));

        assertTrue(!targetDir.getResource("child1/date2.txt").exists());
        assertEquals(time, DateHelper.parseDataTime(readText(targetDir.getResource("child1/date1.txt"))));
        assertEquals(time, DateHelper.parseDataTime(readText(targetDir.getResource("child2/date1.txt"))));
    }

    @Test
    public void testCopy() {
        IFile targetDir = getTargetResource("/codegen");
        ResourceHelper.deleteAll(targetDir);
        XCodeGenerator gen = new XCodeGenerator("/test/tpls", targetDir.getStdPath());

        IEvalScope scope = XLang.newEvalScope();
        LocalDateTime time = LocalDateTime.now();
        scope.setLocalValue(null, "currentTime", time);

        gen.execute("/", scope);

        assertTrue(targetDir.getResource("other").exists());
        assertTrue(!targetDir.getResource("@init.xrun").exists());
        assertEquals("other", ResourceHelper.readText(targetDir.getResource("other/other.txt")));
    }

    @Test
    public void testGenText() {
        IFile targetDir = getTargetResource("/codegen");
        ResourceHelper.deleteAll(targetDir);
        XCodeGenerator gen = new XCodeGenerator("/test/gen", targetDir.getStdPath());

        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(null, "moduleName", "aa");

        gen.execute("/", scope);

        String text = ResourceHelper.readText(targetDir.getResource("web.i18n.yaml"));
        assertEquals("\n" +
                "\"x:extends\": _aa-web.i18n.yaml\n" +
                "\n" +
                "# key: \"value\"\n", normalizeCRLF(text));
    }
}
