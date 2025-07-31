grammar Mermaid;

import BaseRules;



// ======================= Root Rule =======================
mermaidDocument
    : type=mermaidDiagramType_ statements=mermaidStatements_
    ;

// ======================= Diagram Type =======================
mermaidDiagramType_
    : FLOWCHART
    | SEQUENCE
    | CLASS
    | STATE
    | GANTT
    | PIE
    | GIT
    | ER
    | JOURNEY
    ;

// ======================= Statements List =======================
mermaidStatements_
    : (e=mermaidStatement )+
    ;

mermaidStatement
    : mermaidDirectionStatement
    | mermaidComment
    | mermaidFlowNode
    | mermaidFlowEdge
    | mermaidFlowSubgraph
    | mermaidParticipant
    | mermaidSequenceMessage
    | mermaidClassNode
    | mermaidStateNode
    | mermaidGanttTask
    | mermaidPieItem
    | mermaidStyleStatement
    ;

// ======================= Common Statements =======================
mermaidDirectionStatement
    : DIRECTION direction=mermaidDirection_
    ;

mermaidDirection_
    : TB
    | BT
    | LR
    | RL
    ;

mermaidComment
    : COMMENT content=StringLiteral_
    ;

// ======================= Flowchart Statements =======================
mermaidFlowNode
    : id=Identifier_ (LPAREN text=StringLiteral_? RPAREN)? shape=mermaidNodeShape_?
    ;

mermaidNodeShape_
    : ROUND
    | STADIUM
    | SUBROUTINE
    | CYLINDER
    | CIRCLE
    | ASYMMETRIC
    | RHOMBUS
    | HEXAGON
    | PARALLELOGRAM
    | TRAPEZOID
    | DOUBLE_CIRCLE
    ;

mermaidFlowEdge
    : from=Identifier_ edgeType=mermaidEdgeType_? (label=StringLiteral_)? to=Identifier
    ;

mermaidEdgeType_
    : ARROW
    | OPEN_ARROW
    | DOTTED
    | THICK
    ;

mermaidFlowSubgraph
    : SUBGRAPH id=Identifier_ (title=StringLiteral_)? LBRACE  statements=mermaidStatements_  RBRACE
    ;

// ======================= Sequence Diagram Statements =======================
mermaidParticipant
    : PARTICIPANT name=Identifier_ (AS alias=StringLiteral_)?
    ;

mermaidSequenceMessage
    : from=Identifier_ edgeType=mermaidEdgeType_? (message=StringLiteral_)? to=Identifier
    ;

// ======================= Class Diagram Statements =======================
mermaidClassNode
    : CLASS className=Identifier_ LBRACE  members=mermaidClassMembers_?  RBRACE
    ;

mermaidClassMembers_
    : (e=mermaidClassMember )+
    ;

mermaidClassMember
    : visibility=Visibility? name=Identifier_ (COLON type=Identifier)? (isStatic=STATIC)?
    ;

// ======================= State Diagram Statements =======================
mermaidStateNode
    : STATE id=Identifier_ (COLON description=StringLiteral_)?
    ;

// ======================= Gantt Diagram Statements =======================
mermaidGanttTask
    : TASK id=Identifier_ COLON title=StringLiteral_ (COMMA start=StringLiteral_)? (COMMA duration=StringLiteral_)?
    ;

// ======================= Pie Chart Statements =======================
mermaidPieItem
    : PIE label=StringLiteral_ COLON value=NumberLiteral_
    ;

// ======================= Style Statements =======================
mermaidStyleStatement
    : STYLE target=StringLiteral_ LBRACE  attributes=mermaidStyleAttributes_?  RBRACE
    ;

mermaidStyleAttributes_
    : (e=mermaidStyleAttribute )+
    ;

mermaidStyleAttribute
    : name=Identifier_ COLON value=StringLiteral_?
    ;