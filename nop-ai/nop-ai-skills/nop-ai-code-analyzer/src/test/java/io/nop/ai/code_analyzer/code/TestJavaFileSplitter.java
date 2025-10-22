package io.nop.ai.code_analyzer.code;

import io.nop.ai.core.commons.splitter.IAiTextSplitter;
import io.nop.commons.util.FileHelper;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestJavaFileSplitter extends BaseTestCase {
    @Test
    public void testSplit() {
        File file = new File(getModuleDir(), "../../../nop-core/src/main/java/io/nop/core/lang/xml/XNode.java");

        IAiTextSplitter.SplitOptions options = new IAiTextSplitter.SplitOptions();
        options.setMaxContentSize(20000);
        options.setMaxElementsPerChunk(20);

        JavaFileSplitter splitter = new JavaFileSplitter();
        String javaCode = FileHelper.readText(file, null);
        List<IAiTextSplitter.SplitChunk> chunks = splitter.split(null, javaCode, options);
        assertTrue(chunks.size() > 1);
        chunks.forEach(chunk -> {
            System.out.println(chunk.getContent());
        });
    }
}
