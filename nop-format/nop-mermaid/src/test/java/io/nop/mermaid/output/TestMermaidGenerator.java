package io.nop.mermaid.output;

import io.nop.api.core.exceptions.NopException;
import io.nop.mermaid.MermaidErrors;
import io.nop.mermaid.ast.MermaidClassNode;
import io.nop.mermaid.ast.MermaidComment;
import io.nop.mermaid.ast.MermaidFlowEdge;
import io.nop.mermaid.ast.MermaidFlowNode;
import io.nop.mermaid.ast.MermaidFlowSubgraph;
import io.nop.mermaid.ast.MermaidGanttTask;
import io.nop.mermaid.ast.MermaidParticipant;
import io.nop.mermaid.ast.MermaidPieItem;
import io.nop.mermaid.ast.MermaidStateNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    // ======================= P2-4.4-001 / P2-4.4-002 (plan 2405) =======================

    @Test
    public void testNonStaticClassMemberDoesNotNpe() {
        // getIsStatic() defaults to null; a direct unboxing NPEs on the common case
        String src = "classDiagram\n class Foo [\n +name : String\n ]";
        var doc = new io.nop.mermaid.parse.MermaidASTParser().parseFromText(null, src);
        MermaidGenerator generator = new MermaidGenerator();
        assertDoesNotThrow(() -> generator.visit(doc));
        assertTrue(generator.getResult().contains("class Foo ["));
    }

    @Test
    public void testIdentifierValidatedAtAllAppendPoints() {
        String bad = "a-b"; // '-' is outside Identifier_

        assertThrows(NopException.class, () -> {
            MermaidFlowNode node = new MermaidFlowNode();
            node.setId(bad);
            new MermaidGenerator().visitMermaidFlowNode(node);
        });
        assertThrows(NopException.class, () -> {
            MermaidFlowEdge node = new MermaidFlowEdge();
            node.setFrom(bad);
            node.setTo("b");
            new MermaidGenerator().visitMermaidFlowEdge(node);
        });
        assertThrows(NopException.class, () -> {
            MermaidFlowEdge node = new MermaidFlowEdge();
            node.setFrom("a");
            node.setTo(bad);
            new MermaidGenerator().visitMermaidFlowEdge(node);
        });
        assertThrows(NopException.class, () -> {
            MermaidParticipant node = new MermaidParticipant();
            node.setName(bad);
            new MermaidGenerator().visitMermaidParticipant(node);
        });
        assertThrows(NopException.class, () -> {
            MermaidClassNode node = new MermaidClassNode();
            node.setClassName(bad);
            new MermaidGenerator().visitMermaidClassNode(node);
        });
        assertThrows(NopException.class, () -> {
            MermaidFlowSubgraph node = new MermaidFlowSubgraph();
            node.setId(bad);
            node.setStatements(new java.util.ArrayList<>());
            new MermaidGenerator().visitMermaidFlowSubgraph(node);
        });
        assertThrows(NopException.class, () -> {
            MermaidStateNode node = new MermaidStateNode();
            node.setId(bad);
            new MermaidGenerator().visitMermaidStateNode(node);
        });
        assertThrows(NopException.class, () -> {
            MermaidGanttTask node = new MermaidGanttTask();
            node.setId(bad);
            node.setTitle("t");
            new MermaidGenerator().visitMermaidGanttTask(node);
        });
    }

    @Test
    public void testEmptyIdentifierFailsLoudly() {
        NopException ex = assertThrows(NopException.class, () -> MermaidGenerator.id(""));
        assertEquals(MermaidErrors.ERR_MERMAID_INVALID_IDENTIFIER.getErrorCode(), ex.getErrorCode());
    }
}
