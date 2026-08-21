package io.nop.mermaid.output;

import io.nop.api.core.exceptions.NopException;
import io.nop.mermaid.ast.MermaidClassNode;
import io.nop.mermaid.ast.MermaidComment;
import io.nop.mermaid.ast.MermaidPieItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归：CodeBuilder.line(String) 走 MessageFormat 格式化，class/style 语句的花括号
 * 必抛 Unmatched braces，含单引号的文本被静默吞字符
 */
public class TestMermaidGenerator {

    @Test
    public void testClassStatementNotBrokenByMessageFormat() {
        MermaidClassNode node = new MermaidClassNode();
        node.setClassName("Foo");

        MermaidGenerator generator = new MermaidGenerator();
        generator.visitMermaidClassNode(node);
        String result = generator.getResult();

        // 修复前 MessageFormat.format("class Foo {") 抛 IllegalArgumentException
        assertTrue(result.contains("class Foo"), result);
    }

    @Test
    public void testSingleQuoteNotSwallowed() {
        MermaidComment comment = new MermaidComment();
        comment.setContent("don't panic");

        MermaidGenerator generator = new MermaidGenerator();
        generator.visitMermaidComment(comment);
        String result = generator.getResult();

        // 修复前 MessageFormat 把单引号当转义前缀静默吞掉；
        // 修复后单引号保留（经 escapeMermaidString 转义为 \' 形式）
        assertTrue(result.contains("don\\'t panic"), result);
    }

    @Test
    public void testIllegalIdentifierRejected() {
        MermaidClassNode node = new MermaidClassNode();
        node.setClassName("订单类"); // 非ASCII标识符超出文法词法范围

        MermaidGenerator generator = new MermaidGenerator();
        assertThrows(NopException.class, () -> generator.visitMermaidClassNode(node));
    }

    @Test
    public void testPieItemWithValue() {
        MermaidPieItem item = new MermaidPieItem();
        item.setLabel("a");
        item.setValue(1);

        MermaidGenerator generator = new MermaidGenerator();
        assertDoesNotThrow(() -> generator.visitMermaidPieItem(item));
    }
}
