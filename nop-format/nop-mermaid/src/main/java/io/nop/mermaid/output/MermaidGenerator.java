package io.nop.mermaid.output;

import io.nop.api.core.exceptions.NopException;

import static io.nop.mermaid.MermaidErrors.ERR_MERMAID_INVALID_IDENTIFIER;

import io.nop.commons.text.CodeBuilder;
import io.nop.mermaid.ast.MermaidASTVisitor;
import io.nop.mermaid.ast.MermaidClassMember;
import io.nop.mermaid.ast.MermaidClassNode;
import io.nop.mermaid.ast.MermaidComment;
import io.nop.mermaid.ast.MermaidDirectionStatement;
import io.nop.mermaid.ast.MermaidDocument;
import io.nop.mermaid.ast.MermaidFlowEdge;
import io.nop.mermaid.ast.MermaidFlowNode;
import io.nop.mermaid.ast.MermaidFlowSubgraph;
import io.nop.mermaid.ast.MermaidGanttTask;
import io.nop.mermaid.ast.MermaidParticipant;
import io.nop.mermaid.ast.MermaidPieItem;
import io.nop.mermaid.ast.MermaidSequenceMessage;
import io.nop.mermaid.ast.MermaidStateNode;
import io.nop.mermaid.ast.MermaidStyleAttribute;
import io.nop.mermaid.ast.MermaidStyleStatement;

public class MermaidGenerator extends MermaidASTVisitor {
    private final CodeBuilder out;

    public MermaidGenerator(CodeBuilder out) {
        this.out = out;
    }

    public MermaidGenerator() {
        this(new CodeBuilder());
    }

    public String getResult() {
        return out.toString();
    }

    // ======================= String Escaping =======================
    private String escapeMermaidString(String str) {
        if (str == null) return null;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\'':
                    sb.append("\\'");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    // ======================= Document =======================
    @Override
    public void visitMermaidDocument(MermaidDocument node) {
        out.printLine(node.getType().name().toLowerCase() + " " + node.getType());
        super.visitMermaidDocument(node);
    }

    // ======================= Common Statements =======================
    @Override
    public void visitMermaidDirectionStatement(MermaidDirectionStatement node) {
        out.printLine("direction " + node.getDirection());
    }

    @Override
    public void visitMermaidComment(MermaidComment node) {
        out.printLine("%% " + escapeMermaidString(node.getContent()));
    }

    // ======================= Flowchart Statements =======================
    @Override
    public void visitMermaidFlowNode(MermaidFlowNode node) {
        out.append(id(node.getId()));
        if (node.getText() != null) {
            out.append("(\"").append(escapeMermaidString(node.getText())).append("\")");
        } else {
            out.append("()");
        }

        if (node.getShape() != null) {
            out.append(":::").append(node.getShape().name().toLowerCase());
        }
        out.line();
    }

    @Override
    public void visitMermaidFlowEdge(MermaidFlowEdge node) {
        out.append(id(node.getFrom()));

        switch (node.getEdgeType()) {
            case ARROW:
                out.append(" --> ");
                break;
            case OPEN_ARROW:
                out.append(" ---> ");
                break;
            case DOTTED:
                out.append(" -.-> ");
                break;
            case THICK:
                out.append(" ==> ");
                break;
            default:
                out.append(" --> ");
        }

        out.append(id(node.getTo()));

        if (node.getLabel() != null) {
            out.append(" : \"").append(escapeMermaidString(node.getLabel())).append("\"");
        }

        out.line();
    }

    @Override
    public void visitMermaidFlowSubgraph(MermaidFlowSubgraph node) {
        out.printLine("subgraph " + node.getId());
        if (node.getTitle() != null) {
            out.printLine("title \"" + escapeMermaidString(node.getTitle()) + "\"");
        }
        out.incIndent();
        super.visitMermaidFlowSubgraph(node);
        out.decIndent();
        out.printLine("end");
    }

    // ======================= Sequence Diagram Statements =======================
    @Override
    public void visitMermaidParticipant(MermaidParticipant node) {
        out.append("participant ").append(id(node.getName()));
        if (node.getAlias() != null) {
            out.append(" as \"").append(escapeMermaidString(node.getAlias())).append("\"");
        }
        out.line();
    }

    @Override
    public void visitMermaidSequenceMessage(MermaidSequenceMessage node) {
        out.append(id(node.getFrom()));

        switch (node.getEdgeType()) {
            case ARROW:
                out.append(" -> ");
                break;
            case OPEN_ARROW:
                out.append(" --> ");
                break;
            case DOTTED:
                out.append(" -.-> ");
                break;
            case THICK:
                out.append(" ==> ");
                break;
            default:
                out.append(" -> ");
        }

        out.append(id(node.getTo()));

        if (node.getMessage() != null) {
            out.append(" : \"").append(escapeMermaidString(node.getMessage())).append("\"");
        }

        out.line();
    }

    // ======================= Class Diagram Statements =======================
    @Override
    public void visitMermaidClassNode(MermaidClassNode node) {
        out.printLine("class " + id(node.getClassName()) + " {");
        out.incIndent();
        super.visitMermaidClassNode(node);
        out.decIndent();
        out.printLine("}");
    }

    @Override
    public void visitMermaidClassMember(MermaidClassMember node) {
        if (node.getVisibility() != null) {
            out.append(node.getVisibility().getSymbol());
        }
        out.append(node.getName());
        if (node.getType() != null) {
            out.append(" : ").append(node.getType());
        }
        if (node.getIsStatic()) {
            out.append(" $");
        }
        out.line();
    }

    // ======================= State Diagram Statements =======================
    @Override
    public void visitMermaidStateNode(MermaidStateNode node) {
        out.append("state ").append(node.getId());
        if (node.getDescription() != null) {
            out.append(" : \"").append(escapeMermaidString(node.getDescription())).append("\"");
        }
        out.line();
    }

    // ======================= Gantt Diagram Statements =======================
    @Override
    public void visitMermaidGanttTask(MermaidGanttTask node) {
        out.append("task ").append(node.getId())
                .append(" : \"").append(escapeMermaidString(node.getTitle())).append("\"");

        if (node.getStart() != null) {
            out.append(", \"").append(escapeMermaidString(node.getStart())).append("\"");
        }
        if (node.getDuration() != null) {
            out.append(", \"").append(escapeMermaidString(node.getDuration())).append("\"");
        }
        out.line();
    }

    // ======================= Pie Chart Statements =======================
    @Override
    public void visitMermaidPieItem(MermaidPieItem node) {
        out.printLine("pie \"" + escapeMermaidString(node.getLabel()) + "\" : " + node.getValue());
    }

    // ======================= Style Statements =======================
    @Override
    public void visitMermaidStyleStatement(MermaidStyleStatement node) {
        out.printLine("style " + id(node.getTarget()) + " {");
        out.incIndent();
        super.visitMermaidStyleStatement(node);
        out.decIndent();
        out.printLine("}");
    }

    @Override
    public void visitMermaidStyleAttribute(MermaidStyleAttribute node) {
        out.append(node.getName()).append(" : ");
        if (node.getValue() != null) {
            out.append("\"").append(escapeMermaidString(node.getValue())).append("\"");
        }
        out.line();
    }

    /**
     * 文法 Identifier_ 只接受 [a-zA-Z_][a-zA-Z0-9_]*，非ASCII/特殊字符ID会破坏图文本结构
     */
    static String id(String value) {
        if (value == null || value.isEmpty())
            return value;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9' && i > 0) || c == '_';
            if (!ok)
                throw new NopException(ERR_MERMAID_INVALID_IDENTIFIER).param("id", value);
        }
        return value;
    }
}
