package io.nop.jq.jq.ast;

/**
 * Base interface for all jq AST nodes.
 * Each node represents a syntactic construct in a jq expression.
 */
public sealed interface JqAstNode permits
        NullLiteralNode, BooleanLiteralNode, NumberLiteralNode, StringLiteralNode,
        IdentityNode, FieldAccessNode, IndexAccessNode, SliceNode, IteratorNode, RecursiveDescentNode,
        PipeNode, CommaNode, MathOpNode, ComparisonNode, BooleanOpNode, NegateNode,
        IfThenElseNode, TryCatchNode, LabelNode, BreakNode,
        BindNode, VariableNode,
        ObjectConstructNode, ArrayConstructNode, StringInterpNode,
        ReduceNode, ForEachNode,
        EmptyNode, DebugNode, ErrorNode,
        FuncCallNode, FuncDefNode,
        FormatNode,
        AlternativeNode, UpdateAssignNode, WhileNode, UntilNode, InputNode, EnvNode {

    /**
     * Accept a visitor for double dispatch.
     */
    <T> T accept(JqAstVisitor<T> visitor);
}
