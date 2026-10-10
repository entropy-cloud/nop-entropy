package io.nop.mermaid.output;

import io.nop.api.core.exceptions.NopException;

import static io.nop.mermaid.MermaidErrors.ARG_DIAGRAM_TYPE;
import static io.nop.mermaid.MermaidErrors.ERR_MERMAID_INVALID_DIAGRAM_TYPE;
import static io.nop.mermaid.MermaidErrors.ERR_MERMAID_INVALID_IDENTIFIER;

import io.nop.commons.text.CodeBuilder;
import io.nop.mermaid.ast.MermaidASTVisitor;
import io.nop.mermaid.ast.MermaidClassMember;
import io.nop.mermaid.ast.MermaidClassNode;
import io.nop.mermaid.ast.MermaidComment;
import io.nop.mermaid.ast.MermaidDiagramType;
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
        // P1-4.4-A5 (plan 2405): emit the grammar literal (BaseRules.g4:4-12), not the enum name —
        // e.g. SEQUENCE must be `sequenceDiagram`, STATE `stateDiagram`; the old
        // `type.name().toLowerCase() + " " + type` produced `sequence SEQUENCE`, whose second
        // token polluted the statement stream.
        out.printLine(diagramKeyword(node.getType()));
        super.visitMermaidDocument(node);
    }

    static String diagramKeyword(MermaidDiagramType type) {
        switch (type) {
            case FLOWCHART:
                return "flowchart";
            case SEQUENCE:
                return "sequenceDiagram";
            case CLASS:
                return "classDiagram";
            case STATE:
                return "stateDiagram";
            case GANTT:
                return "gantt";
            case PIE:
                return "pie";
            case GIT:
                return "git";
            case ER:
                return "er";
            case JOURNEY:
                return "journey";
            default:
                throw new NopException(ERR_MERMAID_INVALID_DIAGRAM_TYPE).param(ARG_DIAGRAM_TYPE, String.valueOf(type));
        }
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
            // shape is a bare token in the grammar (mermaidNodeShape_: ROUND | ...), not a `:::shape` suffix
            out.append(" ").append(node.getShape().name().toLowerCase());
        }
        out.line();
    }

    @Override
    public void visitMermaidFlowEdge(MermaidFlowEdge node) {
        out.append(id(node.getFrom()));
        out.append(edgeToken(node.getEdgeType()));

        // grammar: from=Identifier_ edgeType? (label=StringLiteral_)? to=Identifier_ — the label
        // precedes the target and has no colon
        if (node.getLabel() != null) {
            out.append("\"").append(escapeMermaidString(node.getLabel())).append("\" ");
        }

        out.append(id(node.getTo()));
        out.line();
    }

    /**
     * Grammar edge tokens (BaseRules.g4:35-38). A null edgeType is legal in the grammar and must
     * not be switched on (unboxing NPE); it emits only the separator space so the from/to tokens
     * stay distinct.
     */
    static String edgeToken(MermaidEdgeType type) {
        if (type == null) {
            return " ";
        }
        switch (type) {
            case ARROW:
                return " --> ";
            case OPEN_ARROW:
                return " ->> ";
            case DOTTED:
                return " -.-> ";
            case THICK:
                return " ==> ";
            default:
                return "";
        }
    }

    @Override
    public void visitMermaidFlowSubgraph(MermaidFlowSubgraph node) {
        // grammar: SUBGRAPH id=Identifier_ (title=StringLiteral_)? LBRACE statements RBRACE,
        // with LBRACE='[' / RBRACE=']' (BaseRules.g4:58-59)
        StringBuilder header = new StringBuilder("subgraph ").append(id(node.getId()));
        if (node.getTitle() != null) {
            header.append(" \"").append(escapeMermaidString(node.getTitle())).append("\"");
        }
        header.append(" [");
        out.printLine(header.toString());
        out.incIndent();
        super.visitMermaidFlowSubgraph(node);
        out.decIndent();
        out.printLine("]");
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
        out.append(edgeToken(node.getEdgeType()));
        out.append(id(node.getTo()));

        if (node.getMessage() != null) {
            out.append(" : \"").append(escapeMermaidString(node.getMessage())).append("\"");
        }

        out.line();
    }

    // ======================= Class Diagram Statements =======================
    @Override
    public void visitMermaidClassNode(MermaidClassNode node) {
        out.printLine("class " + id(node.getClassName()) + " [");
        out.incIndent();
        super.visitMermaidClassNode(node);
        out.decIndent();
        out.printLine("]");
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
        // Boolean field defaults to null; unboxing it directly NPEs on non-static members
        if (Boolean.TRUE.equals(node.getIsStatic())) {
            out.append(" static");
        }
        out.line();
    }

    // ======================= State Diagram Statements =======================
    @Override
    public void visitMermaidStateNode(MermaidStateNode node) {
        out.append("state ").append(id(node.getId()));
        if (node.getDescription() != null) {
            out.append(" : \"").append(escapeMermaidString(node.getDescription())).append("\"");
        }
        out.line();
    }

    // ======================= Gantt Diagram Statements =======================
    @Override
    public void visitMermaidGanttTask(MermaidGanttTask node) {
        out.append("task ").append(id(node.getId()))
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
        // grammar: STYLE target=StringLiteral_ LBRACE attributes RBRACE — target is a string
        // literal (identifier validation does not apply), braces are '[' / ']'
        out.printLine("style \"" + escapeMermaidString(node.getTarget()) + "\" [");
        out.incIndent();
        super.visitMermaidStyleStatement(node);
        out.decIndent();
        out.printLine("]");
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
            throw new NopException(ERR_MERMAID_INVALID_IDENTIFIER).param("id", String.valueOf(value));
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9' && i > 0) || c == '_';
            if (!ok)
                throw new NopException(ERR_MERMAID_INVALID_IDENTIFIER).param("id", value);
        }
        return value;
    }
}
