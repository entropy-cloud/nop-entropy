package io.nop.jq.jq.ast;

/**
 * Visitor for jq AST nodes. Uses double dispatch pattern.
 */
public interface JqAstVisitor<T> {
    T visitNull(NullLiteralNode node);
    T visitBoolean(BooleanLiteralNode node);
    T visitNumber(NumberLiteralNode node);
    T visitString(StringLiteralNode node);
    T visitIdentity(IdentityNode node);
    T visitFieldAccess(FieldAccessNode node);
    T visitIndexAccess(IndexAccessNode node);
    T visitSlice(SliceNode node);
    T visitIterator(IteratorNode node);
    T visitRecursiveDescent(RecursiveDescentNode node);
    T visitPipe(PipeNode node);
    T visitComma(CommaNode node);
    T visitMathOp(MathOpNode node);
    T visitComparison(ComparisonNode node);
    T visitBooleanOp(BooleanOpNode node);
    T visitNegate(NegateNode node);
    T visitIfThenElse(IfThenElseNode node);
    T visitTryCatch(TryCatchNode node);
    T visitLabel(LabelNode node);
    T visitBreak(BreakNode node);
    T visitBind(BindNode node);
    T visitVariable(VariableNode node);
    T visitObjectConstruct(ObjectConstructNode node);
    T visitArrayConstruct(ArrayConstructNode node);
    T visitStringInterp(StringInterpNode node);
    T visitSelect(SelectNode node);
    T visitMap(MapNode node);
    T visitReduce(ReduceNode node);
    T visitForEach(ForEachNode node);
    T visitLimit(LimitNode node);
    T visitEmpty(EmptyNode node);
    T visitDebug(DebugNode node);
    T visitError(ErrorNode node);
    T visitFuncCall(FuncCallNode node);
    T visitFuncDef(FuncDefNode node);
    T visitFormat(FormatNode node);
}
