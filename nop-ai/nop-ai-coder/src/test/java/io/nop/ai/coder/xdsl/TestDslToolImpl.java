package io.nop.ai.coder.xdsl;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.converter.registration.ConverterRegistrationBean;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestDslToolImpl extends JunitBaseTestCase {

    @TempDir
    File tempDir;

    @Inject
    ConverterRegistrationBean registrationBean;

    @Test
    public void testLoadDslFileConvertsAiOrmToOrm() throws Exception {
        String sourceText = classpathResource("io/nop/ai/coder/test.ai-orm.xml").readText();
        File source = new File(tempDir, "model/test.ai-orm.xml");
        source.getParentFile().mkdirs();
        Files.write(source.toPath(), sourceText.getBytes(StandardCharsets.UTF_8));

        DslToolImpl tool = DslToolImpl.createForDir(tempDir);
        String converted = tool.loadDslFile("/model/test.ai-orm.xml", "orm.xml");

        assertTrue(converted.contains("<orm"), "converted content should be orm xml");
        assertTrue(converted.contains("<entity"), "converted content should contain entities");
    }

    @Test
    public void testLoadMalformedXmlFails() throws Exception {
        DslToolImpl tool = DslToolImpl.createForDir(tempDir);
        File badFile = new File(tempDir, "bad.xml");
        Files.write(badFile.toPath(), "<orm><entities></orm>".getBytes(StandardCharsets.UTF_8));

        assertThrows(Exception.class, () -> tool.loadDslFile("bad.xml", "xml"));
    }
}
