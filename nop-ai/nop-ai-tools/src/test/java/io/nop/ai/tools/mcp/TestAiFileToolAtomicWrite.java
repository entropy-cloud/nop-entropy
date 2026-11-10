package io.nop.ai.tools.mcp;

import io.nop.ai.tools.utils.AiToolsHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.ai.tools.xdsl.DslToolImpl;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.converter.registration.ConverterRegistrationBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2557 C1-005 (ruling C) carrier, tools-side extension face: the save
 * paths of {@link AiFileTool#saveNopFile} and {@link DslToolImpl#saveDslFile}
 * go through the atomic temp+rename template (same source as
 * {@code ThoughtStorage.atomicWriteText}) for local {@code FileResource}
 * targets — a failed write preserves the previous file content and no temp
 * residue is left behind. Template pins mirror
 * {@code TestLocalToolFileSystemAtomicWrite} (toolkit-side mavenvfs face is a
 * sibling agent's scope).
 */
public class TestAiFileToolAtomicWrite extends JunitBaseTestCase {

    private AiFileTool tool;
    private File baseDir;

    @TempDir
    File tempDir;

    @BeforeEach
    public void setUp() throws Exception {
        new ConverterRegistrationBean().register();
        tool = new AiFileTool();
        baseDir = new File(tempDir, "base");
        Files.createDirectories(baseDir.toPath());
        tool.setBaseDir(baseDir.getPath());
    }

    private int countTempFiles(File dir) {
        File[] entries = dir.listFiles();
        if (entries == null) return 0;
        int n = 0;
        for (File f : entries) {
            if (f.getName().contains(".tmp")) n++;
        }
        return n;
    }

    @Test
    public void testAtomicWriteTemplateFailurePreservesOldContent() throws Exception {
        // template pin: target in non-replaceable form (non-empty directory) —
        // the final move must fail loud and the previous "content" survives
        File dirTarget = new File(tempDir, "t.txt");
        assertTrue(dirTarget.mkdir());
        File child = new File(dirTarget, "keep.txt");
        Files.writeString(child.toPath(), "keep", StandardCharsets.UTF_8);

        assertThrows(NopException.class, () -> AiToolsHelper.atomicWriteText(dirTarget, "x"));

        assertTrue(dirTarget.isDirectory(), "old directory target must survive the failed write");
        assertEquals("keep", new String(Files.readAllBytes(child.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void testAtomicWriteTemplateReplacesAndLeavesNoTempResidue() throws Exception {
        File target = new File(tempDir, "a.txt");
        Files.writeString(target.toPath(), "old", StandardCharsets.UTF_8);

        AiToolsHelper.atomicWriteText(target, "new content");

        assertEquals("new content", new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8));
        assertEquals(0, countTempFiles(tempDir), "no temp files may be left behind");
    }

    @Test
    public void testSaveNopFileAtomicReplaceKeepsNoTempResidue() throws Exception {
        File target = new File(baseDir, "svc.xml");
        Files.writeString(target.toPath(), "<old/>", StandardCharsets.UTF_8);

        String result = tool.saveNopFile("svc.xml", null, "<new-root/>", false);

        assertEquals("SUCCESS", result);
        assertEquals("<new-root/>", new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8)
                .trim());
        assertEquals(0, countTempFiles(baseDir), "no temp files may be left in the sandbox dir");
    }

    @Test
    public void testSaveNopFileFailurePreservesOldContent() throws Exception {
        File target = new File(baseDir, "m.xml");
        Files.writeString(target.toPath(), "<old/>", StandardCharsets.UTF_8);

        // merge with malformed content throws before any write; the old file
        // must survive untouched (fail-atomicity of the save path)
        assertThrows(Exception.class,
                () -> tool.saveNopFile("m.xml", "xml", "<<<not-xml", true));

        assertEquals("<old/>", new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8));
        assertEquals(0, countTempFiles(baseDir));
    }

    /**
     * DslToolImpl.saveDslFile pin (ruling C extension face): the save goes
     * through the atomic template — content replaces the old file completely
     * and no temp residue remains. Same-type save keeps the conversion facet
     * out of the pin (conversion itself is pinned in {@code TestDslToolImpl}).
     */
    @Test
    public void testDslToolSaveDslFileAtomicReplace() throws Exception {
        File targetDir = new File(tempDir, "proj");
        assertTrue(targetDir.mkdirs() || targetDir.isDirectory());
        File target = new File(targetDir, "test.xml");
        Files.writeString(target.toPath(), "<!-- stale previous content -->", StandardCharsets.UTF_8);

        DslToolImpl dslTool = DslToolImpl.createForDir(targetDir);
        dslTool.saveDslFile("/test.xml", "xml", "<root><item>a</item></root>");

        String written = new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8);
        assertTrue(written.contains("<root>"), "new content must be written: " + written);
        assertFalse(written.contains("stale previous content"),
                "old content must be fully replaced by the atomic write");
        assertEquals(0, countTempFiles(targetDir), "no temp files may be left behind");
    }
}
