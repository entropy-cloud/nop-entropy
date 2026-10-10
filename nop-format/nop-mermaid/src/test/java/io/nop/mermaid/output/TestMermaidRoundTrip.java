package io.nop.mermaid.output;

import io.nop.mermaid.ast.MermaidClassMember;
import io.nop.mermaid.ast.MermaidClassNode;
import io.nop.mermaid.ast.MermaidDirectionStatement;
import io.nop.mermaid.ast.MermaidDocument;
import io.nop.mermaid.ast.MermaidEdgeType;
import io.nop.mermaid.ast.MermaidFlowEdge;
import io.nop.mermaid.ast.MermaidFlowNode;
import io.nop.mermaid.ast.MermaidFlowSubgraph;
import io.nop.mermaid.ast.MermaidGanttTask;
import io.nop.mermaid.ast.MermaidParticipant;
import io.nop.mermaid.ast.MermaidPieItem;
import io.nop.mermaid.ast.MermaidSequenceMessage;
import io.nop.mermaid.ast.MermaidStateNode;
import io.nop.mermaid.ast.MermaidStatement;
import io.nop.mermaid.ast.MermaidStyleAttribute;
import io.nop.mermaid.ast.MermaidStyleStatement;
import io.nop.mermaid.parse.MermaidASTParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-4.4-A5 (plan 2405): the generator must emit grammar-legal text for grammar-legal
 * input. Each case parses, generates, reparses, and compares the AST at field level
 * (recursively through subgraph/style/class children) — a type + statement-count check
 * alone would let lost shapes/labels/edge types pass.
 */
public class TestMermaidRoundTrip {

    private static final MermaidASTParser PARSER = new MermaidASTParser();

    private static MermaidDocument parse(String src) {
        return PARSER.parseFromText(null, src);
    }

    private static String generate(MermaidDocument doc) {
        MermaidGenerator generator = new MermaidGenerator();
        generator.visit(doc);
        return generator.getResult();
    }

    private static void assertRoundTrip(String name, String src) {
        MermaidDocument doc1 = parse(src);
        assertNotNull(doc1, name + ": initial parse returned null");
        String generated = generate(doc1);
        MermaidDocument doc2;
        try {
            doc2 = parse(generated);
        } catch (Exception e) {
            throw new AssertionError(name + ": reparse failed for generated [" + flat(generated) + "]", e);
        }
        assertNotNull(doc2, name + ": reparse returned null for [" + flat(generated) + "]");
        assertDocumentEquals(name, doc1, doc2);
    }

    private static String flat(String text) {
        return text.replace("\n", "\\n");
    }

    private static void assertDocumentEquals(String name, MermaidDocument a, MermaidDocument b) {
        assertEquals(a.getType(), b.getType(), name + ": diagram type");
        assertStatementsEqual(name, a.getStatements(), b.getStatements());
    }

    private static void assertStatementsEqual(String name, List<MermaidStatement> a, List<MermaidStatement> b) {
        assertEquals(a.size(), b.size(), name + ": statement count");
        for (int i = 0; i < a.size(); i++) {
            assertStatementEqual(name + "[" + i + "]", a.get(i), b.get(i));
        }
    }

    private static void assertStatementEqual(String path, MermaidStatement a, MermaidStatement b) {
        assertNotNull(b, path + ": missing statement");
        assertEquals(a.getClass(), b.getClass(), path + ": statement type");

        if (a instanceof MermaidDirectionStatement) {
            assertEquals(((MermaidDirectionStatement) a).getDirection(),
                    ((MermaidDirectionStatement) b).getDirection(), path + ": direction");
        } else if (a instanceof MermaidFlowNode) {
            MermaidFlowNode x = (MermaidFlowNode) a;
            MermaidFlowNode y = (MermaidFlowNode) b;
            assertEquals(x.getId(), y.getId(), path + ": flow node id");
            assertEquals(x.getText(), y.getText(), path + ": flow node text");
            assertEquals(x.getShape(), y.getShape(), path + ": flow node shape");
        } else if (a instanceof MermaidFlowEdge) {
            MermaidFlowEdge x = (MermaidFlowEdge) a;
            MermaidFlowEdge y = (MermaidFlowEdge) b;
            assertEquals(x.getFrom(), y.getFrom(), path + ": edge from");
            assertEquals(x.getEdgeType(), y.getEdgeType(), path + ": edge type");
            assertEquals(x.getLabel(), y.getLabel(), path + ": edge label");
            assertEquals(x.getTo(), y.getTo(), path + ": edge to");
        } else if (a instanceof MermaidFlowSubgraph) {
            MermaidFlowSubgraph x = (MermaidFlowSubgraph) a;
            MermaidFlowSubgraph y = (MermaidFlowSubgraph) b;
            assertEquals(x.getId(), y.getId(), path + ": subgraph id");
            assertEquals(x.getTitle(), y.getTitle(), path + ": subgraph title");
            assertStatementsEqual(path + ".subgraph", x.getStatements(), y.getStatements());
        } else if (a instanceof MermaidParticipant) {
            MermaidParticipant x = (MermaidParticipant) a;
            MermaidParticipant y = (MermaidParticipant) b;
            assertEquals(x.getName(), y.getName(), path + ": participant name");
            assertEquals(x.getAlias(), y.getAlias(), path + ": participant alias");
        } else if (a instanceof MermaidSequenceMessage) {
            MermaidSequenceMessage x = (MermaidSequenceMessage) a;
            MermaidSequenceMessage y = (MermaidSequenceMessage) b;
            assertEquals(x.getFrom(), y.getFrom(), path + ": seq from");
            assertEquals(x.getEdgeType(), y.getEdgeType(), path + ": seq edge type");
            assertEquals(x.getTo(), y.getTo(), path + ": seq to");
            assertEquals(x.getMessage(), y.getMessage(), path + ": seq message");
        } else if (a instanceof MermaidClassNode) {
            MermaidClassNode x = (MermaidClassNode) a;
            MermaidClassNode y = (MermaidClassNode) b;
            assertEquals(x.getClassName(), y.getClassName(), path + ": class name");
            assertEquals(x.getMembers().size(), y.getMembers().size(), path + ": class member count");
            for (int i = 0; i < x.getMembers().size(); i++) {
                assertClassMemberEqual(path + ".member[" + i + "]", x.getMembers().get(i), y.getMembers().get(i));
            }
        } else if (a instanceof MermaidStateNode) {
            MermaidStateNode x = (MermaidStateNode) a;
            MermaidStateNode y = (MermaidStateNode) b;
            assertEquals(x.getId(), y.getId(), path + ": state id");
            assertEquals(x.getDescription(), y.getDescription(), path + ": state description");
        } else if (a instanceof MermaidGanttTask) {
            MermaidGanttTask x = (MermaidGanttTask) a;
            MermaidGanttTask y = (MermaidGanttTask) b;
            assertEquals(x.getId(), y.getId(), path + ": task id");
            assertEquals(x.getTitle(), y.getTitle(), path + ": task title");
            assertEquals(x.getStart(), y.getStart(), path + ": task start");
            assertEquals(x.getDuration(), y.getDuration(), path + ": task duration");
        } else if (a instanceof MermaidPieItem) {
            MermaidPieItem x = (MermaidPieItem) a;
            MermaidPieItem y = (MermaidPieItem) b;
            assertEquals(x.getLabel(), y.getLabel(), path + ": pie label");
            assertEquals(x.getValue(), y.getValue(), path + ": pie value");
        } else if (a instanceof MermaidStyleStatement) {
            MermaidStyleStatement x = (MermaidStyleStatement) a;
            MermaidStyleStatement y = (MermaidStyleStatement) b;
            assertEquals(x.getTarget(), y.getTarget(), path + ": style target");
            assertEquals(x.getAttributes().size(), y.getAttributes().size(), path + ": style attribute count");
            for (int i = 0; i < x.getAttributes().size(); i++) {
                MermaidStyleAttribute xa = x.getAttributes().get(i);
                MermaidStyleAttribute ya = y.getAttributes().get(i);
                assertEquals(xa.getName(), ya.getName(), path + ": style attr name");
                assertEquals(xa.getValue(), ya.getValue(), path + ": style attr value");
            }
        } else {
            throw new AssertionError(path + ": unhandled statement type " + a.getClass().getSimpleName());
        }
    }

    private static void assertClassMemberEqual(String path, MermaidClassMember x, MermaidClassMember y) {
        assertEquals(x.getVisibility(), y.getVisibility(), path + ": visibility");
        assertEquals(x.getName(), y.getName(), path + ": name");
        assertEquals(x.getType(), y.getType(), path + ": type");
        assertEquals(x.getIsStatic(), y.getIsStatic(), path + ": isStatic");
    }

    // ======================= 13 probe cases (round-trip baseline) =======================

    @Test
    void flowchartBasic() {
        assertRoundTrip("flowchart-basic", "flowchart\n a\n b\n a --> b");
    }

    @Test
    void flowchartShape() {
        assertRoundTrip("flowchart-shape", "flowchart\n a(\"Start\") round");
    }

    @Test
    void flowchartLabeledEdge() {
        assertRoundTrip("flowchart-labeled-edge", "flowchart\n a -.-> \"retry\" b");
    }

    @Test
    void flowchartOpenArrow() {
        assertRoundTrip("flowchart-open-arrow", "flowchart\n a ->> b");
    }

    @Test
    void flowchartSubgraph() {
        assertRoundTrip("flowchart-subgraph", "flowchart\n subgraph s1 \"Group\" [\n a --> b\n ]");
    }

    @Test
    void flowchartStyle() {
        assertRoundTrip("flowchart-style", "flowchart\n style \"a\" [\n fill : \"#f9f\"\n ]");
    }

    @Test
    void flowchartDirection() {
        assertRoundTrip("flowchart-direction", "flowchart\n direction LR\n a --> b");
    }

    @Test
    void sequenceMessage() {
        assertRoundTrip("sequence-msg", "sequenceDiagram\n A ->> B: \"hello\"");
    }

    @Test
    void sequenceParticipant() {
        assertRoundTrip("sequence-participant", "sequenceDiagram\n participant A as \"Alice\"");
    }

    @Test
    void classNode() {
        assertRoundTrip("class-node", "classDiagram\n class Foo [\n   +name : String\n ]");
    }

    @Test
    void stateNode() {
        assertRoundTrip("state-node", "stateDiagram\n state A : \"active\"");
    }

    @Test
    void ganttTask() {
        assertRoundTrip("gantt-task", "gantt\n task t1 : \"Design\", \"2026-01-01\", \"3d\"");
    }

    @Test
    void pieItem() {
        assertRoundTrip("pie-item", "pie\n pie \"A\" : 30");
    }

    // ======================= extra grammar-legal cases =======================

    @Test
    void flowEdgeWithoutEdgeType() {
        // grammar: edgeType is optional; a null enum must not be switched on (NPE)
        assertRoundTrip("flow-null-edge", "flowchart\n a \"lbl\" b");
    }

    @Test
    void sequenceMessageWithoutEdgeType() {
        assertRoundTrip("seq-null-edge", "sequenceDiagram\n A B : \"x\"");
    }

    @Test
    void styleTargetWithHyphen() {
        // style target is a StringLiteral_, not an Identifier_ — must not go through id()
        assertRoundTrip("style-hyphen-target", "flowchart\n style \"a-b\" [\n fill : \"#f9f\"\n ]");
    }

    @Test
    void classStaticMember() {
        assertRoundTrip("class-static", "classDiagram\n class Foo [\n +name : String static\n ]");
    }

    // ======================= arrow token discriminators =======================

    @Test
    void flowEdgeTokensMatchGrammar() {
        assertArrow("flowchart\n a --> b", MermaidEdgeType.ARROW, "-->");
        assertArrow("flowchart\n a ->> b", MermaidEdgeType.OPEN_ARROW, "->>");
        assertArrow("flowchart\n a -.-> b", MermaidEdgeType.DOTTED, "-.->");
        assertArrow("flowchart\n a ==> b", MermaidEdgeType.THICK, "==>");
    }

    @Test
    void sequenceEdgeTokensMatchGrammar() {
        assertArrow("sequenceDiagram\n A --> B : \"m\"", MermaidEdgeType.ARROW, "-->");
        assertArrow("sequenceDiagram\n A ->> B : \"m\"", MermaidEdgeType.OPEN_ARROW, "->>");
        assertArrow("sequenceDiagram\n A -.-> B : \"m\"", MermaidEdgeType.DOTTED, "-.->");
        assertArrow("sequenceDiagram\n A ==> B : \"m\"", MermaidEdgeType.THICK, "==>");
    }

    private static void assertArrow(String src, MermaidEdgeType expected, String token) {
        MermaidDocument doc = parse(src);
        String generated = generate(doc);
        assertTrue(generated.contains(token),
                "generated text must contain grammar token [" + token + "], got [" + flat(generated) + "]");

        MermaidDocument reparsed = parse(generated);
        MermaidEdgeType actual = reparsed.getStatements().get(0) instanceof MermaidFlowEdge
                ? ((MermaidFlowEdge) reparsed.getStatements().get(0)).getEdgeType()
                : ((MermaidSequenceMessage) reparsed.getStatements().get(0)).getEdgeType();
        assertEquals(expected, actual, "reparsed edge type for [" + flat(generated) + "]");
    }
}
