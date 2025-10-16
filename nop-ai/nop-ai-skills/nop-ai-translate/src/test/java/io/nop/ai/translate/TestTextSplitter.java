package io.nop.ai.translate;

import io.nop.ai.core.commons.splitter.IAiTextSplitter;
import io.nop.ai.core.commons.splitter.MarkdownTextSplitter;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestTextSplitter extends JunitBaseTestCase {

    @Test
    public void testSplit() {
        File dir = getModuleDir();
        File file = new File(dir, "../../docs/theory/why-springbatch-is-bad.md");
        String text = FileHelper.readText(file, null);

        text = StringHelper.replace(text, "\r\n", "\n");

        IAiTextSplitter splitter = new MarkdownTextSplitter();
        List<IAiTextSplitter.SplitChunk> chunks = splitter.split(null, text, IAiTextSplitter.SplitOptions.create(2048));
        System.out.println(JsonTool.serialize(chunks, true));

        StringBuilder sb = new StringBuilder();
        chunks.forEach(chunk -> {
            System.out.println("**********************");
            System.out.println(chunk.getContent());
            sb.append(chunk.getContent());
        });
        assertEquals(text, sb.toString());
    }
}
