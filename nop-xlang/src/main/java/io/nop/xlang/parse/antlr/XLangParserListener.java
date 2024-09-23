// Nop Generated from XLangParser.g4 by ANTLR 4.13.0
package io.nop.xlang.parse.antlr;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link XLangParser}.
 */
public interface XLangParserListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link XLangParser#program}.
	 * @param ctx the parse tree
	 */
	void enterProgram(XLangParser.ProgramContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#program}.
	 * @param ctx the parse tree
	 */
	void exitProgram(XLangParser.ProgramContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#topLevelStatements_}.
	 * @param ctx the parse tree
	 */
	void enterTopLevelStatements_(XLangParser.TopLevelStatements_Context ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#topLevelStatements_}.
	 * @param ctx the parse tree
	 */
	void exitTopLevelStatements_(XLangParser.TopLevelStatements_Context ctx);
	/**
	 * Enter a parse tree produced by the {@code ModuleDeclaration_import2}
	 * labeled alternative in {@link XLangParser#ast_topLevelStatement}.
	 * @param ctx the parse tree
	 */
	void enterModuleDeclaration_import2(XLangParser.ModuleDeclaration_import2Context ctx);
	/**
	 * Exit a parse tree produced by the {@code ModuleDeclaration_import2}
	 * labeled alternative in {@link XLangParser#ast_topLevelStatement}.
	 * @param ctx the parse tree
	 */
	void exitModuleDeclaration_import2(XLangParser.ModuleDeclaration_import2Context ctx);
	/**
	 * Enter a parse tree produced by the {@code ast_exportStatement2}
	 * labeled alternative in {@link XLangParser#ast_topLevelStatement}.
	 * @param ctx the parse tree
	 */
	void enterAst_exportStatement2(XLangParser.Ast_exportStatement2Context ctx);
	/**
	 * Exit a parse tree produced by the {@code ast_exportStatement2}
	 * labeled alternative in {@link XLangParser#ast_topLevelStatement}.
	 * @param ctx the parse tree
	 */
	void exitAst_exportStatement2(XLangParser.Ast_exportStatement2Context ctx);
	/**
	 * Enter a parse tree produced by the {@code ExportDeclaration_func}
	 * labeled alternative in {@link XLangParser#ast_topLevelStatement}.
	 * @param ctx the parse tree
	 */
	void enterExportDeclaration_func(XLangParser.ExportDeclaration_funcContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ExportDeclaration_func}
	 * labeled alternative in {@link XLangParser#ast_topLevelStatement}.
	 * @param ctx the parse tree
	 */
	void exitExportDeclaration_func(XLangParser.ExportDeclaration_funcContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ExportDeclaration_type}
	 * labeled alternative in {@link XLangParser#ast_topLevelStatement}.
	 * @param ctx the parse tree
	 */
	void enterExportDeclaration_type(XLangParser.ExportDeclaration_typeContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ExportDeclaration_type}
	 * labeled alternative in {@link XLangParser#ast_topLevelStatement}.
	 * @param ctx the parse tree
	 */
	void exitExportDeclaration_type(XLangParser.ExportDeclaration_typeContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ExportDeclaration_const}
	 * labeled alternative in {@link XLangParser#ast_topLevelStatement}.
	 * @param ctx the parse tree
	 */
	void enterExportDeclaration_const(XLangParser.ExportDeclaration_constContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ExportDeclaration_const}
	 * labeled alternative in {@link XLangParser#ast_topLevelStatement}.
	 * @param ctx the parse tree
	 */
	void exitExportDeclaration_const(XLangParser.ExportDeclaration_constContext ctx);
	/**
	 * Enter a parse tree produced by the {@code Statement_top}
	 * labeled alternative in {@link XLangParser#ast_topLevelStatement}.
	 * @param ctx the parse tree
	 */
	void enterStatement_top(XLangParser.Statement_topContext ctx);
	/**
	 * Exit a parse tree produced by the {@code Statement_top}
	 * labeled alternative in {@link XLangParser#ast_topLevelStatement}.
	 * @param ctx the parse tree
	 */
	void exitStatement_top(XLangParser.Statement_topContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#statement}.
	 * @param ctx the parse tree
	 */
	void enterStatement(XLangParser.StatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#statement}.
	 * @param ctx the parse tree
	 */
	void exitStatement(XLangParser.StatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#moduleDeclaration_import}.
	 * @param ctx the parse tree
	 */
	void enterModuleDeclaration_import(XLangParser.ModuleDeclaration_importContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#moduleDeclaration_import}.
	 * @param ctx the parse tree
	 */
	void exitModuleDeclaration_import(XLangParser.ModuleDeclaration_importContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#importDeclaration}.
	 * @param ctx the parse tree
	 */
	void enterImportDeclaration(XLangParser.ImportDeclarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#importDeclaration}.
	 * @param ctx the parse tree
	 */
	void exitImportDeclaration(XLangParser.ImportDeclarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#importSpecifiers_}.
	 * @param ctx the parse tree
	 */
	void enterImportSpecifiers_(XLangParser.ImportSpecifiers_Context ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#importSpecifiers_}.
	 * @param ctx the parse tree
	 */
	void exitImportSpecifiers_(XLangParser.ImportSpecifiers_Context ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#importSpecifier}.
	 * @param ctx the parse tree
	 */
	void enterImportSpecifier(XLangParser.ImportSpecifierContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#importSpecifier}.
	 * @param ctx the parse tree
	 */
	void exitImportSpecifier(XLangParser.ImportSpecifierContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#importAsDeclaration}.
	 * @param ctx the parse tree
	 */
	void enterImportAsDeclaration(XLangParser.ImportAsDeclarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#importAsDeclaration}.
	 * @param ctx the parse tree
	 */
	void exitImportAsDeclaration(XLangParser.ImportAsDeclarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#ast_importSource}.
	 * @param ctx the parse tree
	 */
	void enterAst_importSource(XLangParser.Ast_importSourceContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#ast_importSource}.
	 * @param ctx the parse tree
	 */
	void exitAst_importSource(XLangParser.Ast_importSourceContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#ast_exportStatement}.
	 * @param ctx the parse tree
	 */
	void enterAst_exportStatement(XLangParser.Ast_exportStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#ast_exportStatement}.
	 * @param ctx the parse tree
	 */
	void exitAst_exportStatement(XLangParser.Ast_exportStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#exportNamedDeclaration}.
	 * @param ctx the parse tree
	 */
	void enterExportNamedDeclaration(XLangParser.ExportNamedDeclarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#exportNamedDeclaration}.
	 * @param ctx the parse tree
	 */
	void exitExportNamedDeclaration(XLangParser.ExportNamedDeclarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#exportSpecifiers_}.
	 * @param ctx the parse tree
	 */
	void enterExportSpecifiers_(XLangParser.ExportSpecifiers_Context ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#exportSpecifiers_}.
	 * @param ctx the parse tree
	 */
	void exitExportSpecifiers_(XLangParser.ExportSpecifiers_Context ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#exportSpecifier}.
	 * @param ctx the parse tree
	 */
	void enterExportSpecifier(XLangParser.ExportSpecifierContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#exportSpecifier}.
	 * @param ctx the parse tree
	 */
	void exitExportSpecifier(XLangParser.ExportSpecifierContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#blockStatement}.
	 * @param ctx the parse tree
	 */
	void enterBlockStatement(XLangParser.BlockStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#blockStatement}.
	 * @param ctx the parse tree
	 */
	void exitBlockStatement(XLangParser.BlockStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#statements_}.
	 * @param ctx the parse tree
	 */
	void enterStatements_(XLangParser.Statements_Context ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#statements_}.
	 * @param ctx the parse tree
	 */
	void exitStatements_(XLangParser.Statements_Context ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#variableDeclaration_const}.
	 * @param ctx the parse tree
	 */
	void enterVariableDeclaration_const(XLangParser.VariableDeclaration_constContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#variableDeclaration_const}.
	 * @param ctx the parse tree
	 */
	void exitVariableDeclaration_const(XLangParser.VariableDeclaration_constContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#variableDeclaration}.
	 * @param ctx the parse tree
	 */
	void enterVariableDeclaration(XLangParser.VariableDeclarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#variableDeclaration}.
	 * @param ctx the parse tree
	 */
	void exitVariableDeclaration(XLangParser.VariableDeclarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#varModifier_}.
	 * @param ctx the parse tree
	 */
	void enterVarModifier_(XLangParser.VarModifier_Context ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#varModifier_}.
	 * @param ctx the parse tree
	 */
	void exitVarModifier_(XLangParser.VarModifier_Context ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#variableDeclarators_}.
	 * @param ctx the parse tree
	 */
	void enterVariableDeclarators_(XLangParser.VariableDeclarators_Context ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#variableDeclarators_}.
	 * @param ctx the parse tree
	 */
	void exitVariableDeclarators_(XLangParser.VariableDeclarators_Context ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#variableDeclarator}.
	 * @param ctx the parse tree
	 */
	void enterVariableDeclarator(XLangParser.VariableDeclaratorContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#variableDeclarator}.
	 * @param ctx the parse tree
	 */
	void exitVariableDeclarator(XLangParser.VariableDeclaratorContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#emptyStatement}.
	 * @param ctx the parse tree
	 */
	void enterEmptyStatement(XLangParser.EmptyStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#emptyStatement}.
	 * @param ctx the parse tree
	 */
	void exitEmptyStatement(XLangParser.EmptyStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#expressionStatement}.
	 * @param ctx the parse tree
	 */
	void enterExpressionStatement(XLangParser.ExpressionStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#expressionStatement}.
	 * @param ctx the parse tree
	 */
	void exitExpressionStatement(XLangParser.ExpressionStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#ifStatement}.
	 * @param ctx the parse tree
	 */
	void enterIfStatement(XLangParser.IfStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#ifStatement}.
	 * @param ctx the parse tree
	 */
	void exitIfStatement(XLangParser.IfStatementContext ctx);
	/**
	 * Enter a parse tree produced by the {@code DoWhileStatement}
	 * labeled alternative in {@link XLangParser#statement_iteration}.
	 * @param ctx the parse tree
	 */
	void enterDoWhileStatement(XLangParser.DoWhileStatementContext ctx);
	/**
	 * Exit a parse tree produced by the {@code DoWhileStatement}
	 * labeled alternative in {@link XLangParser#statement_iteration}.
	 * @param ctx the parse tree
	 */
	void exitDoWhileStatement(XLangParser.DoWhileStatementContext ctx);
	/**
	 * Enter a parse tree produced by the {@code WhileStatement}
	 * labeled alternative in {@link XLangParser#statement_iteration}.
	 * @param ctx the parse tree
	 */
	void enterWhileStatement(XLangParser.WhileStatementContext ctx);
	/**
	 * Exit a parse tree produced by the {@code WhileStatement}
	 * labeled alternative in {@link XLangParser#statement_iteration}.
	 * @param ctx the parse tree
	 */
	void exitWhileStatement(XLangParser.WhileStatementContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ForStatement}
	 * labeled alternative in {@link XLangParser#statement_iteration}.
	 * @param ctx the parse tree
	 */
	void enterForStatement(XLangParser.ForStatementContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ForStatement}
	 * labeled alternative in {@link XLangParser#statement_iteration}.
	 * @param ctx the parse tree
	 */
	void exitForStatement(XLangParser.ForStatementContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ForInStatement}
	 * labeled alternative in {@link XLangParser#statement_iteration}.
	 * @param ctx the parse tree
	 */
	void enterForInStatement(XLangParser.ForInStatementContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ForInStatement}
	 * labeled alternative in {@link XLangParser#statement_iteration}.
	 * @param ctx the parse tree
	 */
	void exitForInStatement(XLangParser.ForInStatementContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ForOfStatement}
	 * labeled alternative in {@link XLangParser#statement_iteration}.
	 * @param ctx the parse tree
	 */
	void enterForOfStatement(XLangParser.ForOfStatementContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ForOfStatement}
	 * labeled alternative in {@link XLangParser#statement_iteration}.
	 * @param ctx the parse tree
	 */
	void exitForOfStatement(XLangParser.ForOfStatementContext ctx);
	/**
	 * Enter a parse tree produced by the {@code Identifier_for}
	 * labeled alternative in {@link XLangParser#expression_iterationLeft}.
	 * @param ctx the parse tree
	 */
	void enterIdentifier_for(XLangParser.Identifier_forContext ctx);
	/**
	 * Exit a parse tree produced by the {@code Identifier_for}
	 * labeled alternative in {@link XLangParser#expression_iterationLeft}.
	 * @param ctx the parse tree
	 */
	void exitIdentifier_for(XLangParser.Identifier_forContext ctx);
	/**
	 * Enter a parse tree produced by the {@code VariableDeclaration_for}
	 * labeled alternative in {@link XLangParser#expression_iterationLeft}.
	 * @param ctx the parse tree
	 */
	void enterVariableDeclaration_for(XLangParser.VariableDeclaration_forContext ctx);
	/**
	 * Exit a parse tree produced by the {@code VariableDeclaration_for}
	 * labeled alternative in {@link XLangParser#expression_iterationLeft}.
	 * @param ctx the parse tree
	 */
	void exitVariableDeclaration_for(XLangParser.VariableDeclaration_forContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#continueStatement}.
	 * @param ctx the parse tree
	 */
	void enterContinueStatement(XLangParser.ContinueStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#continueStatement}.
	 * @param ctx the parse tree
	 */
	void exitContinueStatement(XLangParser.ContinueStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#breakStatement}.
	 * @param ctx the parse tree
	 */
	void enterBreakStatement(XLangParser.BreakStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#breakStatement}.
	 * @param ctx the parse tree
	 */
	void exitBreakStatement(XLangParser.BreakStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#returnStatement}.
	 * @param ctx the parse tree
	 */
	void enterReturnStatement(XLangParser.ReturnStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#returnStatement}.
	 * @param ctx the parse tree
	 */
	void exitReturnStatement(XLangParser.ReturnStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#assignmentExpression}.
	 * @param ctx the parse tree
	 */
	void enterAssignmentExpression(XLangParser.AssignmentExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#assignmentExpression}.
	 * @param ctx the parse tree
	 */
	void exitAssignmentExpression(XLangParser.AssignmentExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#expression_leftHandSide}.
	 * @param ctx the parse tree
	 */
	void enterExpression_leftHandSide(XLangParser.Expression_leftHandSideContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#expression_leftHandSide}.
	 * @param ctx the parse tree
	 */
	void exitExpression_leftHandSide(XLangParser.Expression_leftHandSideContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#switchStatement}.
	 * @param ctx the parse tree
	 */
	void enterSwitchStatement(XLangParser.SwitchStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#switchStatement}.
	 * @param ctx the parse tree
	 */
	void exitSwitchStatement(XLangParser.SwitchStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#switchCases_}.
	 * @param ctx the parse tree
	 */
	void enterSwitchCases_(XLangParser.SwitchCases_Context ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#switchCases_}.
	 * @param ctx the parse tree
	 */
	void exitSwitchCases_(XLangParser.SwitchCases_Context ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#switchCase}.
	 * @param ctx the parse tree
	 */
	void enterSwitchCase(XLangParser.SwitchCaseContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#switchCase}.
	 * @param ctx the parse tree
	 */
	void exitSwitchCase(XLangParser.SwitchCaseContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#statement_defaultClause}.
	 * @param ctx the parse tree
	 */
	void enterStatement_defaultClause(XLangParser.Statement_defaultClauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#statement_defaultClause}.
	 * @param ctx the parse tree
	 */
	void exitStatement_defaultClause(XLangParser.Statement_defaultClauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#throwStatement}.
	 * @param ctx the parse tree
	 */
	void enterThrowStatement(XLangParser.ThrowStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#throwStatement}.
	 * @param ctx the parse tree
	 */
	void exitThrowStatement(XLangParser.ThrowStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#tryStatement}.
	 * @param ctx the parse tree
	 */
	void enterTryStatement(XLangParser.TryStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#tryStatement}.
	 * @param ctx the parse tree
	 */
	void exitTryStatement(XLangParser.TryStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#catchClause}.
	 * @param ctx the parse tree
	 */
	void enterCatchClause(XLangParser.CatchClauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#catchClause}.
	 * @param ctx the parse tree
	 */
	void exitCatchClause(XLangParser.CatchClauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#blockStatement_finally}.
	 * @param ctx the parse tree
	 */
	void enterBlockStatement_finally(XLangParser.BlockStatement_finallyContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#blockStatement_finally}.
	 * @param ctx the parse tree
	 */
	void exitBlockStatement_finally(XLangParser.BlockStatement_finallyContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#functionDeclaration}.
	 * @param ctx the parse tree
	 */
	void enterFunctionDeclaration(XLangParser.FunctionDeclarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#functionDeclaration}.
	 * @param ctx the parse tree
	 */
	void exitFunctionDeclaration(XLangParser.FunctionDeclarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#parameterList_}.
	 * @param ctx the parse tree
	 */
	void enterParameterList_(XLangParser.ParameterList_Context ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#parameterList_}.
	 * @param ctx the parse tree
	 */
	void exitParameterList_(XLangParser.ParameterList_Context ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#parameterDeclaration}.
	 * @param ctx the parse tree
	 */
	void enterParameterDeclaration(XLangParser.ParameterDeclarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#parameterDeclaration}.
	 * @param ctx the parse tree
	 */
	void exitParameterDeclaration(XLangParser.ParameterDeclarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#ast_identifierOrPattern}.
	 * @param ctx the parse tree
	 */
	void enterAst_identifierOrPattern(XLangParser.Ast_identifierOrPatternContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#ast_identifierOrPattern}.
	 * @param ctx the parse tree
	 */
	void exitAst_identifierOrPattern(XLangParser.Ast_identifierOrPatternContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#expression_initializer}.
	 * @param ctx the parse tree
	 */
	void enterExpression_initializer(XLangParser.Expression_initializerContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#expression_initializer}.
	 * @param ctx the parse tree
	 */
	void exitExpression_initializer(XLangParser.Expression_initializerContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ArrayBinding_full}
	 * labeled alternative in {@link XLangParser#arrayBinding}.
	 * @param ctx the parse tree
	 */
	void enterArrayBinding_full(XLangParser.ArrayBinding_fullContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ArrayBinding_full}
	 * labeled alternative in {@link XLangParser#arrayBinding}.
	 * @param ctx the parse tree
	 */
	void exitArrayBinding_full(XLangParser.ArrayBinding_fullContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ArrayBinding_rest}
	 * labeled alternative in {@link XLangParser#arrayBinding}.
	 * @param ctx the parse tree
	 */
	void enterArrayBinding_rest(XLangParser.ArrayBinding_restContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ArrayBinding_rest}
	 * labeled alternative in {@link XLangParser#arrayBinding}.
	 * @param ctx the parse tree
	 */
	void exitArrayBinding_rest(XLangParser.ArrayBinding_restContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#arrayElementBindings_}.
	 * @param ctx the parse tree
	 */
	void enterArrayElementBindings_(XLangParser.ArrayElementBindings_Context ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#arrayElementBindings_}.
	 * @param ctx the parse tree
	 */
	void exitArrayElementBindings_(XLangParser.ArrayElementBindings_Context ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#arrayElementBinding}.
	 * @param ctx the parse tree
	 */
	void enterArrayElementBinding(XLangParser.ArrayElementBindingContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#arrayElementBinding}.
	 * @param ctx the parse tree
	 */
	void exitArrayElementBinding(XLangParser.ArrayElementBindingContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ObjectBinding_full}
	 * labeled alternative in {@link XLangParser#objectBinding}.
	 * @param ctx the parse tree
	 */
	void enterObjectBinding_full(XLangParser.ObjectBinding_fullContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ObjectBinding_full}
	 * labeled alternative in {@link XLangParser#objectBinding}.
	 * @param ctx the parse tree
	 */
	void exitObjectBinding_full(XLangParser.ObjectBinding_fullContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ObjectBinding_rest}
	 * labeled alternative in {@link XLangParser#objectBinding}.
	 * @param ctx the parse tree
	 */
	void enterObjectBinding_rest(XLangParser.ObjectBinding_restContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ObjectBinding_rest}
	 * labeled alternative in {@link XLangParser#objectBinding}.
	 * @param ctx the parse tree
	 */
	void exitObjectBinding_rest(XLangParser.ObjectBinding_restContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#propertyBindings_}.
	 * @param ctx the parse tree
	 */
	void enterPropertyBindings_(XLangParser.PropertyBindings_Context ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#propertyBindings_}.
	 * @param ctx the parse tree
	 */
	void exitPropertyBindings_(XLangParser.PropertyBindings_Context ctx);
	/**
	 * Enter a parse tree produced by the {@code PropertyBinding_full}
	 * labeled alternative in {@link XLangParser#propertyBinding}.
	 * @param ctx the parse tree
	 */
	void enterPropertyBinding_full(XLangParser.PropertyBinding_fullContext ctx);
	/**
	 * Exit a parse tree produced by the {@code PropertyBinding_full}
	 * labeled alternative in {@link XLangParser#propertyBinding}.
	 * @param ctx the parse tree
	 */
	void exitPropertyBinding_full(XLangParser.PropertyBinding_fullContext ctx);
	/**
	 * Enter a parse tree produced by the {@code PropertyBinding_simple}
	 * labeled alternative in {@link XLangParser#propertyBinding}.
	 * @param ctx the parse tree
	 */
	void enterPropertyBinding_simple(XLangParser.PropertyBinding_simpleContext ctx);
	/**
	 * Exit a parse tree produced by the {@code PropertyBinding_simple}
	 * labeled alternative in {@link XLangParser#propertyBinding}.
	 * @param ctx the parse tree
	 */
	void exitPropertyBinding_simple(XLangParser.PropertyBinding_simpleContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#restBinding}.
	 * @param ctx the parse tree
	 */
	void enterRestBinding(XLangParser.RestBindingContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#restBinding}.
	 * @param ctx the parse tree
	 */
	void exitRestBinding(XLangParser.RestBindingContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#arrayExpression}.
	 * @param ctx the parse tree
	 */
	void enterArrayExpression(XLangParser.ArrayExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#arrayExpression}.
	 * @param ctx the parse tree
	 */
	void exitArrayExpression(XLangParser.ArrayExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#elementList_}.
	 * @param ctx the parse tree
	 */
	void enterElementList_(XLangParser.ElementList_Context ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#elementList_}.
	 * @param ctx the parse tree
	 */
	void exitElementList_(XLangParser.ElementList_Context ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#ast_arrayElement}.
	 * @param ctx the parse tree
	 */
	void enterAst_arrayElement(XLangParser.Ast_arrayElementContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#ast_arrayElement}.
	 * @param ctx the parse tree
	 */
	void exitAst_arrayElement(XLangParser.Ast_arrayElementContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#spreadElement}.
	 * @param ctx the parse tree
	 */
	void enterSpreadElement(XLangParser.SpreadElementContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#spreadElement}.
	 * @param ctx the parse tree
	 */
	void exitSpreadElement(XLangParser.SpreadElementContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#objectExpression}.
	 * @param ctx the parse tree
	 */
	void enterObjectExpression(XLangParser.ObjectExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#objectExpression}.
	 * @param ctx the parse tree
	 */
	void exitObjectExpression(XLangParser.ObjectExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#objectProperties_}.
	 * @param ctx the parse tree
	 */
	void enterObjectProperties_(XLangParser.ObjectProperties_Context ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#objectProperties_}.
	 * @param ctx the parse tree
	 */
	void exitObjectProperties_(XLangParser.ObjectProperties_Context ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#ast_objectProperty}.
	 * @param ctx the parse tree
	 */
	void enterAst_objectProperty(XLangParser.Ast_objectPropertyContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#ast_objectProperty}.
	 * @param ctx the parse tree
	 */
	void exitAst_objectProperty(XLangParser.Ast_objectPropertyContext ctx);
	/**
	 * Enter a parse tree produced by the {@code PropertyAssignment_assign}
	 * labeled alternative in {@link XLangParser#propertyAssignment}.
	 * @param ctx the parse tree
	 */
	void enterPropertyAssignment_assign(XLangParser.PropertyAssignment_assignContext ctx);
	/**
	 * Exit a parse tree produced by the {@code PropertyAssignment_assign}
	 * labeled alternative in {@link XLangParser#propertyAssignment}.
	 * @param ctx the parse tree
	 */
	void exitPropertyAssignment_assign(XLangParser.PropertyAssignment_assignContext ctx);
	/**
	 * Enter a parse tree produced by the {@code PropertyAssignment_computed}
	 * labeled alternative in {@link XLangParser#propertyAssignment}.
	 * @param ctx the parse tree
	 */
	void enterPropertyAssignment_computed(XLangParser.PropertyAssignment_computedContext ctx);
	/**
	 * Exit a parse tree produced by the {@code PropertyAssignment_computed}
	 * labeled alternative in {@link XLangParser#propertyAssignment}.
	 * @param ctx the parse tree
	 */
	void exitPropertyAssignment_computed(XLangParser.PropertyAssignment_computedContext ctx);
	/**
	 * Enter a parse tree produced by the {@code PropertyAssignment_shorthand}
	 * labeled alternative in {@link XLangParser#propertyAssignment}.
	 * @param ctx the parse tree
	 */
	void enterPropertyAssignment_shorthand(XLangParser.PropertyAssignment_shorthandContext ctx);
	/**
	 * Exit a parse tree produced by the {@code PropertyAssignment_shorthand}
	 * labeled alternative in {@link XLangParser#propertyAssignment}.
	 * @param ctx the parse tree
	 */
	void exitPropertyAssignment_shorthand(XLangParser.PropertyAssignment_shorthandContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#arguments_}.
	 * @param ctx the parse tree
	 */
	void enterArguments_(XLangParser.Arguments_Context ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#arguments_}.
	 * @param ctx the parse tree
	 */
	void exitArguments_(XLangParser.Arguments_Context ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#sequenceExpression}.
	 * @param ctx the parse tree
	 */
	void enterSequenceExpression(XLangParser.SequenceExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#sequenceExpression}.
	 * @param ctx the parse tree
	 */
	void exitSequenceExpression(XLangParser.SequenceExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#singleExpressions_}.
	 * @param ctx the parse tree
	 */
	void enterSingleExpressions_(XLangParser.SingleExpressions_Context ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#singleExpressions_}.
	 * @param ctx the parse tree
	 */
	void exitSingleExpressions_(XLangParser.SingleExpressions_Context ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#assignmentExpression_init}.
	 * @param ctx the parse tree
	 */
	void enterAssignmentExpression_init(XLangParser.AssignmentExpression_initContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#assignmentExpression_init}.
	 * @param ctx the parse tree
	 */
	void exitAssignmentExpression_init(XLangParser.AssignmentExpression_initContext ctx);
	/**
	 * Enter a parse tree produced by the {@code SequenceExpression_init}
	 * labeled alternative in {@link XLangParser#expression_forInit}.
	 * @param ctx the parse tree
	 */
	void enterSequenceExpression_init(XLangParser.SequenceExpression_initContext ctx);
	/**
	 * Exit a parse tree produced by the {@code SequenceExpression_init}
	 * labeled alternative in {@link XLangParser#expression_forInit}.
	 * @param ctx the parse tree
	 */
	void exitSequenceExpression_init(XLangParser.SequenceExpression_initContext ctx);
	/**
	 * Enter a parse tree produced by the {@code VariableDeclaration_init}
	 * labeled alternative in {@link XLangParser#expression_forInit}.
	 * @param ctx the parse tree
	 */
	void enterVariableDeclaration_init(XLangParser.VariableDeclaration_initContext ctx);
	/**
	 * Exit a parse tree produced by the {@code VariableDeclaration_init}
	 * labeled alternative in {@link XLangParser#expression_forInit}.
	 * @param ctx the parse tree
	 */
	void exitVariableDeclaration_init(XLangParser.VariableDeclaration_initContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#initExpressions_}.
	 * @param ctx the parse tree
	 */
	void enterInitExpressions_(XLangParser.InitExpressions_Context ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#initExpressions_}.
	 * @param ctx the parse tree
	 */
	void exitInitExpressions_(XLangParser.InitExpressions_Context ctx);
	/**
	 * Enter a parse tree produced by the {@code TemplateStringExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void enterTemplateStringExpression(XLangParser.TemplateStringExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code TemplateStringExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void exitTemplateStringExpression(XLangParser.TemplateStringExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code MacroExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void enterMacroExpression(XLangParser.MacroExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code MacroExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void exitMacroExpression(XLangParser.MacroExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ChainExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void enterChainExpression(XLangParser.ChainExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ChainExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void exitChainExpression(XLangParser.ChainExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code TypeOfExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void enterTypeOfExpression(XLangParser.TypeOfExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code TypeOfExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void exitTypeOfExpression(XLangParser.TypeOfExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ObjectExpression_expr}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void enterObjectExpression_expr(XLangParser.ObjectExpression_exprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ObjectExpression_expr}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void exitObjectExpression_expr(XLangParser.ObjectExpression_exprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code NewExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void enterNewExpression(XLangParser.NewExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code NewExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void exitNewExpression(XLangParser.NewExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code InExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void enterInExpression(XLangParser.InExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code InExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void exitInExpression(XLangParser.InExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code Identifier_expr}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void enterIdentifier_expr(XLangParser.Identifier_exprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code Identifier_expr}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void exitIdentifier_expr(XLangParser.Identifier_exprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ArrayExpression_expr}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void enterArrayExpression_expr(XLangParser.ArrayExpression_exprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ArrayExpression_expr}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void exitArrayExpression_expr(XLangParser.ArrayExpression_exprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code UnaryExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void enterUnaryExpression(XLangParser.UnaryExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code UnaryExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void exitUnaryExpression(XLangParser.UnaryExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code Literal_expr}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void enterLiteral_expr(XLangParser.Literal_exprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code Literal_expr}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void exitLiteral_expr(XLangParser.Literal_exprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ThisExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void enterThisExpression(XLangParser.ThisExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ThisExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void exitThisExpression(XLangParser.ThisExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code BinaryExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void enterBinaryExpression(XLangParser.BinaryExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code BinaryExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void exitBinaryExpression(XLangParser.BinaryExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code IfStatement_expr}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void enterIfStatement_expr(XLangParser.IfStatement_exprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code IfStatement_expr}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void exitIfStatement_expr(XLangParser.IfStatement_exprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code InstanceOfExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void enterInstanceOfExpression(XLangParser.InstanceOfExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code InstanceOfExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void exitInstanceOfExpression(XLangParser.InstanceOfExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code CastExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void enterCastExpression(XLangParser.CastExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code CastExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void exitCastExpression(XLangParser.CastExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ArrowFunctionExpression_expr}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void enterArrowFunctionExpression_expr(XLangParser.ArrowFunctionExpression_exprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ArrowFunctionExpression_expr}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void exitArrowFunctionExpression_expr(XLangParser.ArrowFunctionExpression_exprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code MemberExpression_dot}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void enterMemberExpression_dot(XLangParser.MemberExpression_dotContext ctx);
	/**
	 * Exit a parse tree produced by the {@code MemberExpression_dot}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void exitMemberExpression_dot(XLangParser.MemberExpression_dotContext ctx);
	/**
	 * Enter a parse tree produced by the {@code UpdateExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void enterUpdateExpression(XLangParser.UpdateExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code UpdateExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void exitUpdateExpression(XLangParser.UpdateExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code SuperExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void enterSuperExpression(XLangParser.SuperExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code SuperExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void exitSuperExpression(XLangParser.SuperExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code CallExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void enterCallExpression(XLangParser.CallExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code CallExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void exitCallExpression(XLangParser.CallExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code BraceExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void enterBraceExpression(XLangParser.BraceExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code BraceExpression}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void exitBraceExpression(XLangParser.BraceExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code MemberExpression_index}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void enterMemberExpression_index(XLangParser.MemberExpression_indexContext ctx);
	/**
	 * Exit a parse tree produced by the {@code MemberExpression_index}
	 * labeled alternative in {@link XLangParser#expression_single}.
	 * @param ctx the parse tree
	 */
	void exitMemberExpression_index(XLangParser.MemberExpression_indexContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#templateStringLiteral}.
	 * @param ctx the parse tree
	 */
	void enterTemplateStringLiteral(XLangParser.TemplateStringLiteralContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#templateStringLiteral}.
	 * @param ctx the parse tree
	 */
	void exitTemplateStringLiteral(XLangParser.TemplateStringLiteralContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ArrowFunctionExpression_full}
	 * labeled alternative in {@link XLangParser#arrowFunctionExpression}.
	 * @param ctx the parse tree
	 */
	void enterArrowFunctionExpression_full(XLangParser.ArrowFunctionExpression_fullContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ArrowFunctionExpression_full}
	 * labeled alternative in {@link XLangParser#arrowFunctionExpression}.
	 * @param ctx the parse tree
	 */
	void exitArrowFunctionExpression_full(XLangParser.ArrowFunctionExpression_fullContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ArrowFunctionExpression_single}
	 * labeled alternative in {@link XLangParser#arrowFunctionExpression}.
	 * @param ctx the parse tree
	 */
	void enterArrowFunctionExpression_single(XLangParser.ArrowFunctionExpression_singleContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ArrowFunctionExpression_single}
	 * labeled alternative in {@link XLangParser#arrowFunctionExpression}.
	 * @param ctx the parse tree
	 */
	void exitArrowFunctionExpression_single(XLangParser.ArrowFunctionExpression_singleContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#parameterDeclaration_simple}.
	 * @param ctx the parse tree
	 */
	void enterParameterDeclaration_simple(XLangParser.ParameterDeclaration_simpleContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#parameterDeclaration_simple}.
	 * @param ctx the parse tree
	 */
	void exitParameterDeclaration_simple(XLangParser.ParameterDeclaration_simpleContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#expression_functionBody}.
	 * @param ctx the parse tree
	 */
	void enterExpression_functionBody(XLangParser.Expression_functionBodyContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#expression_functionBody}.
	 * @param ctx the parse tree
	 */
	void exitExpression_functionBody(XLangParser.Expression_functionBodyContext ctx);
	/**
	 * Enter a parse tree produced by the {@code MemberExpression_index2}
	 * labeled alternative in {@link XLangParser#memberExpression}.
	 * @param ctx the parse tree
	 */
	void enterMemberExpression_index2(XLangParser.MemberExpression_index2Context ctx);
	/**
	 * Exit a parse tree produced by the {@code MemberExpression_index2}
	 * labeled alternative in {@link XLangParser#memberExpression}.
	 * @param ctx the parse tree
	 */
	void exitMemberExpression_index2(XLangParser.MemberExpression_index2Context ctx);
	/**
	 * Enter a parse tree produced by the {@code MemberExpression_dot2}
	 * labeled alternative in {@link XLangParser#memberExpression}.
	 * @param ctx the parse tree
	 */
	void enterMemberExpression_dot2(XLangParser.MemberExpression_dot2Context ctx);
	/**
	 * Exit a parse tree produced by the {@code MemberExpression_dot2}
	 * labeled alternative in {@link XLangParser#memberExpression}.
	 * @param ctx the parse tree
	 */
	void exitMemberExpression_dot2(XLangParser.MemberExpression_dot2Context ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#assignmentOperator_}.
	 * @param ctx the parse tree
	 */
	void enterAssignmentOperator_(XLangParser.AssignmentOperator_Context ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#assignmentOperator_}.
	 * @param ctx the parse tree
	 */
	void exitAssignmentOperator_(XLangParser.AssignmentOperator_Context ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#eos__}.
	 * @param ctx the parse tree
	 */
	void enterEos__(XLangParser.Eos__Context ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#eos__}.
	 * @param ctx the parse tree
	 */
	void exitEos__(XLangParser.Eos__Context ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#typeParameters_}.
	 * @param ctx the parse tree
	 */
	void enterTypeParameters_(XLangParser.TypeParameters_Context ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#typeParameters_}.
	 * @param ctx the parse tree
	 */
	void exitTypeParameters_(XLangParser.TypeParameters_Context ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#typeParameterNode}.
	 * @param ctx the parse tree
	 */
	void enterTypeParameterNode(XLangParser.TypeParameterNodeContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#typeParameterNode}.
	 * @param ctx the parse tree
	 */
	void exitTypeParameterNode(XLangParser.TypeParameterNodeContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#typeArguments_}.
	 * @param ctx the parse tree
	 */
	void enterTypeArguments_(XLangParser.TypeArguments_Context ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#typeArguments_}.
	 * @param ctx the parse tree
	 */
	void exitTypeArguments_(XLangParser.TypeArguments_Context ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#structuredTypeDef}.
	 * @param ctx the parse tree
	 */
	void enterStructuredTypeDef(XLangParser.StructuredTypeDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#structuredTypeDef}.
	 * @param ctx the parse tree
	 */
	void exitStructuredTypeDef(XLangParser.StructuredTypeDefContext ctx);
	/**
	 * Enter a parse tree produced by the {@code IntersectionTypeDef}
	 * labeled alternative in {@link XLangParser#typeNode_unionOrIntersection}.
	 * @param ctx the parse tree
	 */
	void enterIntersectionTypeDef(XLangParser.IntersectionTypeDefContext ctx);
	/**
	 * Exit a parse tree produced by the {@code IntersectionTypeDef}
	 * labeled alternative in {@link XLangParser#typeNode_unionOrIntersection}.
	 * @param ctx the parse tree
	 */
	void exitIntersectionTypeDef(XLangParser.IntersectionTypeDefContext ctx);
	/**
	 * Enter a parse tree produced by the {@code UnionTypeDef}
	 * labeled alternative in {@link XLangParser#typeNode_unionOrIntersection}.
	 * @param ctx the parse tree
	 */
	void enterUnionTypeDef(XLangParser.UnionTypeDefContext ctx);
	/**
	 * Exit a parse tree produced by the {@code UnionTypeDef}
	 * labeled alternative in {@link XLangParser#typeNode_unionOrIntersection}.
	 * @param ctx the parse tree
	 */
	void exitUnionTypeDef(XLangParser.UnionTypeDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#intersectionTypeDef_}.
	 * @param ctx the parse tree
	 */
	void enterIntersectionTypeDef_(XLangParser.IntersectionTypeDef_Context ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#intersectionTypeDef_}.
	 * @param ctx the parse tree
	 */
	void exitIntersectionTypeDef_(XLangParser.IntersectionTypeDef_Context ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#unionTypeDef_}.
	 * @param ctx the parse tree
	 */
	void enterUnionTypeDef_(XLangParser.UnionTypeDef_Context ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#unionTypeDef_}.
	 * @param ctx the parse tree
	 */
	void exitUnionTypeDef_(XLangParser.UnionTypeDef_Context ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#tupleTypeDef}.
	 * @param ctx the parse tree
	 */
	void enterTupleTypeDef(XLangParser.TupleTypeDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#tupleTypeDef}.
	 * @param ctx the parse tree
	 */
	void exitTupleTypeDef(XLangParser.TupleTypeDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#tupleTypeElements_}.
	 * @param ctx the parse tree
	 */
	void enterTupleTypeElements_(XLangParser.TupleTypeElements_Context ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#tupleTypeElements_}.
	 * @param ctx the parse tree
	 */
	void exitTupleTypeElements_(XLangParser.TupleTypeElements_Context ctx);
	/**
	 * Enter a parse tree produced by the {@code TypeNameNode_named}
	 * labeled alternative in {@link XLangParser#namedTypeNode}.
	 * @param ctx the parse tree
	 */
	void enterTypeNameNode_named(XLangParser.TypeNameNode_namedContext ctx);
	/**
	 * Exit a parse tree produced by the {@code TypeNameNode_named}
	 * labeled alternative in {@link XLangParser#namedTypeNode}.
	 * @param ctx the parse tree
	 */
	void exitTypeNameNode_named(XLangParser.TypeNameNode_namedContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ArrayTypeNode}
	 * labeled alternative in {@link XLangParser#namedTypeNode}.
	 * @param ctx the parse tree
	 */
	void enterArrayTypeNode(XLangParser.ArrayTypeNodeContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ArrayTypeNode}
	 * labeled alternative in {@link XLangParser#namedTypeNode}.
	 * @param ctx the parse tree
	 */
	void exitArrayTypeNode(XLangParser.ArrayTypeNodeContext ctx);
	/**
	 * Enter a parse tree produced by the {@code TypeNameNode_predefined_named}
	 * labeled alternative in {@link XLangParser#namedTypeNode}.
	 * @param ctx the parse tree
	 */
	void enterTypeNameNode_predefined_named(XLangParser.TypeNameNode_predefined_namedContext ctx);
	/**
	 * Exit a parse tree produced by the {@code TypeNameNode_predefined_named}
	 * labeled alternative in {@link XLangParser#namedTypeNode}.
	 * @param ctx the parse tree
	 */
	void exitTypeNameNode_predefined_named(XLangParser.TypeNameNode_predefined_namedContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ParameterizedTypeNode_named}
	 * labeled alternative in {@link XLangParser#namedTypeNode}.
	 * @param ctx the parse tree
	 */
	void enterParameterizedTypeNode_named(XLangParser.ParameterizedTypeNode_namedContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ParameterizedTypeNode_named}
	 * labeled alternative in {@link XLangParser#namedTypeNode}.
	 * @param ctx the parse tree
	 */
	void exitParameterizedTypeNode_named(XLangParser.ParameterizedTypeNode_namedContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#typeNameNode_predefined}.
	 * @param ctx the parse tree
	 */
	void enterTypeNameNode_predefined(XLangParser.TypeNameNode_predefinedContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#typeNameNode_predefined}.
	 * @param ctx the parse tree
	 */
	void exitTypeNameNode_predefined(XLangParser.TypeNameNode_predefinedContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#parameterizedTypeNode}.
	 * @param ctx the parse tree
	 */
	void enterParameterizedTypeNode(XLangParser.ParameterizedTypeNodeContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#parameterizedTypeNode}.
	 * @param ctx the parse tree
	 */
	void exitParameterizedTypeNode(XLangParser.ParameterizedTypeNodeContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#objectTypeDef}.
	 * @param ctx the parse tree
	 */
	void enterObjectTypeDef(XLangParser.ObjectTypeDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#objectTypeDef}.
	 * @param ctx the parse tree
	 */
	void exitObjectTypeDef(XLangParser.ObjectTypeDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#objectTypeElements_}.
	 * @param ctx the parse tree
	 */
	void enterObjectTypeElements_(XLangParser.ObjectTypeElements_Context ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#objectTypeElements_}.
	 * @param ctx the parse tree
	 */
	void exitObjectTypeElements_(XLangParser.ObjectTypeElements_Context ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#functionTypeDef}.
	 * @param ctx the parse tree
	 */
	void enterFunctionTypeDef(XLangParser.FunctionTypeDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#functionTypeDef}.
	 * @param ctx the parse tree
	 */
	void exitFunctionTypeDef(XLangParser.FunctionTypeDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#propertyTypeDef}.
	 * @param ctx the parse tree
	 */
	void enterPropertyTypeDef(XLangParser.PropertyTypeDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#propertyTypeDef}.
	 * @param ctx the parse tree
	 */
	void exitPropertyTypeDef(XLangParser.PropertyTypeDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#namedTypeNode_annotation}.
	 * @param ctx the parse tree
	 */
	void enterNamedTypeNode_annotation(XLangParser.NamedTypeNode_annotationContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#namedTypeNode_annotation}.
	 * @param ctx the parse tree
	 */
	void exitNamedTypeNode_annotation(XLangParser.NamedTypeNode_annotationContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#structuredTypeDef_annotation}.
	 * @param ctx the parse tree
	 */
	void enterStructuredTypeDef_annotation(XLangParser.StructuredTypeDef_annotationContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#structuredTypeDef_annotation}.
	 * @param ctx the parse tree
	 */
	void exitStructuredTypeDef_annotation(XLangParser.StructuredTypeDef_annotationContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#functionParameterTypes_}.
	 * @param ctx the parse tree
	 */
	void enterFunctionParameterTypes_(XLangParser.FunctionParameterTypes_Context ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#functionParameterTypes_}.
	 * @param ctx the parse tree
	 */
	void exitFunctionParameterTypes_(XLangParser.FunctionParameterTypes_Context ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#functionArgTypeDef}.
	 * @param ctx the parse tree
	 */
	void enterFunctionArgTypeDef(XLangParser.FunctionArgTypeDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#functionArgTypeDef}.
	 * @param ctx the parse tree
	 */
	void exitFunctionArgTypeDef(XLangParser.FunctionArgTypeDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#typeAliasDeclaration}.
	 * @param ctx the parse tree
	 */
	void enterTypeAliasDeclaration(XLangParser.TypeAliasDeclarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#typeAliasDeclaration}.
	 * @param ctx the parse tree
	 */
	void exitTypeAliasDeclaration(XLangParser.TypeAliasDeclarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#enumDeclaration}.
	 * @param ctx the parse tree
	 */
	void enterEnumDeclaration(XLangParser.EnumDeclarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#enumDeclaration}.
	 * @param ctx the parse tree
	 */
	void exitEnumDeclaration(XLangParser.EnumDeclarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#enumMembers_}.
	 * @param ctx the parse tree
	 */
	void enterEnumMembers_(XLangParser.EnumMembers_Context ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#enumMembers_}.
	 * @param ctx the parse tree
	 */
	void exitEnumMembers_(XLangParser.EnumMembers_Context ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#enumMember}.
	 * @param ctx the parse tree
	 */
	void enterEnumMember(XLangParser.EnumMemberContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#enumMember}.
	 * @param ctx the parse tree
	 */
	void exitEnumMember(XLangParser.EnumMemberContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#decorators}.
	 * @param ctx the parse tree
	 */
	void enterDecorators(XLangParser.DecoratorsContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#decorators}.
	 * @param ctx the parse tree
	 */
	void exitDecorators(XLangParser.DecoratorsContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#decoratorElements_}.
	 * @param ctx the parse tree
	 */
	void enterDecoratorElements_(XLangParser.DecoratorElements_Context ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#decoratorElements_}.
	 * @param ctx the parse tree
	 */
	void exitDecoratorElements_(XLangParser.DecoratorElements_Context ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#decorator}.
	 * @param ctx the parse tree
	 */
	void enterDecorator(XLangParser.DecoratorContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#decorator}.
	 * @param ctx the parse tree
	 */
	void exitDecorator(XLangParser.DecoratorContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#metaObject}.
	 * @param ctx the parse tree
	 */
	void enterMetaObject(XLangParser.MetaObjectContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#metaObject}.
	 * @param ctx the parse tree
	 */
	void exitMetaObject(XLangParser.MetaObjectContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#metaObjectProperties_}.
	 * @param ctx the parse tree
	 */
	void enterMetaObjectProperties_(XLangParser.MetaObjectProperties_Context ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#metaObjectProperties_}.
	 * @param ctx the parse tree
	 */
	void exitMetaObjectProperties_(XLangParser.MetaObjectProperties_Context ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#metaProperty}.
	 * @param ctx the parse tree
	 */
	void enterMetaProperty(XLangParser.MetaPropertyContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#metaProperty}.
	 * @param ctx the parse tree
	 */
	void exitMetaProperty(XLangParser.MetaPropertyContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#metaArray}.
	 * @param ctx the parse tree
	 */
	void enterMetaArray(XLangParser.MetaArrayContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#metaArray}.
	 * @param ctx the parse tree
	 */
	void exitMetaArray(XLangParser.MetaArrayContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#metaArrayElements_}.
	 * @param ctx the parse tree
	 */
	void enterMetaArrayElements_(XLangParser.MetaArrayElements_Context ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#metaArrayElements_}.
	 * @param ctx the parse tree
	 */
	void exitMetaArrayElements_(XLangParser.MetaArrayElements_Context ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#ast_metaValue}.
	 * @param ctx the parse tree
	 */
	void enterAst_metaValue(XLangParser.Ast_metaValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#ast_metaValue}.
	 * @param ctx the parse tree
	 */
	void exitAst_metaValue(XLangParser.Ast_metaValueContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#qualifiedName}.
	 * @param ctx the parse tree
	 */
	void enterQualifiedName(XLangParser.QualifiedNameContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#qualifiedName}.
	 * @param ctx the parse tree
	 */
	void exitQualifiedName(XLangParser.QualifiedNameContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#qualifiedName_name_}.
	 * @param ctx the parse tree
	 */
	void enterQualifiedName_name_(XLangParser.QualifiedName_name_Context ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#qualifiedName_name_}.
	 * @param ctx the parse tree
	 */
	void exitQualifiedName_name_(XLangParser.QualifiedName_name_Context ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#qualifiedName_}.
	 * @param ctx the parse tree
	 */
	void enterQualifiedName_(XLangParser.QualifiedName_Context ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#qualifiedName_}.
	 * @param ctx the parse tree
	 */
	void exitQualifiedName_(XLangParser.QualifiedName_Context ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#propertyName_}.
	 * @param ctx the parse tree
	 */
	void enterPropertyName_(XLangParser.PropertyName_Context ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#propertyName_}.
	 * @param ctx the parse tree
	 */
	void exitPropertyName_(XLangParser.PropertyName_Context ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#expression_propName}.
	 * @param ctx the parse tree
	 */
	void enterExpression_propName(XLangParser.Expression_propNameContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#expression_propName}.
	 * @param ctx the parse tree
	 */
	void exitExpression_propName(XLangParser.Expression_propNameContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#identifier_ex}.
	 * @param ctx the parse tree
	 */
	void enterIdentifier_ex(XLangParser.Identifier_exContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#identifier_ex}.
	 * @param ctx the parse tree
	 */
	void exitIdentifier_ex(XLangParser.Identifier_exContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#identifier}.
	 * @param ctx the parse tree
	 */
	void enterIdentifier(XLangParser.IdentifierContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#identifier}.
	 * @param ctx the parse tree
	 */
	void exitIdentifier(XLangParser.IdentifierContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#identifierOrKeyword_}.
	 * @param ctx the parse tree
	 */
	void enterIdentifierOrKeyword_(XLangParser.IdentifierOrKeyword_Context ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#identifierOrKeyword_}.
	 * @param ctx the parse tree
	 */
	void exitIdentifierOrKeyword_(XLangParser.IdentifierOrKeyword_Context ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#reservedWord_}.
	 * @param ctx the parse tree
	 */
	void enterReservedWord_(XLangParser.ReservedWord_Context ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#reservedWord_}.
	 * @param ctx the parse tree
	 */
	void exitReservedWord_(XLangParser.ReservedWord_Context ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#keyword_}.
	 * @param ctx the parse tree
	 */
	void enterKeyword_(XLangParser.Keyword_Context ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#keyword_}.
	 * @param ctx the parse tree
	 */
	void exitKeyword_(XLangParser.Keyword_Context ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#literal}.
	 * @param ctx the parse tree
	 */
	void enterLiteral(XLangParser.LiteralContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#literal}.
	 * @param ctx the parse tree
	 */
	void exitLiteral(XLangParser.LiteralContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#literal_numeric}.
	 * @param ctx the parse tree
	 */
	void enterLiteral_numeric(XLangParser.Literal_numericContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#literal_numeric}.
	 * @param ctx the parse tree
	 */
	void exitLiteral_numeric(XLangParser.Literal_numericContext ctx);
	/**
	 * Enter a parse tree produced by {@link XLangParser#literal_string}.
	 * @param ctx the parse tree
	 */
	void enterLiteral_string(XLangParser.Literal_stringContext ctx);
	/**
	 * Exit a parse tree produced by {@link XLangParser#literal_string}.
	 * @param ctx the parse tree
	 */
	void exitLiteral_string(XLangParser.Literal_stringContext ctx);
}