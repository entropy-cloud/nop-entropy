package io.nop.jq.ast;

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
    T visitReduce(ReduceNode node);
    T visitForEach(ForEachNode node);
    T visitEmpty(EmptyNode node);
    T visitDebug(DebugNode node);
    T visitError(ErrorNode node);
    T visitFuncCall(FuncCallNode node);
    T visitFuncDef(FuncDefNode node);
    T visitFormat(FormatNode node);
    T visitAlternative(AlternativeNode node);
    T visitUpdateAssign(UpdateAssignNode node);
    T visitWhile(WhileNode node);
    T visitUntil(UntilNode node);
    T visitInput(InputNode node);
    T visitEnv(EnvNode node);
}
