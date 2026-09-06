package io.nop.codegen.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestCodeBlock {

    @Test
    public void testAppendThreeTimesKeepsAllText() {
        CodeBlock block = new CodeBlock(null);
        block.append("a");
        block.append("b");
        block.append("c");

        // 修复前：第三次 append 重建 buf 为 this.text + "c"，中间追加的 "b" 丢失，返回 "ac"
        assertEquals("abc", block.getText());
    }

    @Test
    public void testAppendEmptyTextIgnored() {
        CodeBlock block = new CodeBlock(null);
        block.append("a");
        block.append("");
        block.append(null);

        assertEquals("a", block.getText());
    }
}
