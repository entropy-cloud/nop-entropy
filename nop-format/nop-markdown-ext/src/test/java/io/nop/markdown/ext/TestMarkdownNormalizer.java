package io.nop.markdown.ext;

import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestMarkdownNormalizer extends BaseTestCase {
    @Test
    public void testNormalize() {
        String text = "## `$`x$\\delta +1 $  \n\n\n\n ### some \n # value \n ````xml\n <a/> \n````\na\n$$\n \\alpha\n$$";
        String normalized = new MarkdownNormalizer().normalizeText(text);
        System.out.println(normalized);
        Assertions.assertEquals(normalizeCRLF(normalized).trim(), normalizeCRLF(attachmentText("normalized.md")).trim());
    }

    // 内容含```的代码块：围栏长度必须大于内容反引号串，否则重解析时提前闭合（不可逆破坏）
    @Test
    public void testFenceLengthPreservedForNestedBackticks() {
        String text = "````markdown\nbefore\n\n```\ncode inside\n```\n\nafter\n````";
        String normalized = new MarkdownNormalizer().normalizeText(text);

        // 归一化后围栏仍是4个反引号（修复前被压到3，代码块在内容的```处提前闭合）
        Assertions.assertTrue(normalized.startsWith("````"), normalized);
        Assertions.assertTrue(normalized.contains("code inside"), normalized);

        // 输出可再次解析且内容完整
        String renormalized = new MarkdownNormalizer().normalizeText(normalized);
        Assertions.assertTrue(renormalized.contains("code inside"), renormalized);
        Assertions.assertTrue(renormalized.contains("before"), renormalized);
        Assertions.assertTrue(renormalized.contains("after"), renormalized);
    }

    // 奇数个$的文本：不成对的起始$必须原样保留，归一化不能丢字符
    // （修复前起始$被消费后丢弃，"a$x$b$y"归一化成"a$x$by"少一个$）
    @Test
    public void testUnpairedDollarPreserved() {
        String normalized = new MarkdownNormalizer().normalizeText("a$x$b$y");
        // 三个$全部保留，内容与输入一致
        Assertions.assertEquals("a$x$b$y", normalizeCRLF(normalized).trim());
    }
}
