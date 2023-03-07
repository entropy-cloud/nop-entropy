// Nop Generated from XLangParser.g4 by ANTLR 4.10.1
package io.nop.xlang.parse.antlr;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link XLangParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface XLangParserVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link XLangParser#program}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitProgram(XLangParser.ProgramContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#topLevelStatements_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTopLevelStatements_(XLangParser.TopLevelStatements_Context ctx);
	/**
	 * Visit a parse tree produced by the {@code ModuleDeclaration_import2}
	 * labeled alternative in {@link XLangParser#ast_topLevelStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitModuleDeclaration_import2(XLangParser.ModuleDeclaration_import2Context ctx);
	/**
	 * Visit a parse tree produced by the {@code ast_exportStatement2}
	 * labeled alternative in {@link XLangParser#ast_topLevelStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAst_exportStatement2(XLangParser.Ast_exportStatement2Context ctx);
	/**
	 * Visit a parse tree produced by the {@code ExportDeclaration_func}
	 * labeled alternative in {@link XLangParser#ast_topLevelStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExportDeclaration_func(XLangParser.ExportDeclaration_funcContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ExportDeclaration_type}
	 * labeled alternative in {@link XLangParser#ast_topLevelStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExportDeclaration_type(XLangParser.ExportDeclaration_typeContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ExportDeclaration_const}
	 * labeled alternative in {@link XLangParser#ast_topLevelStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExportDeclaration_const(XLangParser.ExportDeclaration_constContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Statement_top}
	 * labeled alternative in {@link XLangParser#ast_topLevelStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatement_top(XLangParser.Statement_topContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatement(XLangParser.StatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#moduleDeclaration_import}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitModuleDeclaration_import(XLangParser.ModuleDeclaration_importContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#importDeclaration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitImportDeclaration(XLangParser.ImportDeclarationContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#importSpecifiers_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitImportSpecifiers_(XLangParser.ImportSpecifiers_Context ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#importSpecifier}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitImportSpecifier(XLangParser.ImportSpecifierContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#importAsDeclaration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitImportAsDeclaration(XLangParser.ImportAsDeclarationContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#ast_importSource}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAst_importSource(XLangParser.Ast_importSourceContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#ast_exportStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAst_exportStatement(XLangParser.Ast_exportStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#exportNamedDeclaration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExportNamedDeclaration(XLangParser.ExportNamedDeclarationContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#exportSpecifiers_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExportSpecifiers_(XLangParser.ExportSpecifiers_Context ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#exportSpecifier}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExportSpecifier(XLangParser.ExportSpecifierContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#blockStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBlockStatement(XLangParser.BlockStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#statements_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatements_(XLangParser.Statements_Context ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#variableDeclaration_const}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVariableDeclaration_const(XLangParser.VariableDeclaration_constContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#variableDeclaration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVariableDeclaration(XLangParser.VariableDeclarationContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#varModifier_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVarModifier_(XLangParser.VarModifier_Context ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#variableDeclarators_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVariableDeclarators_(XLangParser.VariableDeclarators_Context ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#variableDeclarator}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVariableDeclarator(XLangParser.VariableDeclaratorContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#emptyStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitEmptyStatement(XLangParser.EmptyStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#expressionStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpressionStatement(XLangParser.ExpressionStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#ifStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIfStatement(XLangParser.IfStatementContext ctx);
	/**
	 * Visit a parse tree produced by the {@code DoWhileStatement}
	 * labeled alternative in {@link XLangParser#statement_iteration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDoWhileStatement(XLangParser.DoWhileStatementContext ctx);
	/**
	 * Visit a parse tree produced by the {@code WhileStatement}
	 * labeled alternative in {@link XLangParser#statement_iteration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitWhileStatement(XLangParser.WhileStatementContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ForStatement}
	 * labeled alternative in {@link XLangParser#statement_iteration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitForStatement(XLangParser.ForStatementContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ForInStatement}
	 * labeled alternative in {@link XLangParser#statement_iteration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitForInStatement(XLangParser.ForInStatementContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ForOfStatement}
	 * labeled alternative in {@link XLangParser#statement_iteration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitForOfStatement(XLangParser.ForOfStatementContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Identifier_for}
	 * labeled alternative in {@link XLangParser#expression_iterationLeft}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIdentifier_for(XLangParser.Identifier_forContext ctx);
	/**
	 * Visit a parse tree produced by the {@code VariableDeclaration_for}
	 * labeled alternative in {@link XLangParser#expression_iterationLeft}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVariableDeclaration_for(XLangParser.VariableDeclaration_forContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#continueStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitContinueStatement(XLangParser.ContinueStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#breakStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBreakStatement(XLangParser.BreakStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#returnStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitReturnStatement(XLangParser.ReturnStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#assignmentExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAssignmentExpression(XLangParser.AssignmentExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#expression_leftHandSide}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpression_leftHandSide(XLangParser.Expression_leftHandSideContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#switchStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSwitchStatement(XLangParser.SwitchStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#switchCases_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSwitchCases_(XLangParser.SwitchCases_Context ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#switchCase}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSwitchCase(XLangParser.SwitchCaseContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#statement_defaultClause}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatement_defaultClause(XLangParser.Statement_defaultClauseContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#throwStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitThrowStatement(XLangParser.ThrowStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#tryStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTryStatement(XLangParser.TryStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#catchClause}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCatchClause(XLangParser.CatchClauseContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#blockStatement_finally}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBlockStatement_finally(XLangParser.BlockStatement_finallyContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#functionDeclaration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFunctionDeclaration(XLangParser.FunctionDeclarationContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#parameterList_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParameterList_(XLangParser.ParameterList_Context ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#parameterDeclaration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParameterDeclaration(XLangParser.ParameterDeclarationContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#ast_identifierOrPattern}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAst_identifierOrPattern(XLangParser.Ast_identifierOrPatternContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#expression_initializer}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpression_initializer(XLangParser.Expression_initializerContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ArrayBinding_full}
	 * labeled alternative in {@link XLangParser#arrayBinding}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArrayBinding_full(XLangParser.ArrayBinding_fullContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ArrayBinding_rest}
	 * labeled alternative in {@link XLangParser#arrayBinding}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArrayBinding_rest(XLangParser.ArrayBinding_restContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#arrayElementBindings_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArrayElementBindings_(XLangParser.ArrayElementBindings_Context ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#arrayElementBinding}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArrayElementBinding(XLangParser.ArrayElementBindingContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ObjectBinding_full}
	 * labeled alternative in {@link XLangParser#objectBinding}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitObjectBinding_full(XLangParser.ObjectBinding_fullContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ObjectBinding_rest}
	 * labeled alternative in {@link XLangParser#objectBinding}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitObjectBinding_rest(XLangParser.ObjectBinding_restContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#propertyBindings_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPropertyBindings_(XLangParser.PropertyBindings_Context ctx);
	/**
	 * Visit a parse tree produced by the {@code PropertyBinding_full}
	 * labeled alternative in {@link XLangParser#propertyBinding}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPropertyBinding_full(XLangParser.PropertyBinding_fullContext ctx);
	/**
	 * Visit a parse tree produced by the {@code PropertyBinding_simple}
	 * labeled alternative in {@link XLangParser#propertyBinding}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPropertyBinding_simple(XLangParser.PropertyBinding_simpleContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#restBinding}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRestBinding(XLangParser.RestBindingContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#arrayExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArrayExpression(XLangParser.ArrayExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#elementList_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitElementList_(XLangParser.ElementList_Context ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#ast_arrayElement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAst_arrayElement(XLangParser.Ast_arrayElementContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#spreadElement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSpreadElement(XLangParser.SpreadElementContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#objectExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitObjectExpression(XLangParser.ObjectExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#objectProperties_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitObjectProperties_(XLangParser.ObjectProperties_Context ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#ast_objectProperty}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAst_objectProperty(XLangParser.Ast_objectPropertyContext ctx);
	/**
	 * Visit a parse tree produced by the {@code PropertyAssignment_assign}
	 * labeled alternative in {@link XLangParser#propertyAssignment}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPropertyAssignment_assign(XLangParser.PropertyAssignment_assignContext ctx);
	/**
	 * Visit a parse tree produced by the {@code PropertyAssignment_computed}
	 * labeled alternative in {@link XLangParser#propertyAssignment}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPropertyAssignment_computed(XLangParser.PropertyAssignment_computedContext ctx);
	/**
	 * Visit a parse tree produced by the {@code PropertyAssignment_shorthand}
	 * labeled alternative in {@link XLangParser#propertyAssignment}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPropertyAssignment_shorthand(XLangParser.PropertyAssignment_shorthandContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#arguments_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArguments_(XLangParser.Arguments_Context ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#sequenceExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSequenceExpression(XLangParser.SequenceExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#singleExpressions_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSingleExpressions_(XLangParser.SingleExpressions_Context ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#assignmentExpression_init}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAssignmentExpression_init(XLangParser.AssignmentExpression_initContext ctx);
	/**
	 * Visit a parse tree produced by the {@code SequenceExpression_init}
	 * labeled alternative in {@link XLangParser#expression_forInit}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSequenceExpression_init(XLangParser.SequenceExpression_initContext ctx);
	/**
	 * Visit a parse tree produced by the {@code VariableDeclaration_init}
	 * labeled alternative in {@link XLangParser#expression_forInit}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVariableDeclaration_init(XLangParser.VariableDeclaration_initContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#initExpressions_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInitExpressions_(XLangParser.InitExpressions_Context ctx);
	/**
	 * Visit a parse tree produced by the {@code TemplateStringExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTemplateStringExpression(XLangParser.TemplateStringExpressionContext ctx);
	/**
	 * Visit a parse tree produced by the {@code MacroExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMacroExpression(XLangParser.MacroExpressionContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ChainExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitChainExpression(XLangParser.ChainExpressionContext ctx);
	/**
	 * Visit a parse tree produced by the {@code TypeOfExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTypeOfExpression(XLangParser.TypeOfExpressionContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ObjectExpression_expr}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitObjectExpression_expr(XLangParser.ObjectExpression_exprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code NewExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNewExpression(XLangParser.NewExpressionContext ctx);
	/**
	 * Visit a parse tree produced by the {@code InExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInExpression(XLangParser.InExpressionContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Identifier_expr}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIdentifier_expr(XLangParser.Identifier_exprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ArrayExpression_expr}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArrayExpression_expr(XLangParser.ArrayExpression_exprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code UnaryExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUnaryExpression(XLangParser.UnaryExpressionContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Literal_expr}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLiteral_expr(XLangParser.Literal_exprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ThisExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitThisExpression(XLangParser.ThisExpressionContext ctx);
	/**
	 * Visit a parse tree produced by the {@code BinaryExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBinaryExpression(XLangParser.BinaryExpressionContext ctx);
	/**
	 * Visit a parse tree produced by the {@code IfStatement_expr}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIfStatement_expr(XLangParser.IfStatement_exprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code InstanceOfExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInstanceOfExpression(XLangParser.InstanceOfExpressionContext ctx);
	/**
	 * Visit a parse tree produced by the {@code CastExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCastExpression(XLangParser.CastExpressionContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ArrowFunctionExpression_expr}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArrowFunctionExpression_expr(XLangParser.ArrowFunctionExpression_exprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code MemberExpression_dot}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMemberExpression_dot(XLangParser.MemberExpression_dotContext ctx);
	/**
	 * Visit a parse tree produced by the {@code UpdateExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUpdateExpression(XLangParser.UpdateExpressionContext ctx);
	/**
	 * Visit a parse tree produced by the {@code SuperExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSuperExpression(XLangParser.SuperExpressionContext ctx);
	/**
	 * Visit a parse tree produced by the {@code CallExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCallExpression(XLangParser.CallExpressionContext ctx);
	/**
	 * Visit a parse tree produced by the {@code BraceExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBraceExpression(XLangParser.BraceExpressionContext ctx);
	/**
	 * Visit a parse tree produced by the {@code MemberExpression_index}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMemberExpression_index(XLangParser.MemberExpression_indexContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#templateStringLiteral}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTemplateStringLiteral(XLangParser.TemplateStringLiteralContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ArrowFunctionExpression_full}
	 * labeled alternative in {@link XLangParser#arrowFunctionExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArrowFunctionExpression_full(XLangParser.ArrowFunctionExpression_fullContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ArrowFunctionExpression_single}
	 * labeled alternative in {@link XLangParser#arrowFunctionExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArrowFunctionExpression_single(XLangParser.ArrowFunctionExpression_singleContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#parameterDeclaration_simple}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParameterDeclaration_simple(XLangParser.ParameterDeclaration_simpleContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#expression_functionBody}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpression_functionBody(XLangParser.Expression_functionBodyContext ctx);
	/**
	 * Visit a parse tree produced by the {@code MemberExpression_index2}
	 * labeled alternative in {@link XLangParser#memberExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMemberExpression_index2(XLangParser.MemberExpression_index2Context ctx);
	/**
	 * Visit a parse tree produced by the {@code MemberExpression_dot2}
	 * labeled alternative in {@link XLangParser#memberExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMemberExpression_dot2(XLangParser.MemberExpression_dot2Context ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#assignmentOperator_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAssignmentOperator_(XLangParser.AssignmentOperator_Context ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#eos__}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitEos__(XLangParser.Eos__Context ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#typeParameters_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTypeParameters_(XLangParser.TypeParameters_Context ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#typeParameterNode}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTypeParameterNode(XLangParser.TypeParameterNodeContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#typeArguments_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTypeArguments_(XLangParser.TypeArguments_Context ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#structuredTypeDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStructuredTypeDef(XLangParser.StructuredTypeDefContext ctx);
	/**
	 * Visit a parse tree produced by the {@code IntersectionTypeDef}
	 * labeled alternative in {@link XLangParser#typeNode_unionOrIntersection}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIntersectionTypeDef(XLangParser.IntersectionTypeDefContext ctx);
	/**
	 * Visit a parse tree produced by the {@code UnionTypeDef}
	 * labeled alternative in {@link XLangParser#typeNode_unionOrIntersection}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUnionTypeDef(XLangParser.UnionTypeDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#intersectionTypeDef_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIntersectionTypeDef_(XLangParser.IntersectionTypeDef_Context ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#unionTypeDef_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUnionTypeDef_(XLangParser.UnionTypeDef_Context ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#tupleTypeDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTupleTypeDef(XLangParser.TupleTypeDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#tupleTypeElements_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTupleTypeElements_(XLangParser.TupleTypeElements_Context ctx);
	/**
	 * Visit a parse tree produced by the {@code TypeNameNode_named}
	 * labeled alternative in {@link XLangParser#namedTypeNode}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTypeNameNode_named(XLangParser.TypeNameNode_namedContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ArrayTypeNode}
	 * labeled alternative in {@link XLangParser#namedTypeNode}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArrayTypeNode(XLangParser.ArrayTypeNodeContext ctx);
	/**
	 * Visit a parse tree produced by the {@code TypeNameNode_predefined_named}
	 * labeled alternative in {@link XLangParser#namedTypeNode}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTypeNameNode_predefined_named(XLangParser.TypeNameNode_predefined_namedContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ParameterizedTypeNode_named}
	 * labeled alternative in {@link XLangParser#namedTypeNode}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParameterizedTypeNode_named(XLangParser.ParameterizedTypeNode_namedContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#typeNameNode_predefined}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTypeNameNode_predefined(XLangParser.TypeNameNode_predefinedContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#parameterizedTypeNode}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParameterizedTypeNode(XLangParser.ParameterizedTypeNodeContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#objectTypeDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitObjectTypeDef(XLangParser.ObjectTypeDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#objectTypeElements_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitObjectTypeElements_(XLangParser.ObjectTypeElements_Context ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#functionTypeDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFunctionTypeDef(XLangParser.FunctionTypeDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#propertyTypeDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPropertyTypeDef(XLangParser.PropertyTypeDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#namedTypeNode_annotation}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNamedTypeNode_annotation(XLangParser.NamedTypeNode_annotationContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#structuredTypeDef_annotation}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStructuredTypeDef_annotation(XLangParser.StructuredTypeDef_annotationContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#functionParameterTypes_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFunctionParameterTypes_(XLangParser.FunctionParameterTypes_Context ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#functionArgTypeDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFunctionArgTypeDef(XLangParser.FunctionArgTypeDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#typeAliasDeclaration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTypeAliasDeclaration(XLangParser.TypeAliasDeclarationContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#enumDeclaration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitEnumDeclaration(XLangParser.EnumDeclarationContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#enumMembers_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitEnumMembers_(XLangParser.EnumMembers_Context ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#enumMember}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitEnumMember(XLangParser.EnumMemberContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#decorators}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDecorators(XLangParser.DecoratorsContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#decoratorElements_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDecoratorElements_(XLangParser.DecoratorElements_Context ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#decorator}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDecorator(XLangParser.DecoratorContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#metaObject}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMetaObject(XLangParser.MetaObjectContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#metaObjectProperties_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMetaObjectProperties_(XLangParser.MetaObjectProperties_Context ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#metaProperty}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMetaProperty(XLangParser.MetaPropertyContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#metaArray}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMetaArray(XLangParser.MetaArrayContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#metaArrayElements_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMetaArrayElements_(XLangParser.MetaArrayElements_Context ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#ast_metaValue}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAst_metaValue(XLangParser.Ast_metaValueContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#qualifiedName}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitQualifiedName(XLangParser.QualifiedNameContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#qualifiedName_name_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitQualifiedName_name_(XLangParser.QualifiedName_name_Context ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#qualifiedName_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitQualifiedName_(XLangParser.QualifiedName_Context ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#propertyName_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPropertyName_(XLangParser.PropertyName_Context ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#expression_propName}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpression_propName(XLangParser.Expression_propNameContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#identifier_ex}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIdentifier_ex(XLangParser.Identifier_exContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#identifier}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIdentifier(XLangParser.IdentifierContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#identifierOrKeyword_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIdentifierOrKeyword_(XLangParser.IdentifierOrKeyword_Context ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#reservedWord_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitReservedWord_(XLangParser.ReservedWord_Context ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#keyword_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitKeyword_(XLangParser.Keyword_Context ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#literal}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLiteral(XLangParser.LiteralContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#literal_numeric}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLiteral_numeric(XLangParser.Literal_numericContext ctx);
	/**
	 * Visit a parse tree produced by {@link XLangParser#literal_string}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLiteral_string(XLangParser.Literal_stringContext ctx);
}