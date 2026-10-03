package io.nop.mermaid.parse;

import io.nop.api.core.exceptions.NopException;
import io.nop.mermaid.ast.MermaidASTKind;
import io.nop.mermaid.ast.MermaidASTNode;
import io.nop.mermaid.ast.MermaidASTOptimizer;
import io.nop.mermaid.ast.MermaidASTProcessor;
import io.nop.mermaid.ast.MermaidClassNode;
import io.nop.mermaid.ast.MermaidDiagramType;
import io.nop.mermaid.ast.MermaidDirection;
import io.nop.mermaid.ast.MermaidDirectionStatement;
import io.nop.mermaid.ast.MermaidDocument;
import io.nop.mermaid.ast.MermaidEdgeType;
import io.nop.mermaid.ast.MermaidFlowEdge;
import io.nop.mermaid.ast.MermaidFlowNode;
import io.nop.mermaid.ast.MermaidFlowSubgraph;
import io.nop.mermaid.ast.MermaidGanttTask;
import io.nop.mermaid.ast.MermaidNodeShape;
import io.nop.mermaid.ast.MermaidParticipant;
import io.nop.mermaid.ast.MermaidPieItem;
import io.nop.mermaid.ast.MermaidSequenceMessage;
import io.nop.mermaid.ast.MermaidStateNode;
import io.nop.mermaid.ast.MermaidStyleAttribute;
import io.nop.mermaid.ast.MermaidStyleStatement;
import io.nop.mermaid.ast.MermaidVisibility;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * mermaid 文本解析语义：文法可解析语句（flowchart节点/边、participant、subgraph、style、pie、gantt、
 * class/state/direction/sequence message）解析出的 AST 字段必须与源文本一一对应；
 * 注释走 HIDDEN 通道不产生语句；非法文法抛 NopException。
 *
 * 文法缺陷修复回归（plan 2306 项 6）：CLASS/STATE token 重复定义已拆分为
 * CLASS/STATE（图表类型）与 CLASS_KEYWORD/STATE_KEYWORD（语句关键字）；DIRECTION 已补词法规则；
 * sequenceMessage 与 flowEdge 已按 "to 后置 COLON message" 结构区分。
 */
public class TestMermaidASTParser {

    private final MermaidASTParser parser = new MermaidASTParser();

    private MermaidDocument parse(String text) {
        return parser.parseFromText(null, text);
    }

    @Test
    public void testParseFlowchartNodesAndEdges() {
        MermaidDocument doc = parse("flowchart\n a(\"Start\") round\n b\n a --> b");
        assertEquals(MermaidDiagramType.FLOWCHART, doc.getType());

        List<io.nop.mermaid.ast.MermaidStatement> stmts = doc.getStatements();
        assertEquals(3, stmts.size());

        MermaidFlowNode a = (MermaidFlowNode) stmts.get(0);
        assertEquals("a", a.getId());
        assertEquals("Start", a.getText());
        assertEquals(MermaidNodeShape.ROUND, a.getShape());

        MermaidFlowNode b = (MermaidFlowNode) stmts.get(1);
        assertEquals("b", b.getId());
        assertNull(b.getText());
        assertNull(b.getShape());

        MermaidFlowEdge edge = (MermaidFlowEdge) stmts.get(2);
        assertEquals("a", edge.getFrom());
        assertEquals("b", edge.getTo());
        assertEquals(MermaidEdgeType.ARROW, edge.getEdgeType());
        assertNull(edge.getLabel());
    }

    @Test
    public void testGraphAliasMapsToFlowchartType() {
        // FLOWCHART token 同时接受 'flowchart' 与 'graph' 别名
        MermaidDocument doc = parse("graph\n a --> b");
        assertEquals(MermaidDiagramType.FLOWCHART, doc.getType());
        assertEquals(1, doc.getStatements().size());
    }

    @Test
    public void testEdgeLabelAndDottedType() {
        // 文法中边 label 位于 from 与 to 之间
        MermaidDocument doc = parse("flowchart\n a -.-> \"retry\" b");
        MermaidFlowEdge edge = (MermaidFlowEdge) doc.getStatements().get(0);
        assertEquals(MermaidEdgeType.DOTTED, edge.getEdgeType());
        assertEquals("retry", edge.getLabel());
        assertEquals("a", edge.getFrom());
        assertEquals("b", edge.getTo());
    }

    @Test
    public void testOpenArrowEdgeType() {
        MermaidDocument doc = parse("flowchart\n a ->> b");
        MermaidFlowEdge edge = (MermaidFlowEdge) doc.getStatements().get(0);
        assertEquals(MermaidEdgeType.OPEN_ARROW, edge.getEdgeType());
    }

    @Test
    public void testSequenceMessageDispatchesToSequenceMessageNode() {
        // 回归覆盖 wi10#3（plan 2306 项 6）：sequenceMessage 与 flowEdge 规则已按
        // "to 后置 COLON message" 结构区分，A ->> B: "hello" 必须分发到 MermaidSequenceMessage
        MermaidDocument doc = parse("sequenceDiagram\n A ->> B: \"hello\"");
        assertEquals(MermaidDiagramType.SEQUENCE, doc.getType());
        assertEquals(1, doc.getStatements().size());
        assertEquals(MermaidASTKind.MermaidSequenceMessage, doc.getStatements().get(0).getASTKind());

        MermaidSequenceMessage msg = (MermaidSequenceMessage) doc.getStatements().get(0);
        assertEquals("A", msg.getFrom());
        assertEquals("B", msg.getTo());
        assertEquals(MermaidEdgeType.OPEN_ARROW, msg.getEdgeType());
        assertEquals("hello", msg.getMessage());
    }

    @Test
    public void testSequenceMessageWithoutColonStillParsesAsFlowEdge() {
        // 无 COLON 后缀的边语法仍按 flowEdge 承载（两规则结构可区分后各归其位）
        MermaidDocument doc = parse("sequenceDiagram\n A ->> \"hello\" B");
        assertEquals(MermaidDiagramType.SEQUENCE, doc.getType());
        assertEquals(MermaidASTKind.MermaidFlowEdge, doc.getStatements().get(0).getASTKind());

        MermaidFlowEdge edge = (MermaidFlowEdge) doc.getStatements().get(0);
        assertEquals("A", edge.getFrom());
        assertEquals("B", edge.getTo());
        assertEquals(MermaidEdgeType.OPEN_ARROW, edge.getEdgeType());
        assertEquals("hello", edge.getLabel());
    }

    @Test
    public void testParseParticipantWithAlias() {
        MermaidDocument doc = parse("sequenceDiagram\n participant A as \"Alice\"\n participant B");
        assertEquals(MermaidDiagramType.SEQUENCE, doc.getType());

        MermaidParticipant first = (MermaidParticipant) doc.getStatements().get(0);
        assertEquals("A", first.getName());
        assertEquals("Alice", first.getAlias());

        MermaidParticipant second = (MermaidParticipant) doc.getStatements().get(1);
        assertEquals("B", second.getName());
        assertNull(second.getAlias());
    }

    @Test
    public void testParseSubgraphWithNestedStatements() {
        MermaidDocument doc = parse("flowchart\n subgraph s1 \"Group\" [\n a --> b\n ]");
        assertEquals(1, doc.getStatements().size());

        MermaidFlowSubgraph sub = (MermaidFlowSubgraph) doc.getStatements().get(0);
        assertEquals("s1", sub.getId());
        assertEquals("Group", sub.getTitle());
        assertEquals(1, sub.getStatements().size());

        MermaidFlowEdge edge = (MermaidFlowEdge) sub.getStatements().get(0);
        assertEquals("a", edge.getFrom());
        assertEquals("b", edge.getTo());
    }

    @Test
    public void testParseStyleStatement() {
        MermaidDocument doc = parse("flowchart\n style \"a\" [\n fill : \"#f9f\"\n ]");
        assertEquals(1, doc.getStatements().size());

        MermaidStyleStatement style = (MermaidStyleStatement) doc.getStatements().get(0);
        assertEquals("a", style.getTarget());

        List<MermaidStyleAttribute> attrs = style.getAttributes();
        assertEquals(1, attrs.size());
        assertEquals("fill", attrs.get(0).getName());
        assertEquals("#f9f", attrs.get(0).getValue());
    }

    @Test
    public void testParsePieItems() {
        MermaidDocument doc = parse("pie\n pie \"A\" : 30\n pie \"B\" : 70.5");
        assertEquals(MermaidDiagramType.PIE, doc.getType());

        MermaidPieItem a = (MermaidPieItem) doc.getStatements().get(0);
        assertEquals("A", a.getLabel());
        assertEquals(30, a.getValue().intValue());

        MermaidPieItem b = (MermaidPieItem) doc.getStatements().get(1);
        assertEquals("B", b.getLabel());
        assertEquals(70.5, b.getValue().doubleValue(), 1e-9);
    }

    @Test
    public void testParseGanttTask() {
        MermaidDocument doc = parse("gantt\n task t1 : \"Design\", \"2026-01-01\", \"3d\"");
        assertEquals(MermaidDiagramType.GANTT, doc.getType());

        MermaidGanttTask task = (MermaidGanttTask) doc.getStatements().get(0);
        assertEquals("t1", task.getId());
        assertEquals("Design", task.getTitle());
        assertEquals("2026-01-01", task.getStart());
        assertEquals("3d", task.getDuration());
    }

    @Test
    public void testCommentOnHiddenChannelProducesNoStatement() {
        MermaidDocument doc = parse("flowchart\n %% this is a comment\n a --> b");
        // COMMENT 进 HIDDEN 通道，不得产生语句
        assertEquals(1, doc.getStatements().size());
        assertEquals(MermaidASTKind.MermaidFlowEdge, doc.getStatements().get(0).getASTKind());
    }

    @Test
    public void testClassNodeParsesWithMembers() {
        // 回归覆盖 wi10#1（plan 2306 项 6）：'class' 关键字语句解析为 MermaidClassNode
        // 而非退化为 FlowNode；CLASS token 仅匹配图表类型 'classDiagram'
        MermaidDocument doc = parse("classDiagram\n class Foo [\n   +name : String\n   m static\n ]");
        assertEquals(MermaidDiagramType.CLASS, doc.getType());
        assertEquals(1, doc.getStatements().size());
        assertEquals(MermaidASTKind.MermaidClassNode, doc.getStatements().get(0).getASTKind());

        MermaidClassNode cls = (MermaidClassNode) doc.getStatements().get(0);
        assertEquals("Foo", cls.getClassName());
        assertEquals(2, cls.getMembers().size());
        assertEquals("name", cls.getMembers().get(0).getName());
        assertEquals(MermaidVisibility.PUBLIC, cls.getMembers().get(0).getVisibility());
        assertEquals("String", cls.getMembers().get(0).getType());
        assertEquals("m", cls.getMembers().get(1).getName());
        assertEquals(Boolean.TRUE, cls.getMembers().get(1).getIsStatic());
    }

    @Test
    public void testStateNodeParsesWithDescription() {
        // 回归覆盖 wi10#1：'state' 关键字语句解析为 MermaidStateNode
        MermaidDocument doc = parse("stateDiagram\n state A : \"active\"");
        assertEquals(MermaidDiagramType.STATE, doc.getType());
        assertEquals(1, doc.getStatements().size());
        assertEquals(MermaidASTKind.MermaidStateNode, doc.getStatements().get(0).getASTKind());

        MermaidStateNode state = (MermaidStateNode) doc.getStatements().get(0);
        assertEquals("A", state.getId());
        assertEquals("active", state.getDescription());
    }

    @Test
    public void testDirectionStatementParses() {
        // 回归覆盖 wi10#2（plan 2306 项 6）：DIRECTION 已补词法规则，
        // direction TB 解析为 MermaidDirectionStatement 而非普通 FlowNode
        MermaidDocument doc = parse("flowchart\n direction LR\n a --> b");
        assertEquals(MermaidDiagramType.FLOWCHART, doc.getType());
        assertEquals(2, doc.getStatements().size());
        assertEquals(MermaidASTKind.MermaidDirectionStatement, doc.getStatements().get(0).getASTKind());
        assertEquals(MermaidDirection.LR, ((MermaidDirectionStatement) doc.getStatements().get(0)).getDirection());
        assertEquals(MermaidASTKind.MermaidFlowEdge, doc.getStatements().get(1).getASTKind());
    }

    @Test
    public void testInvalidDiagramTypeRejected() {
        // 文档首 token 必须是图表类型关键字
        assertThrows(NopException.class, () -> parse("unknownType a --> b"));
    }

    @Test
    public void testMissingStatementsRejected() {
        // mermaidStatements_ 要求至少一条语句
        assertThrows(NopException.class, () -> parse("flowchart"));
    }

    @Test
    public void testDirectionKeywordCannotBeStatement() {
        // 'TB' 等方向关键字不能作为 FlowNode 标识符（词法优先级），孤立出现时解析失败
        assertThrows(NopException.class, () -> parse("flowchart\n TB"));
    }

    @Test
    public void testProcessorDispatchesByASTKind() {
        MermaidDocument doc = parse("flowchart\n a(\"x\")\n b\n a --> b");

        AtomicInteger flowNodes = new AtomicInteger();
        AtomicInteger flowEdges = new AtomicInteger();
        MermaidASTProcessor<Void, Void> processor = new MermaidASTProcessor<Void, Void>() {
            @Override
            public Void processMermaidFlowNode(MermaidFlowNode node, Void ctx) {
                flowNodes.incrementAndGet();
                return null;
            }

            @Override
            public Void processMermaidFlowEdge(MermaidFlowEdge node, Void ctx) {
                flowEdges.incrementAndGet();
                return null;
            }
        };

        AtomicInteger total = new AtomicInteger();
        walk(doc, processor, total);

        // document + 2 flowNode + 1 flowEdge = 4 个节点被遍历分发
        assertEquals(4, total.get());
        assertEquals(2, flowNodes.get());
        assertEquals(1, flowEdges.get());
    }

    private <T, C> void walk(MermaidASTNode node, MermaidASTProcessor<T, C> processor, AtomicInteger total) {
        processor.processAST(node, null);
        total.incrementAndGet();
        node.forEachChild(child -> walk(child, processor, total));
    }

    @Test
    public void testOptimizerKeepsUnchangedDocument() {
        MermaidDocument doc = parse("flowchart\n a --> b");
        MermaidASTOptimizer<Void> optimizer = new MermaidASTOptimizer<>();
        // 默认优化器对叶子无变换，列表不变时必须返回原实例（不克隆）
        assertSame(doc, optimizer.optimize(doc, null));
        assertEquals(1, doc.getStatements().size());
    }

    @Test
    public void testVisibilitySymbolMapping() {
        assertEquals(MermaidVisibility.PUBLIC, MermaidVisibility.fromSymbol("+"));
        assertEquals(MermaidVisibility.PRIVATE, MermaidVisibility.fromSymbol("-"));
        assertEquals(MermaidVisibility.PROTECTED, MermaidVisibility.fromSymbol("#"));
        assertEquals(MermaidVisibility.PACKAGE, MermaidVisibility.fromSymbol("~"));
        // 未知符号回退默认 PUBLIC
        assertEquals(MermaidVisibility.PUBLIC, MermaidVisibility.fromSymbol("?"));
        assertEquals("+", MermaidVisibility.PUBLIC.getSymbol());
    }

    @Test
    public void testParseErrorCarriesSourceContext() {
        try {
            parse("flowchart\n TB");
            throw new IllegalStateException("should not reach here");
        } catch (NopException e) {
            // 解析错误必须携带 offendingToken 与 source 上下文便于定位
            assertTrue(e.getLocalizedMessage().contains("TB"));
        }
    }
}
