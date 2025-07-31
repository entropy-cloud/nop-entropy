lexer grammar MermaidLexer;

// ======================= 图表类型关键字 =======================
FLOWCHART: 'flowchart' | 'graph';
SEQUENCE: 'sequenceDiagram';
CLASS: 'classDiagram';
STATE: 'stateDiagram';
GANTT: 'gantt';
PIE: 'pie';
GIT: 'git';
ER: 'er';
JOURNEY: 'journey';

// ======================= 方向关键字 =======================
TB: 'TB' | 'TD';
BT: 'BT';
LR: 'LR';
RL: 'RL';

// ======================= 节点形状关键字 =======================
ROUND: 'round';
STADIUM: 'stadium';
SUBROUTINE: 'subroutine';
CYLINDER: 'cylinder';
CIRCLE: 'circle';
ASYMMETRIC: 'asymmetric';
RHOMBUS: 'rhombus';
HEXAGON: 'hexagon';
PARALLELOGRAM: 'parallelogram';
TRAPEZOID: 'trapezoid';
DOUBLE_CIRCLE: 'double_circle';

// ======================= 边类型关键字 =======================
ARROW: '-->';
OPEN_ARROW: '->>';
DOTTED: '-.->';
THICK: '==>';

// ======================= 其他关键字 =======================
PARTICIPANT: 'participant';
AS: 'as';
CLASS: 'class';
STATE: 'state';
TASK: 'task';
PIE: 'pie';
SUBGRAPH: 'subgraph';
STYLE: 'style';
STATIC: 'static';

// ======================= 可见性修饰符 =======================
Visibility: '+' | '-' | '#' | '~';

// ======================= 标点符号 =======================
LPAREN: '(';
RPAREN: ')';
LCURLY: '{';
RCURLY: '}';
LBRACE: '[';
RBRACE: ']';
COLON: ':';
SEMI: ';';
COMMA: ',';
DOT: '.';
PIPE: '|';

// ======================= 字面量 =======================
StringLiteral_: '"' (~["\\\r\n] | '\\' .)* '"';
NumberLiteral_: [0-9]+ ('.' [0-9]+)?;

// ======================= 标识符 =======================
Identifier_: [a-zA-Z_][a-zA-Z0-9_]*;

// ======================= 注释 =======================
COMMENT: '%%' ~[\r\n]* -> channel(HIDDEN);

// ======================= 空白 =======================
WS: [ \t\r\n]+ -> skip;