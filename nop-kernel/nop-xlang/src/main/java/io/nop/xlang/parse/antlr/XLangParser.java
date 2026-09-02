// Nop Generated from XLangParser.g4 by ANTLR 4.13.0
package io.nop.xlang.parse.antlr;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*; //NOPMD - suppressed UnusedImports - Auto Gen Code
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator; //NOPMD - suppressed UnusedImports - Auto Gen Code
import java.util.ArrayList; //NOPMD - suppressed UnusedImports - Auto Gen Code

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue"})
// tell cpd to start ignoring code - CPD-OFF
public class XLangParser extends XLangParserBase {
	static { RuntimeMetaData.checkVersion("4.13.0", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		MultiLineComment=1, SingleLineComment=2, RegularExpressionLiteral=3, OpenBracket=4, 
		CloseBracket=5, OpenParen=6, CloseParen=7, OpenBrace=8, CloseBrace=9, 
		SemiColon=10, Comma=11, Assign=12, NullCoalesce=13, Question=14, OptionalDot=15, 
		Colon=16, ColonColon=17, Ellipsis=18, Dot=19, PlusPlus=20, MinusMinus=21, 
		Plus=22, Minus=23, BitNot=24, Not=25, Multiply=26, Divide=27, Modulus=28, 
		RightShiftArithmetic=29, LeftShiftArithmetic=30, RightShiftLogical=31, 
		LessThan=32, MoreThan=33, LessThanEquals=34, GreaterThanEquals=35, Equals_=36, 
		NotEquals=37, IdentityEquals=38, IdentityNotEquals=39, BitAnd=40, BitXOr=41, 
		BitOr=42, And=43, Or=44, MultiplyAssign=45, DivideAssign=46, ModulusAssign=47, 
		PlusAssign=48, MinusAssign=49, LeftShiftArithmeticAssign=50, RightShiftArithmeticAssign=51, 
		RightShiftLogicalAssign=52, BitAndAssign=53, BitXorAssign=54, BitOrAssign=55, 
		Arrow=56, NullLiteral=57, BooleanLiteral=58, AndLiteral=59, OrLiteral=60, 
		DecimalIntegerLiteral=61, HexIntegerLiteral=62, BinaryIntegerLiteral=63, 
		DecimalLiteral=64, Break=65, Do=66, Instanceof=67, Typeof=68, Case=69, 
		Else=70, New=71, Var=72, Catch=73, Finally=74, Return=75, Void=76, Continue=77, 
		For=78, Switch=79, While=80, Debugger=81, Function=82, This=83, With=84, 
		Default=85, If=86, Throw=87, Delete=88, In=89, Try=90, As=91, From=92, 
		ReadOnly=93, Async=94, Await=95, Class=96, Enum=97, Extends=98, Super=99, 
		Const=100, Export=101, Import=102, Implements=103, Let=104, Private=105, 
		Public=106, Interface=107, Package=108, Protected=109, Static=110, Any=111, 
		Number=112, Boolean=113, String=114, Symbol=115, TypeAlias=116, Constructor=117, 
		Abstract=118, At=119, StringLiteral=120, TemplateStringLiteral=121, Identifier=122, 
		WhiteSpaces=123, LineTerminator=124, UnexpectedCharacter=125, CpExprStart=126;
	public static final int
		RULE_program = 0, RULE_topLevelStatements_ = 1, RULE_ast_topLevelStatement = 2, 
		RULE_statement = 3, RULE_moduleDeclaration_import = 4, RULE_importDeclaration = 5, 
		RULE_importSpecifiers_ = 6, RULE_importSpecifier = 7, RULE_importAsDeclaration = 8, 
		RULE_ast_importSource = 9, RULE_ast_exportStatement = 10, RULE_exportNamedDeclaration = 11, 
		RULE_exportSpecifiers_ = 12, RULE_exportSpecifier = 13, RULE_blockStatement = 14, 
		RULE_statements_ = 15, RULE_caseStatements_ = 16, RULE_variableDeclaration_const = 17, 
		RULE_variableDeclaration = 18, RULE_varModifier_ = 19, RULE_variableDeclarators_ = 20, 
		RULE_variableDeclarator = 21, RULE_emptyStatement = 22, RULE_expressionStatement = 23, 
		RULE_ifStatement = 24, RULE_statement_iteration = 25, RULE_expression_iterationLeft = 26, 
		RULE_continueStatement = 27, RULE_breakStatement = 28, RULE_returnStatement = 29, 
		RULE_assignmentExpression = 30, RULE_expression_leftHandSide = 31, RULE_switchStatement = 32, 
		RULE_switchCases_ = 33, RULE_switchCase = 34, RULE_throwStatement = 35, 
		RULE_tryStatement = 36, RULE_catchClause = 37, RULE_blockStatement_finally = 38, 
		RULE_functionDeclaration = 39, RULE_parameterList_ = 40, RULE_parameterDeclaration = 41, 
		RULE_ast_identifierOrPattern = 42, RULE_expression_initializer = 43, RULE_arrayBinding = 44, 
		RULE_arrayElementBindings_ = 45, RULE_arrayElementBinding = 46, RULE_objectBinding = 47, 
		RULE_propertyBindings_ = 48, RULE_propertyBinding = 49, RULE_restBinding = 50, 
		RULE_arrayExpression = 51, RULE_elementList_ = 52, RULE_ast_arrayElement = 53, 
		RULE_spreadElement = 54, RULE_objectExpression = 55, RULE_objectProperties_ = 56, 
		RULE_ast_objectProperty = 57, RULE_propertyAssignment = 58, RULE_arguments_ = 59, 
		RULE_sequenceExpression = 60, RULE_singleExpressions_ = 61, RULE_assignmentExpression_init = 62, 
		RULE_expression_forInit = 63, RULE_initExpressions_ = 64, RULE_expression_single = 65, 
		RULE_templateStringLiteral = 66, RULE_arrowFunctionExpression = 67, RULE_parameterDeclaration_simple = 68, 
		RULE_expression_functionBody = 69, RULE_memberExpression = 70, RULE_assignmentOperator_ = 71, 
		RULE_eos__ = 72, RULE_typeParameters_ = 73, RULE_typeParameterNode = 74, 
		RULE_typeArguments_ = 75, RULE_structuredTypeDef = 76, RULE_typeNode_unionOrIntersection = 77, 
		RULE_intersectionTypeDef_ = 78, RULE_unionTypeDef_ = 79, RULE_tupleTypeDef = 80, 
		RULE_tupleTypeElements_ = 81, RULE_namedTypeNode = 82, RULE_typeNameNode_predefined = 83, 
		RULE_parameterizedTypeNode = 84, RULE_objectTypeDef = 85, RULE_objectTypeElements_ = 86, 
		RULE_functionTypeDef = 87, RULE_propertyTypeDef = 88, RULE_namedTypeNode_annotation = 89, 
		RULE_structuredTypeDef_annotation = 90, RULE_functionParameterTypes_ = 91, 
		RULE_functionArgTypeDef = 92, RULE_typeAliasDeclaration = 93, RULE_enumDeclaration = 94, 
		RULE_enumMembers_ = 95, RULE_enumMember = 96, RULE_decorators = 97, RULE_decoratorElements_ = 98, 
		RULE_decorator = 99, RULE_metaObject = 100, RULE_metaObjectProperties_ = 101, 
		RULE_metaProperty = 102, RULE_metaArray = 103, RULE_metaArrayElements_ = 104, 
		RULE_ast_metaValue = 105, RULE_qualifiedName = 106, RULE_qualifiedName_name_ = 107, 
		RULE_qualifiedName_ = 108, RULE_propertyName_ = 109, RULE_expression_propName = 110, 
		RULE_identifier_ex = 111, RULE_identifier = 112, RULE_identifierOrKeyword_ = 113, 
		RULE_reservedWord_ = 114, RULE_keyword_ = 115, RULE_literal = 116, RULE_literal_numeric = 117, 
		RULE_literal_string = 118;
	private static String[] makeRuleNames() {
		return new String[] {
			"program", "topLevelStatements_", "ast_topLevelStatement", "statement", 
			"moduleDeclaration_import", "importDeclaration", "importSpecifiers_", 
			"importSpecifier", "importAsDeclaration", "ast_importSource", "ast_exportStatement", 
			"exportNamedDeclaration", "exportSpecifiers_", "exportSpecifier", "blockStatement", 
			"statements_", "caseStatements_", "variableDeclaration_const", "variableDeclaration", 
			"varModifier_", "variableDeclarators_", "variableDeclarator", "emptyStatement", 
			"expressionStatement", "ifStatement", "statement_iteration", "expression_iterationLeft", 
			"continueStatement", "breakStatement", "returnStatement", "assignmentExpression", 
			"expression_leftHandSide", "switchStatement", "switchCases_", "switchCase", 
			"throwStatement", "tryStatement", "catchClause", "blockStatement_finally", 
			"functionDeclaration", "parameterList_", "parameterDeclaration", "ast_identifierOrPattern", 
			"expression_initializer", "arrayBinding", "arrayElementBindings_", "arrayElementBinding", 
			"objectBinding", "propertyBindings_", "propertyBinding", "restBinding", 
			"arrayExpression", "elementList_", "ast_arrayElement", "spreadElement", 
			"objectExpression", "objectProperties_", "ast_objectProperty", "propertyAssignment", 
			"arguments_", "sequenceExpression", "singleExpressions_", "assignmentExpression_init", 
			"expression_forInit", "initExpressions_", "expression_single", "templateStringLiteral", 
			"arrowFunctionExpression", "parameterDeclaration_simple", "expression_functionBody", 
			"memberExpression", "assignmentOperator_", "eos__", "typeParameters_", 
			"typeParameterNode", "typeArguments_", "structuredTypeDef", "typeNode_unionOrIntersection", 
			"intersectionTypeDef_", "unionTypeDef_", "tupleTypeDef", "tupleTypeElements_", 
			"namedTypeNode", "typeNameNode_predefined", "parameterizedTypeNode", 
			"objectTypeDef", "objectTypeElements_", "functionTypeDef", "propertyTypeDef", 
			"namedTypeNode_annotation", "structuredTypeDef_annotation", "functionParameterTypes_", 
			"functionArgTypeDef", "typeAliasDeclaration", "enumDeclaration", "enumMembers_", 
			"enumMember", "decorators", "decoratorElements_", "decorator", "metaObject", 
			"metaObjectProperties_", "metaProperty", "metaArray", "metaArrayElements_", 
			"ast_metaValue", "qualifiedName", "qualifiedName_name_", "qualifiedName_", 
			"propertyName_", "expression_propName", "identifier_ex", "identifier", 
			"identifierOrKeyword_", "reservedWord_", "keyword_", "literal", "literal_numeric", 
			"literal_string"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, null, null, null, "'['", "']'", "'('", "')'", "'{'", "'}'", "';'", 
			"','", "'='", "'??'", "'?'", "'?.'", "':'", "'::'", "'...'", "'.'", "'++'", 
			"'--'", "'+'", "'-'", "'~'", "'!'", "'*'", "'/'", "'%'", "'>>'", "'<<'", 
			"'>>>'", "'<'", "'>'", "'<='", "'>='", "'=='", "'!='", "'==='", "'!=='", 
			"'&'", "'^'", "'|'", "'&&'", "'||'", "'*='", "'/='", "'%='", "'+='", 
			"'-='", "'<<='", "'>>='", "'>>>='", "'&='", "'^='", "'|='", "'=>'", "'null'", 
			null, "'and'", "'or'", null, null, null, null, "'break'", "'do'", "'instanceof'", 
			"'typeof'", "'case'", "'else'", "'new'", "'var'", "'catch'", "'finally'", 
			"'return'", "'void'", "'continue'", "'for'", "'switch'", "'while'", "'debugger'", 
			"'function'", "'this'", "'with'", "'default'", "'if'", "'throw'", "'delete'", 
			"'in'", "'try'", "'as'", "'from'", "'readonly'", "'async'", "'await'", 
			"'class'", "'enum'", "'extends'", "'super'", "'const'", "'export'", "'import'", 
			"'implements'", "'let'", "'private'", "'public'", "'interface'", "'package'", 
			"'protected'", "'static'", "'any'", "'number'", "'boolean'", "'string'", 
			"'symbol'", "'type'", "'constructor'", "'abstract'", "'@'", null, null, 
			null, null, null, null, "'#{'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "MultiLineComment", "SingleLineComment", "RegularExpressionLiteral", 
			"OpenBracket", "CloseBracket", "OpenParen", "CloseParen", "OpenBrace", 
			"CloseBrace", "SemiColon", "Comma", "Assign", "NullCoalesce", "Question", 
			"OptionalDot", "Colon", "ColonColon", "Ellipsis", "Dot", "PlusPlus", 
			"MinusMinus", "Plus", "Minus", "BitNot", "Not", "Multiply", "Divide", 
			"Modulus", "RightShiftArithmetic", "LeftShiftArithmetic", "RightShiftLogical", 
			"LessThan", "MoreThan", "LessThanEquals", "GreaterThanEquals", "Equals_", 
			"NotEquals", "IdentityEquals", "IdentityNotEquals", "BitAnd", "BitXOr", 
			"BitOr", "And", "Or", "MultiplyAssign", "DivideAssign", "ModulusAssign", 
			"PlusAssign", "MinusAssign", "LeftShiftArithmeticAssign", "RightShiftArithmeticAssign", 
			"RightShiftLogicalAssign", "BitAndAssign", "BitXorAssign", "BitOrAssign", 
			"Arrow", "NullLiteral", "BooleanLiteral", "AndLiteral", "OrLiteral", 
			"DecimalIntegerLiteral", "HexIntegerLiteral", "BinaryIntegerLiteral", 
			"DecimalLiteral", "Break", "Do", "Instanceof", "Typeof", "Case", "Else", 
			"New", "Var", "Catch", "Finally", "Return", "Void", "Continue", "For", 
			"Switch", "While", "Debugger", "Function", "This", "With", "Default", 
			"If", "Throw", "Delete", "In", "Try", "As", "From", "ReadOnly", "Async", 
			"Await", "Class", "Enum", "Extends", "Super", "Const", "Export", "Import", 
			"Implements", "Let", "Private", "Public", "Interface", "Package", "Protected", 
			"Static", "Any", "Number", "Boolean", "String", "Symbol", "TypeAlias", 
			"Constructor", "Abstract", "At", "StringLiteral", "TemplateStringLiteral", 
			"Identifier", "WhiteSpaces", "LineTerminator", "UnexpectedCharacter", 
			"CpExprStart"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}

	@Override
	public String getGrammarFileName() { return "XLangParser.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public XLangParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ProgramContext extends ParserRuleContext {
		public TopLevelStatements_Context body;
		public TerminalNode EOF() { return getToken(XLangParser.EOF, 0); }
		public TopLevelStatements_Context topLevelStatements_() {
			return getRuleContext(TopLevelStatements_Context.class,0);
		}
		public ProgramContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_program; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterProgram(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitProgram(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitProgram(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ProgramContext program() throws RecognitionException {
		ProgramContext _localctx = new ProgramContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_program);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(238);
			((ProgramContext)_localctx).body = topLevelStatements_();
			setState(239);
			match(EOF);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class TopLevelStatements_Context extends ParserRuleContext {
		public Ast_topLevelStatementContext e;
		public List<Ast_topLevelStatementContext> ast_topLevelStatement() {
			return getRuleContexts(Ast_topLevelStatementContext.class);
		}
		public Ast_topLevelStatementContext ast_topLevelStatement(int i) {
			return getRuleContext(Ast_topLevelStatementContext.class,i);
		}
		public TopLevelStatements_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_topLevelStatements_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterTopLevelStatements_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitTopLevelStatements_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitTopLevelStatements_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TopLevelStatements_Context topLevelStatements_() throws RecognitionException {
		TopLevelStatements_Context _localctx = new TopLevelStatements_Context(_ctx, getState());
		enterRule(_localctx, 2, RULE_topLevelStatements_);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(244);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,0,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(241);
					((TopLevelStatements_Context)_localctx).e = ast_topLevelStatement();
					}
					} 
				}
				setState(246);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,0,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Ast_topLevelStatementContext extends ParserRuleContext {
		public Ast_topLevelStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_ast_topLevelStatement; }
	 
		public Ast_topLevelStatementContext() { }
		public void copyFrom(Ast_topLevelStatementContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ExportDeclaration_constContext extends Ast_topLevelStatementContext {
		public VariableDeclaration_constContext declaration;
		public TerminalNode Export() { return getToken(XLangParser.Export, 0); }
		public VariableDeclaration_constContext variableDeclaration_const() {
			return getRuleContext(VariableDeclaration_constContext.class,0);
		}
		public ExportDeclaration_constContext(Ast_topLevelStatementContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterExportDeclaration_const(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitExportDeclaration_const(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitExportDeclaration_const(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class Statement_topContext extends Ast_topLevelStatementContext {
		public StatementContext statement() {
			return getRuleContext(StatementContext.class,0);
		}
		public Statement_topContext(Ast_topLevelStatementContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterStatement_top(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitStatement_top(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitStatement_top(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ExportDeclaration_typeContext extends Ast_topLevelStatementContext {
		public TypeAliasDeclarationContext declaration;
		public TerminalNode Export() { return getToken(XLangParser.Export, 0); }
		public TypeAliasDeclarationContext typeAliasDeclaration() {
			return getRuleContext(TypeAliasDeclarationContext.class,0);
		}
		public ExportDeclaration_typeContext(Ast_topLevelStatementContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterExportDeclaration_type(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitExportDeclaration_type(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitExportDeclaration_type(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class Ast_exportStatement2Context extends Ast_topLevelStatementContext {
		public Ast_exportStatementContext ast_exportStatement() {
			return getRuleContext(Ast_exportStatementContext.class,0);
		}
		public Ast_exportStatement2Context(Ast_topLevelStatementContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterAst_exportStatement2(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitAst_exportStatement2(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitAst_exportStatement2(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ExportDeclaration_funcContext extends Ast_topLevelStatementContext {
		public FunctionDeclarationContext declaration;
		public TerminalNode Export() { return getToken(XLangParser.Export, 0); }
		public FunctionDeclarationContext functionDeclaration() {
			return getRuleContext(FunctionDeclarationContext.class,0);
		}
		public ExportDeclaration_funcContext(Ast_topLevelStatementContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterExportDeclaration_func(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitExportDeclaration_func(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitExportDeclaration_func(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ModuleDeclaration_import2Context extends Ast_topLevelStatementContext {
		public ModuleDeclaration_importContext moduleDeclaration_import() {
			return getRuleContext(ModuleDeclaration_importContext.class,0);
		}
		public ModuleDeclaration_import2Context(Ast_topLevelStatementContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterModuleDeclaration_import2(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitModuleDeclaration_import2(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitModuleDeclaration_import2(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Ast_topLevelStatementContext ast_topLevelStatement() throws RecognitionException {
		Ast_topLevelStatementContext _localctx = new Ast_topLevelStatementContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_ast_topLevelStatement);
		try {
			setState(256);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,1,_ctx) ) {
			case 1:
				_localctx = new ModuleDeclaration_import2Context(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(247);
				moduleDeclaration_import();
				}
				break;
			case 2:
				_localctx = new Ast_exportStatement2Context(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(248);
				ast_exportStatement();
				}
				break;
			case 3:
				_localctx = new ExportDeclaration_funcContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(249);
				match(Export);
				setState(250);
				((ExportDeclaration_funcContext)_localctx).declaration = functionDeclaration();
				}
				break;
			case 4:
				_localctx = new ExportDeclaration_typeContext(_localctx);
				enterOuterAlt(_localctx, 4);
				{
				setState(251);
				match(Export);
				setState(252);
				((ExportDeclaration_typeContext)_localctx).declaration = typeAliasDeclaration();
				}
				break;
			case 5:
				_localctx = new ExportDeclaration_constContext(_localctx);
				enterOuterAlt(_localctx, 5);
				{
				setState(253);
				match(Export);
				setState(254);
				((ExportDeclaration_constContext)_localctx).declaration = variableDeclaration_const();
				}
				break;
			case 6:
				_localctx = new Statement_topContext(_localctx);
				enterOuterAlt(_localctx, 6);
				{
				setState(255);
				statement();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class StatementContext extends ParserRuleContext {
		public BlockStatementContext blockStatement() {
			return getRuleContext(BlockStatementContext.class,0);
		}
		public VariableDeclarationContext variableDeclaration() {
			return getRuleContext(VariableDeclarationContext.class,0);
		}
		public EmptyStatementContext emptyStatement() {
			return getRuleContext(EmptyStatementContext.class,0);
		}
		public IfStatementContext ifStatement() {
			return getRuleContext(IfStatementContext.class,0);
		}
		public Statement_iterationContext statement_iteration() {
			return getRuleContext(Statement_iterationContext.class,0);
		}
		public ContinueStatementContext continueStatement() {
			return getRuleContext(ContinueStatementContext.class,0);
		}
		public BreakStatementContext breakStatement() {
			return getRuleContext(BreakStatementContext.class,0);
		}
		public ReturnStatementContext returnStatement() {
			return getRuleContext(ReturnStatementContext.class,0);
		}
		public SwitchStatementContext switchStatement() {
			return getRuleContext(SwitchStatementContext.class,0);
		}
		public ThrowStatementContext throwStatement() {
			return getRuleContext(ThrowStatementContext.class,0);
		}
		public TryStatementContext tryStatement() {
			return getRuleContext(TryStatementContext.class,0);
		}
		public FunctionDeclarationContext functionDeclaration() {
			return getRuleContext(FunctionDeclarationContext.class,0);
		}
		public TypeAliasDeclarationContext typeAliasDeclaration() {
			return getRuleContext(TypeAliasDeclarationContext.class,0);
		}
		public EnumDeclarationContext enumDeclaration() {
			return getRuleContext(EnumDeclarationContext.class,0);
		}
		public ExpressionStatementContext expressionStatement() {
			return getRuleContext(ExpressionStatementContext.class,0);
		}
		public AssignmentExpressionContext assignmentExpression() {
			return getRuleContext(AssignmentExpressionContext.class,0);
		}
		public StatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final StatementContext statement() throws RecognitionException {
		StatementContext _localctx = new StatementContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_statement);
		try {
			setState(274);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,2,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(258);
				blockStatement();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(259);
				variableDeclaration();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(260);
				emptyStatement();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(261);
				ifStatement();
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(262);
				statement_iteration();
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(263);
				continueStatement();
				}
				break;
			case 7:
				enterOuterAlt(_localctx, 7);
				{
				setState(264);
				breakStatement();
				}
				break;
			case 8:
				enterOuterAlt(_localctx, 8);
				{
				setState(265);
				returnStatement();
				}
				break;
			case 9:
				enterOuterAlt(_localctx, 9);
				{
				setState(266);
				switchStatement();
				}
				break;
			case 10:
				enterOuterAlt(_localctx, 10);
				{
				setState(267);
				throwStatement();
				}
				break;
			case 11:
				enterOuterAlt(_localctx, 11);
				{
				setState(268);
				tryStatement();
				}
				break;
			case 12:
				enterOuterAlt(_localctx, 12);
				{
				setState(269);
				functionDeclaration();
				}
				break;
			case 13:
				enterOuterAlt(_localctx, 13);
				{
				setState(270);
				typeAliasDeclaration();
				}
				break;
			case 14:
				enterOuterAlt(_localctx, 14);
				{
				setState(271);
				enumDeclaration();
				}
				break;
			case 15:
				enterOuterAlt(_localctx, 15);
				{
				setState(272);
				expressionStatement();
				}
				break;
			case 16:
				enterOuterAlt(_localctx, 16);
				{
				setState(273);
				assignmentExpression();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ModuleDeclaration_importContext extends ParserRuleContext {
		public ImportDeclarationContext importDeclaration() {
			return getRuleContext(ImportDeclarationContext.class,0);
		}
		public ImportAsDeclarationContext importAsDeclaration() {
			return getRuleContext(ImportAsDeclarationContext.class,0);
		}
		public ModuleDeclaration_importContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_moduleDeclaration_import; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterModuleDeclaration_import(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitModuleDeclaration_import(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitModuleDeclaration_import(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ModuleDeclaration_importContext moduleDeclaration_import() throws RecognitionException {
		ModuleDeclaration_importContext _localctx = new ModuleDeclaration_importContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_moduleDeclaration_import);
		try {
			setState(278);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,3,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(276);
				importDeclaration();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(277);
				importAsDeclaration();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ImportDeclarationContext extends ParserRuleContext {
		public ImportSpecifiers_Context specifiers;
		public Literal_stringContext source;
		public TerminalNode Import() { return getToken(XLangParser.Import, 0); }
		public TerminalNode OpenBrace() { return getToken(XLangParser.OpenBrace, 0); }
		public TerminalNode CloseBrace() { return getToken(XLangParser.CloseBrace, 0); }
		public TerminalNode From() { return getToken(XLangParser.From, 0); }
		public Eos__Context eos__() {
			return getRuleContext(Eos__Context.class,0);
		}
		public ImportSpecifiers_Context importSpecifiers_() {
			return getRuleContext(ImportSpecifiers_Context.class,0);
		}
		public Literal_stringContext literal_string() {
			return getRuleContext(Literal_stringContext.class,0);
		}
		public ImportDeclarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_importDeclaration; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterImportDeclaration(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitImportDeclaration(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitImportDeclaration(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ImportDeclarationContext importDeclaration() throws RecognitionException {
		ImportDeclarationContext _localctx = new ImportDeclarationContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_importDeclaration);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(280);
			match(Import);
			setState(281);
			match(OpenBrace);
			setState(282);
			((ImportDeclarationContext)_localctx).specifiers = importSpecifiers_();
			setState(283);
			match(CloseBrace);
			setState(284);
			match(From);
			setState(285);
			((ImportDeclarationContext)_localctx).source = literal_string();
			setState(286);
			eos__();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ImportSpecifiers_Context extends ParserRuleContext {
		public ImportSpecifierContext e;
		public List<ImportSpecifierContext> importSpecifier() {
			return getRuleContexts(ImportSpecifierContext.class);
		}
		public ImportSpecifierContext importSpecifier(int i) {
			return getRuleContext(ImportSpecifierContext.class,i);
		}
		public List<TerminalNode> Comma() { return getTokens(XLangParser.Comma); }
		public TerminalNode Comma(int i) {
			return getToken(XLangParser.Comma, i);
		}
		public ImportSpecifiers_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_importSpecifiers_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterImportSpecifiers_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitImportSpecifiers_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitImportSpecifiers_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ImportSpecifiers_Context importSpecifiers_() throws RecognitionException {
		ImportSpecifiers_Context _localctx = new ImportSpecifiers_Context(_ctx, getState());
		enterRule(_localctx, 12, RULE_importSpecifiers_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(288);
			((ImportSpecifiers_Context)_localctx).e = importSpecifier();
			setState(293);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==Comma) {
				{
				{
				setState(289);
				match(Comma);
				setState(290);
				((ImportSpecifiers_Context)_localctx).e = importSpecifier();
				}
				}
				setState(295);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ImportSpecifierContext extends ParserRuleContext {
		public IdentifierContext imported;
		public IdentifierContext local;
		public List<IdentifierContext> identifier() {
			return getRuleContexts(IdentifierContext.class);
		}
		public IdentifierContext identifier(int i) {
			return getRuleContext(IdentifierContext.class,i);
		}
		public TerminalNode As() { return getToken(XLangParser.As, 0); }
		public ImportSpecifierContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_importSpecifier; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterImportSpecifier(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitImportSpecifier(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitImportSpecifier(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ImportSpecifierContext importSpecifier() throws RecognitionException {
		ImportSpecifierContext _localctx = new ImportSpecifierContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_importSpecifier);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(296);
			((ImportSpecifierContext)_localctx).imported = identifier();
			setState(299);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==As) {
				{
				setState(297);
				match(As);
				setState(298);
				((ImportSpecifierContext)_localctx).local = identifier();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ImportAsDeclarationContext extends ParserRuleContext {
		public Ast_importSourceContext source;
		public IdentifierContext local;
		public TerminalNode Import() { return getToken(XLangParser.Import, 0); }
		public Eos__Context eos__() {
			return getRuleContext(Eos__Context.class,0);
		}
		public Ast_importSourceContext ast_importSource() {
			return getRuleContext(Ast_importSourceContext.class,0);
		}
		public TerminalNode As() { return getToken(XLangParser.As, 0); }
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public ImportAsDeclarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_importAsDeclaration; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterImportAsDeclaration(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitImportAsDeclaration(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitImportAsDeclaration(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ImportAsDeclarationContext importAsDeclaration() throws RecognitionException {
		ImportAsDeclarationContext _localctx = new ImportAsDeclarationContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_importAsDeclaration);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(301);
			match(Import);
			setState(302);
			((ImportAsDeclarationContext)_localctx).source = ast_importSource();
			setState(305);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,6,_ctx) ) {
			case 1:
				{
				setState(303);
				match(As);
				setState(304);
				((ImportAsDeclarationContext)_localctx).local = identifier();
				}
				break;
			}
			setState(307);
			eos__();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Ast_importSourceContext extends ParserRuleContext {
		public QualifiedNameContext qualifiedName() {
			return getRuleContext(QualifiedNameContext.class,0);
		}
		public Literal_stringContext literal_string() {
			return getRuleContext(Literal_stringContext.class,0);
		}
		public Ast_importSourceContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_ast_importSource; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterAst_importSource(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitAst_importSource(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitAst_importSource(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Ast_importSourceContext ast_importSource() throws RecognitionException {
		Ast_importSourceContext _localctx = new Ast_importSourceContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_ast_importSource);
		try {
			setState(311);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case From:
			case TypeAlias:
			case Identifier:
				enterOuterAlt(_localctx, 1);
				{
				setState(309);
				qualifiedName();
				}
				break;
			case StringLiteral:
				enterOuterAlt(_localctx, 2);
				{
				setState(310);
				literal_string();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Ast_exportStatementContext extends ParserRuleContext {
		public ExportNamedDeclarationContext exportNamedDeclaration() {
			return getRuleContext(ExportNamedDeclarationContext.class,0);
		}
		public Ast_exportStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_ast_exportStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterAst_exportStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitAst_exportStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitAst_exportStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Ast_exportStatementContext ast_exportStatement() throws RecognitionException {
		Ast_exportStatementContext _localctx = new Ast_exportStatementContext(_ctx, getState());
		enterRule(_localctx, 20, RULE_ast_exportStatement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(313);
			exportNamedDeclaration();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ExportNamedDeclarationContext extends ParserRuleContext {
		public ExportSpecifiers_Context specifiers;
		public Literal_stringContext source;
		public TerminalNode Export() { return getToken(XLangParser.Export, 0); }
		public TerminalNode OpenBrace() { return getToken(XLangParser.OpenBrace, 0); }
		public TerminalNode CloseBrace() { return getToken(XLangParser.CloseBrace, 0); }
		public TerminalNode From() { return getToken(XLangParser.From, 0); }
		public Eos__Context eos__() {
			return getRuleContext(Eos__Context.class,0);
		}
		public ExportSpecifiers_Context exportSpecifiers_() {
			return getRuleContext(ExportSpecifiers_Context.class,0);
		}
		public Literal_stringContext literal_string() {
			return getRuleContext(Literal_stringContext.class,0);
		}
		public ExportNamedDeclarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_exportNamedDeclaration; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterExportNamedDeclaration(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitExportNamedDeclaration(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitExportNamedDeclaration(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ExportNamedDeclarationContext exportNamedDeclaration() throws RecognitionException {
		ExportNamedDeclarationContext _localctx = new ExportNamedDeclarationContext(_ctx, getState());
		enterRule(_localctx, 22, RULE_exportNamedDeclaration);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(315);
			match(Export);
			setState(316);
			match(OpenBrace);
			setState(317);
			((ExportNamedDeclarationContext)_localctx).specifiers = exportSpecifiers_();
			setState(318);
			match(CloseBrace);
			setState(319);
			match(From);
			setState(320);
			((ExportNamedDeclarationContext)_localctx).source = literal_string();
			setState(321);
			eos__();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ExportSpecifiers_Context extends ParserRuleContext {
		public ExportSpecifierContext e;
		public List<ExportSpecifierContext> exportSpecifier() {
			return getRuleContexts(ExportSpecifierContext.class);
		}
		public ExportSpecifierContext exportSpecifier(int i) {
			return getRuleContext(ExportSpecifierContext.class,i);
		}
		public List<TerminalNode> Comma() { return getTokens(XLangParser.Comma); }
		public TerminalNode Comma(int i) {
			return getToken(XLangParser.Comma, i);
		}
		public ExportSpecifiers_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_exportSpecifiers_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterExportSpecifiers_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitExportSpecifiers_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitExportSpecifiers_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ExportSpecifiers_Context exportSpecifiers_() throws RecognitionException {
		ExportSpecifiers_Context _localctx = new ExportSpecifiers_Context(_ctx, getState());
		enterRule(_localctx, 24, RULE_exportSpecifiers_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(323);
			((ExportSpecifiers_Context)_localctx).e = exportSpecifier();
			setState(328);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==Comma) {
				{
				{
				setState(324);
				match(Comma);
				setState(325);
				((ExportSpecifiers_Context)_localctx).e = exportSpecifier();
				}
				}
				setState(330);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ExportSpecifierContext extends ParserRuleContext {
		public IdentifierContext local;
		public IdentifierContext exported;
		public List<IdentifierContext> identifier() {
			return getRuleContexts(IdentifierContext.class);
		}
		public IdentifierContext identifier(int i) {
			return getRuleContext(IdentifierContext.class,i);
		}
		public TerminalNode As() { return getToken(XLangParser.As, 0); }
		public ExportSpecifierContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_exportSpecifier; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterExportSpecifier(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitExportSpecifier(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitExportSpecifier(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ExportSpecifierContext exportSpecifier() throws RecognitionException {
		ExportSpecifierContext _localctx = new ExportSpecifierContext(_ctx, getState());
		enterRule(_localctx, 26, RULE_exportSpecifier);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(331);
			((ExportSpecifierContext)_localctx).local = identifier();
			setState(334);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==As) {
				{
				setState(332);
				match(As);
				setState(333);
				((ExportSpecifierContext)_localctx).exported = identifier();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class BlockStatementContext extends ParserRuleContext {
		public Statements_Context body;
		public TerminalNode OpenBrace() { return getToken(XLangParser.OpenBrace, 0); }
		public TerminalNode CloseBrace() { return getToken(XLangParser.CloseBrace, 0); }
		public Statements_Context statements_() {
			return getRuleContext(Statements_Context.class,0);
		}
		public BlockStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_blockStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterBlockStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitBlockStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitBlockStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final BlockStatementContext blockStatement() throws RecognitionException {
		BlockStatementContext _localctx = new BlockStatementContext(_ctx, getState());
		enterRule(_localctx, 28, RULE_blockStatement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(336);
			match(OpenBrace);
			setState(337);
			((BlockStatementContext)_localctx).body = statements_();
			setState(338);
			match(CloseBrace);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Statements_Context extends ParserRuleContext {
		public StatementContext e;
		public List<StatementContext> statement() {
			return getRuleContexts(StatementContext.class);
		}
		public StatementContext statement(int i) {
			return getRuleContext(StatementContext.class,i);
		}
		public Statements_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_statements_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterStatements_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitStatements_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitStatements_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Statements_Context statements_() throws RecognitionException {
		Statements_Context _localctx = new Statements_Context(_ctx, getState());
		enterRule(_localctx, 30, RULE_statements_);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(343);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,10,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(340);
					((Statements_Context)_localctx).e = statement();
					}
					} 
				}
				setState(345);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,10,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class CaseStatements_Context extends ParserRuleContext {
		public StatementContext e;
		public List<StatementContext> statement() {
			return getRuleContexts(StatementContext.class);
		}
		public StatementContext statement(int i) {
			return getRuleContext(StatementContext.class,i);
		}
		public CaseStatements_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_caseStatements_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterCaseStatements_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitCaseStatements_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitCaseStatements_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final CaseStatements_Context caseStatements_() throws RecognitionException {
		CaseStatements_Context _localctx = new CaseStatements_Context(_ctx, getState());
		enterRule(_localctx, 32, RULE_caseStatements_);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(349);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,11,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(346);
					((CaseStatements_Context)_localctx).e = statement();
					}
					} 
				}
				setState(351);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,11,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class VariableDeclaration_constContext extends ParserRuleContext {
		public Token kind;
		public VariableDeclarators_Context declarators;
		public Eos__Context eos__() {
			return getRuleContext(Eos__Context.class,0);
		}
		public TerminalNode Const() { return getToken(XLangParser.Const, 0); }
		public VariableDeclarators_Context variableDeclarators_() {
			return getRuleContext(VariableDeclarators_Context.class,0);
		}
		public VariableDeclaration_constContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_variableDeclaration_const; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterVariableDeclaration_const(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitVariableDeclaration_const(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitVariableDeclaration_const(this);
			else return visitor.visitChildren(this);
		}
	}

	public final VariableDeclaration_constContext variableDeclaration_const() throws RecognitionException {
		VariableDeclaration_constContext _localctx = new VariableDeclaration_constContext(_ctx, getState());
		enterRule(_localctx, 34, RULE_variableDeclaration_const);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(352);
			((VariableDeclaration_constContext)_localctx).kind = match(Const);
			setState(353);
			((VariableDeclaration_constContext)_localctx).declarators = variableDeclarators_();
			setState(354);
			eos__();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class VariableDeclarationContext extends ParserRuleContext {
		public VarModifier_Context kind;
		public VariableDeclarators_Context declarators;
		public Eos__Context eos__() {
			return getRuleContext(Eos__Context.class,0);
		}
		public VarModifier_Context varModifier_() {
			return getRuleContext(VarModifier_Context.class,0);
		}
		public VariableDeclarators_Context variableDeclarators_() {
			return getRuleContext(VariableDeclarators_Context.class,0);
		}
		public VariableDeclarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_variableDeclaration; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterVariableDeclaration(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitVariableDeclaration(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitVariableDeclaration(this);
			else return visitor.visitChildren(this);
		}
	}

	public final VariableDeclarationContext variableDeclaration() throws RecognitionException {
		VariableDeclarationContext _localctx = new VariableDeclarationContext(_ctx, getState());
		enterRule(_localctx, 36, RULE_variableDeclaration);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(356);
			((VariableDeclarationContext)_localctx).kind = varModifier_();
			setState(357);
			((VariableDeclarationContext)_localctx).declarators = variableDeclarators_();
			setState(358);
			eos__();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class VarModifier_Context extends ParserRuleContext {
		public TerminalNode Let() { return getToken(XLangParser.Let, 0); }
		public TerminalNode Const() { return getToken(XLangParser.Const, 0); }
		public VarModifier_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_varModifier_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterVarModifier_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitVarModifier_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitVarModifier_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final VarModifier_Context varModifier_() throws RecognitionException {
		VarModifier_Context _localctx = new VarModifier_Context(_ctx, getState());
		enterRule(_localctx, 38, RULE_varModifier_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(360);
			_la = _input.LA(1);
			if ( !(_la==Const || _la==Let) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class VariableDeclarators_Context extends ParserRuleContext {
		public VariableDeclaratorContext e;
		public List<VariableDeclaratorContext> variableDeclarator() {
			return getRuleContexts(VariableDeclaratorContext.class);
		}
		public VariableDeclaratorContext variableDeclarator(int i) {
			return getRuleContext(VariableDeclaratorContext.class,i);
		}
		public List<TerminalNode> Comma() { return getTokens(XLangParser.Comma); }
		public TerminalNode Comma(int i) {
			return getToken(XLangParser.Comma, i);
		}
		public VariableDeclarators_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_variableDeclarators_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterVariableDeclarators_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitVariableDeclarators_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitVariableDeclarators_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final VariableDeclarators_Context variableDeclarators_() throws RecognitionException {
		VariableDeclarators_Context _localctx = new VariableDeclarators_Context(_ctx, getState());
		enterRule(_localctx, 40, RULE_variableDeclarators_);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(362);
			((VariableDeclarators_Context)_localctx).e = variableDeclarator();
			setState(367);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,12,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(363);
					match(Comma);
					setState(364);
					((VariableDeclarators_Context)_localctx).e = variableDeclarator();
					}
					} 
				}
				setState(369);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,12,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class VariableDeclaratorContext extends ParserRuleContext {
		public Ast_identifierOrPatternContext id;
		public NamedTypeNode_annotationContext varType;
		public Expression_initializerContext init;
		public Ast_identifierOrPatternContext ast_identifierOrPattern() {
			return getRuleContext(Ast_identifierOrPatternContext.class,0);
		}
		public NamedTypeNode_annotationContext namedTypeNode_annotation() {
			return getRuleContext(NamedTypeNode_annotationContext.class,0);
		}
		public Expression_initializerContext expression_initializer() {
			return getRuleContext(Expression_initializerContext.class,0);
		}
		public VariableDeclaratorContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_variableDeclarator; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterVariableDeclarator(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitVariableDeclarator(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitVariableDeclarator(this);
			else return visitor.visitChildren(this);
		}
	}

	public final VariableDeclaratorContext variableDeclarator() throws RecognitionException {
		VariableDeclaratorContext _localctx = new VariableDeclaratorContext(_ctx, getState());
		enterRule(_localctx, 42, RULE_variableDeclarator);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(370);
			((VariableDeclaratorContext)_localctx).id = ast_identifierOrPattern();
			setState(372);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,13,_ctx) ) {
			case 1:
				{
				setState(371);
				((VariableDeclaratorContext)_localctx).varType = namedTypeNode_annotation();
				}
				break;
			}
			setState(375);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,14,_ctx) ) {
			case 1:
				{
				setState(374);
				((VariableDeclaratorContext)_localctx).init = expression_initializer();
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class EmptyStatementContext extends ParserRuleContext {
		public TerminalNode SemiColon() { return getToken(XLangParser.SemiColon, 0); }
		public EmptyStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_emptyStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterEmptyStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitEmptyStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitEmptyStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final EmptyStatementContext emptyStatement() throws RecognitionException {
		EmptyStatementContext _localctx = new EmptyStatementContext(_ctx, getState());
		enterRule(_localctx, 44, RULE_emptyStatement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(377);
			match(SemiColon);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ExpressionStatementContext extends ParserRuleContext {
		public Expression_singleContext expression;
		public Eos__Context eos__() {
			return getRuleContext(Eos__Context.class,0);
		}
		public Expression_singleContext expression_single() {
			return getRuleContext(Expression_singleContext.class,0);
		}
		public ExpressionStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expressionStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterExpressionStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitExpressionStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitExpressionStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ExpressionStatementContext expressionStatement() throws RecognitionException {
		ExpressionStatementContext _localctx = new ExpressionStatementContext(_ctx, getState());
		enterRule(_localctx, 46, RULE_expressionStatement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(379);
			if (!(this.notOpenBraceAndNotFunction())) throw new FailedPredicateException(this, "this.notOpenBraceAndNotFunction()");
			setState(380);
			((ExpressionStatementContext)_localctx).expression = expression_single(0);
			setState(381);
			eos__();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class IfStatementContext extends ParserRuleContext {
		public Expression_singleContext test;
		public StatementContext consequent;
		public StatementContext alternate;
		public TerminalNode If() { return getToken(XLangParser.If, 0); }
		public TerminalNode OpenParen() { return getToken(XLangParser.OpenParen, 0); }
		public TerminalNode CloseParen() { return getToken(XLangParser.CloseParen, 0); }
		public Expression_singleContext expression_single() {
			return getRuleContext(Expression_singleContext.class,0);
		}
		public List<StatementContext> statement() {
			return getRuleContexts(StatementContext.class);
		}
		public StatementContext statement(int i) {
			return getRuleContext(StatementContext.class,i);
		}
		public TerminalNode Else() { return getToken(XLangParser.Else, 0); }
		public IfStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_ifStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterIfStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitIfStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitIfStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final IfStatementContext ifStatement() throws RecognitionException {
		IfStatementContext _localctx = new IfStatementContext(_ctx, getState());
		enterRule(_localctx, 48, RULE_ifStatement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(383);
			match(If);
			setState(384);
			match(OpenParen);
			setState(385);
			((IfStatementContext)_localctx).test = expression_single(0);
			setState(386);
			match(CloseParen);
			setState(387);
			((IfStatementContext)_localctx).consequent = statement();
			setState(390);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,15,_ctx) ) {
			case 1:
				{
				setState(388);
				match(Else);
				setState(389);
				((IfStatementContext)_localctx).alternate = statement();
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Statement_iterationContext extends ParserRuleContext {
		public Statement_iterationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_statement_iteration; }
	 
		public Statement_iterationContext() { }
		public void copyFrom(Statement_iterationContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class WhileStatementContext extends Statement_iterationContext {
		public Expression_singleContext test;
		public StatementContext body;
		public TerminalNode While() { return getToken(XLangParser.While, 0); }
		public TerminalNode OpenParen() { return getToken(XLangParser.OpenParen, 0); }
		public TerminalNode CloseParen() { return getToken(XLangParser.CloseParen, 0); }
		public Expression_singleContext expression_single() {
			return getRuleContext(Expression_singleContext.class,0);
		}
		public StatementContext statement() {
			return getRuleContext(StatementContext.class,0);
		}
		public WhileStatementContext(Statement_iterationContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterWhileStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitWhileStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitWhileStatement(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ForStatementContext extends Statement_iterationContext {
		public Expression_forInitContext init;
		public Expression_singleContext test;
		public SequenceExpressionContext update;
		public StatementContext body;
		public TerminalNode For() { return getToken(XLangParser.For, 0); }
		public TerminalNode OpenParen() { return getToken(XLangParser.OpenParen, 0); }
		public List<TerminalNode> SemiColon() { return getTokens(XLangParser.SemiColon); }
		public TerminalNode SemiColon(int i) {
			return getToken(XLangParser.SemiColon, i);
		}
		public TerminalNode CloseParen() { return getToken(XLangParser.CloseParen, 0); }
		public StatementContext statement() {
			return getRuleContext(StatementContext.class,0);
		}
		public Expression_forInitContext expression_forInit() {
			return getRuleContext(Expression_forInitContext.class,0);
		}
		public Expression_singleContext expression_single() {
			return getRuleContext(Expression_singleContext.class,0);
		}
		public SequenceExpressionContext sequenceExpression() {
			return getRuleContext(SequenceExpressionContext.class,0);
		}
		public ForStatementContext(Statement_iterationContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterForStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitForStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitForStatement(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class DoWhileStatementContext extends Statement_iterationContext {
		public StatementContext body;
		public Expression_singleContext test;
		public TerminalNode Do() { return getToken(XLangParser.Do, 0); }
		public TerminalNode While() { return getToken(XLangParser.While, 0); }
		public TerminalNode OpenParen() { return getToken(XLangParser.OpenParen, 0); }
		public TerminalNode CloseParen() { return getToken(XLangParser.CloseParen, 0); }
		public Eos__Context eos__() {
			return getRuleContext(Eos__Context.class,0);
		}
		public StatementContext statement() {
			return getRuleContext(StatementContext.class,0);
		}
		public Expression_singleContext expression_single() {
			return getRuleContext(Expression_singleContext.class,0);
		}
		public DoWhileStatementContext(Statement_iterationContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterDoWhileStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitDoWhileStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitDoWhileStatement(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ForInStatementContext extends Statement_iterationContext {
		public Expression_iterationLeftContext left;
		public Expression_singleContext right;
		public StatementContext body;
		public TerminalNode For() { return getToken(XLangParser.For, 0); }
		public TerminalNode OpenParen() { return getToken(XLangParser.OpenParen, 0); }
		public TerminalNode In() { return getToken(XLangParser.In, 0); }
		public TerminalNode CloseParen() { return getToken(XLangParser.CloseParen, 0); }
		public Expression_iterationLeftContext expression_iterationLeft() {
			return getRuleContext(Expression_iterationLeftContext.class,0);
		}
		public Expression_singleContext expression_single() {
			return getRuleContext(Expression_singleContext.class,0);
		}
		public StatementContext statement() {
			return getRuleContext(StatementContext.class,0);
		}
		public ForInStatementContext(Statement_iterationContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterForInStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitForInStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitForInStatement(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ForOfStatementContext extends Statement_iterationContext {
		public Expression_iterationLeftContext left;
		public Expression_singleContext right;
		public StatementContext body;
		public TerminalNode For() { return getToken(XLangParser.For, 0); }
		public TerminalNode OpenParen() { return getToken(XLangParser.OpenParen, 0); }
		public TerminalNode Identifier() { return getToken(XLangParser.Identifier, 0); }
		public TerminalNode CloseParen() { return getToken(XLangParser.CloseParen, 0); }
		public Expression_iterationLeftContext expression_iterationLeft() {
			return getRuleContext(Expression_iterationLeftContext.class,0);
		}
		public Expression_singleContext expression_single() {
			return getRuleContext(Expression_singleContext.class,0);
		}
		public StatementContext statement() {
			return getRuleContext(StatementContext.class,0);
		}
		public ForOfStatementContext(Statement_iterationContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterForOfStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitForOfStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitForOfStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Statement_iterationContext statement_iteration() throws RecognitionException {
		Statement_iterationContext _localctx = new Statement_iterationContext(_ctx, getState());
		enterRule(_localctx, 50, RULE_statement_iteration);
		int _la;
		try {
			setState(438);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,19,_ctx) ) {
			case 1:
				_localctx = new DoWhileStatementContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(392);
				match(Do);
				setState(393);
				((DoWhileStatementContext)_localctx).body = statement();
				setState(394);
				match(While);
				setState(395);
				match(OpenParen);
				setState(396);
				((DoWhileStatementContext)_localctx).test = expression_single(0);
				setState(397);
				match(CloseParen);
				setState(398);
				eos__();
				}
				break;
			case 2:
				_localctx = new WhileStatementContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(400);
				match(While);
				setState(401);
				match(OpenParen);
				setState(402);
				((WhileStatementContext)_localctx).test = expression_single(0);
				setState(403);
				match(CloseParen);
				setState(404);
				((WhileStatementContext)_localctx).body = statement();
				}
				break;
			case 3:
				_localctx = new ForStatementContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(406);
				match(For);
				setState(407);
				match(OpenParen);
				setState(409);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (((((_la - 92)) & ~0x3f) == 0 && ((1L << (_la - 92)) & 1090523393L) != 0)) {
					{
					setState(408);
					((ForStatementContext)_localctx).init = expression_forInit();
					}
				}

				setState(411);
				match(SemiColon);
				setState(413);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & -1873497444920065704L) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & 5120592810965729425L) != 0)) {
					{
					setState(412);
					((ForStatementContext)_localctx).test = expression_single(0);
					}
				}

				setState(415);
				match(SemiColon);
				setState(417);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & -1873497444920065704L) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & 5120592810965729425L) != 0)) {
					{
					setState(416);
					((ForStatementContext)_localctx).update = sequenceExpression();
					}
				}

				setState(419);
				match(CloseParen);
				setState(420);
				((ForStatementContext)_localctx).body = statement();
				}
				break;
			case 4:
				_localctx = new ForInStatementContext(_localctx);
				enterOuterAlt(_localctx, 4);
				{
				setState(421);
				match(For);
				setState(422);
				match(OpenParen);
				setState(423);
				((ForInStatementContext)_localctx).left = expression_iterationLeft();
				setState(424);
				match(In);
				setState(425);
				((ForInStatementContext)_localctx).right = expression_single(0);
				setState(426);
				match(CloseParen);
				setState(427);
				((ForInStatementContext)_localctx).body = statement();
				}
				break;
			case 5:
				_localctx = new ForOfStatementContext(_localctx);
				enterOuterAlt(_localctx, 5);
				{
				setState(429);
				match(For);
				setState(430);
				match(OpenParen);
				setState(431);
				((ForOfStatementContext)_localctx).left = expression_iterationLeft();
				setState(432);
				match(Identifier);
				setState(433);
				if (!(this.p("of"))) throw new FailedPredicateException(this, "this.p(\"of\")");
				setState(434);
				((ForOfStatementContext)_localctx).right = expression_single(0);
				setState(435);
				match(CloseParen);
				setState(436);
				((ForOfStatementContext)_localctx).body = statement();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Expression_iterationLeftContext extends ParserRuleContext {
		public Expression_iterationLeftContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expression_iterationLeft; }
	 
		public Expression_iterationLeftContext() { }
		public void copyFrom(Expression_iterationLeftContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class Identifier_forContext extends Expression_iterationLeftContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public Identifier_forContext(Expression_iterationLeftContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterIdentifier_for(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitIdentifier_for(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitIdentifier_for(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class VariableDeclaration_forContext extends Expression_iterationLeftContext {
		public VarModifier_Context kind;
		public VariableDeclaratorContext declarators_single;
		public VarModifier_Context varModifier_() {
			return getRuleContext(VarModifier_Context.class,0);
		}
		public VariableDeclaratorContext variableDeclarator() {
			return getRuleContext(VariableDeclaratorContext.class,0);
		}
		public VariableDeclaration_forContext(Expression_iterationLeftContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterVariableDeclaration_for(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitVariableDeclaration_for(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitVariableDeclaration_for(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Expression_iterationLeftContext expression_iterationLeft() throws RecognitionException {
		Expression_iterationLeftContext _localctx = new Expression_iterationLeftContext(_ctx, getState());
		enterRule(_localctx, 52, RULE_expression_iterationLeft);
		try {
			setState(444);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case From:
			case TypeAlias:
			case Identifier:
				_localctx = new Identifier_forContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(440);
				identifier();
				}
				break;
			case Const:
			case Let:
				_localctx = new VariableDeclaration_forContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(441);
				((VariableDeclaration_forContext)_localctx).kind = varModifier_();
				setState(442);
				((VariableDeclaration_forContext)_localctx).declarators_single = variableDeclarator();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ContinueStatementContext extends ParserRuleContext {
		public TerminalNode Continue() { return getToken(XLangParser.Continue, 0); }
		public Eos__Context eos__() {
			return getRuleContext(Eos__Context.class,0);
		}
		public ContinueStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_continueStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterContinueStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitContinueStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitContinueStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ContinueStatementContext continueStatement() throws RecognitionException {
		ContinueStatementContext _localctx = new ContinueStatementContext(_ctx, getState());
		enterRule(_localctx, 54, RULE_continueStatement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(446);
			match(Continue);
			setState(447);
			eos__();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class BreakStatementContext extends ParserRuleContext {
		public TerminalNode Break() { return getToken(XLangParser.Break, 0); }
		public Eos__Context eos__() {
			return getRuleContext(Eos__Context.class,0);
		}
		public BreakStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_breakStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterBreakStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitBreakStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitBreakStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final BreakStatementContext breakStatement() throws RecognitionException {
		BreakStatementContext _localctx = new BreakStatementContext(_ctx, getState());
		enterRule(_localctx, 56, RULE_breakStatement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(449);
			match(Break);
			setState(450);
			eos__();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ReturnStatementContext extends ParserRuleContext {
		public Expression_singleContext argument;
		public TerminalNode Return() { return getToken(XLangParser.Return, 0); }
		public Eos__Context eos__() {
			return getRuleContext(Eos__Context.class,0);
		}
		public Expression_singleContext expression_single() {
			return getRuleContext(Expression_singleContext.class,0);
		}
		public ReturnStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_returnStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterReturnStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitReturnStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitReturnStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ReturnStatementContext returnStatement() throws RecognitionException {
		ReturnStatementContext _localctx = new ReturnStatementContext(_ctx, getState());
		enterRule(_localctx, 58, RULE_returnStatement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(452);
			match(Return);
			setState(455);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,21,_ctx) ) {
			case 1:
				{
				setState(453);
				if (!(this.notLineTerminator())) throw new FailedPredicateException(this, "this.notLineTerminator()");
				setState(454);
				((ReturnStatementContext)_localctx).argument = expression_single(0);
				}
				break;
			}
			setState(457);
			eos__();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class AssignmentExpressionContext extends ParserRuleContext {
		public Expression_leftHandSideContext left;
		public AssignmentOperator_Context operator;
		public Expression_singleContext right;
		public Eos__Context eos__() {
			return getRuleContext(Eos__Context.class,0);
		}
		public Expression_leftHandSideContext expression_leftHandSide() {
			return getRuleContext(Expression_leftHandSideContext.class,0);
		}
		public AssignmentOperator_Context assignmentOperator_() {
			return getRuleContext(AssignmentOperator_Context.class,0);
		}
		public Expression_singleContext expression_single() {
			return getRuleContext(Expression_singleContext.class,0);
		}
		public AssignmentExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_assignmentExpression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterAssignmentExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitAssignmentExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitAssignmentExpression(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AssignmentExpressionContext assignmentExpression() throws RecognitionException {
		AssignmentExpressionContext _localctx = new AssignmentExpressionContext(_ctx, getState());
		enterRule(_localctx, 60, RULE_assignmentExpression);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(459);
			((AssignmentExpressionContext)_localctx).left = expression_leftHandSide();
			setState(460);
			((AssignmentExpressionContext)_localctx).operator = assignmentOperator_();
			setState(461);
			((AssignmentExpressionContext)_localctx).right = expression_single(0);
			setState(462);
			eos__();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Expression_leftHandSideContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public MemberExpressionContext memberExpression() {
			return getRuleContext(MemberExpressionContext.class,0);
		}
		public Expression_leftHandSideContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expression_leftHandSide; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterExpression_leftHandSide(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitExpression_leftHandSide(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitExpression_leftHandSide(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Expression_leftHandSideContext expression_leftHandSide() throws RecognitionException {
		Expression_leftHandSideContext _localctx = new Expression_leftHandSideContext(_ctx, getState());
		enterRule(_localctx, 62, RULE_expression_leftHandSide);
		try {
			setState(466);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,22,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(464);
				identifier();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(465);
				memberExpression();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class SwitchStatementContext extends ParserRuleContext {
		public Expression_singleContext discriminant;
		public SwitchCases_Context cases;
		public CaseStatements_Context defaultCase;
		public TerminalNode Switch() { return getToken(XLangParser.Switch, 0); }
		public TerminalNode OpenParen() { return getToken(XLangParser.OpenParen, 0); }
		public TerminalNode CloseParen() { return getToken(XLangParser.CloseParen, 0); }
		public TerminalNode OpenBrace() { return getToken(XLangParser.OpenBrace, 0); }
		public TerminalNode CloseBrace() { return getToken(XLangParser.CloseBrace, 0); }
		public Expression_singleContext expression_single() {
			return getRuleContext(Expression_singleContext.class,0);
		}
		public SwitchCases_Context switchCases_() {
			return getRuleContext(SwitchCases_Context.class,0);
		}
		public TerminalNode Default() { return getToken(XLangParser.Default, 0); }
		public TerminalNode Colon() { return getToken(XLangParser.Colon, 0); }
		public CaseStatements_Context caseStatements_() {
			return getRuleContext(CaseStatements_Context.class,0);
		}
		public SwitchStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_switchStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterSwitchStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitSwitchStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitSwitchStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SwitchStatementContext switchStatement() throws RecognitionException {
		SwitchStatementContext _localctx = new SwitchStatementContext(_ctx, getState());
		enterRule(_localctx, 64, RULE_switchStatement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(468);
			match(Switch);
			setState(469);
			match(OpenParen);
			setState(470);
			((SwitchStatementContext)_localctx).discriminant = expression_single(0);
			setState(471);
			match(CloseParen);
			setState(472);
			match(OpenBrace);
			setState(473);
			((SwitchStatementContext)_localctx).cases = switchCases_();
			setState(477);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Default) {
				{
				setState(474);
				match(Default);
				setState(475);
				match(Colon);
				setState(476);
				((SwitchStatementContext)_localctx).defaultCase = caseStatements_();
				}
			}

			setState(479);
			match(CloseBrace);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class SwitchCases_Context extends ParserRuleContext {
		public SwitchCaseContext e;
		public List<SwitchCaseContext> switchCase() {
			return getRuleContexts(SwitchCaseContext.class);
		}
		public SwitchCaseContext switchCase(int i) {
			return getRuleContext(SwitchCaseContext.class,i);
		}
		public SwitchCases_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_switchCases_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterSwitchCases_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitSwitchCases_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitSwitchCases_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SwitchCases_Context switchCases_() throws RecognitionException {
		SwitchCases_Context _localctx = new SwitchCases_Context(_ctx, getState());
		enterRule(_localctx, 66, RULE_switchCases_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(482); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(481);
				((SwitchCases_Context)_localctx).e = switchCase();
				}
				}
				setState(484); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( _la==Case );
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class SwitchCaseContext extends ParserRuleContext {
		public Expression_singleContext test;
		public CaseStatements_Context consequent;
		public TerminalNode Case() { return getToken(XLangParser.Case, 0); }
		public TerminalNode Colon() { return getToken(XLangParser.Colon, 0); }
		public Expression_singleContext expression_single() {
			return getRuleContext(Expression_singleContext.class,0);
		}
		public CaseStatements_Context caseStatements_() {
			return getRuleContext(CaseStatements_Context.class,0);
		}
		public SwitchCaseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_switchCase; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterSwitchCase(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitSwitchCase(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitSwitchCase(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SwitchCaseContext switchCase() throws RecognitionException {
		SwitchCaseContext _localctx = new SwitchCaseContext(_ctx, getState());
		enterRule(_localctx, 68, RULE_switchCase);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(486);
			match(Case);
			setState(487);
			((SwitchCaseContext)_localctx).test = expression_single(0);
			setState(488);
			match(Colon);
			setState(490);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,25,_ctx) ) {
			case 1:
				{
				setState(489);
				((SwitchCaseContext)_localctx).consequent = caseStatements_();
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ThrowStatementContext extends ParserRuleContext {
		public Expression_singleContext argument;
		public TerminalNode Throw() { return getToken(XLangParser.Throw, 0); }
		public Eos__Context eos__() {
			return getRuleContext(Eos__Context.class,0);
		}
		public Expression_singleContext expression_single() {
			return getRuleContext(Expression_singleContext.class,0);
		}
		public ThrowStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_throwStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterThrowStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitThrowStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitThrowStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ThrowStatementContext throwStatement() throws RecognitionException {
		ThrowStatementContext _localctx = new ThrowStatementContext(_ctx, getState());
		enterRule(_localctx, 70, RULE_throwStatement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(492);
			match(Throw);
			setState(493);
			if (!(this.notLineTerminator())) throw new FailedPredicateException(this, "this.notLineTerminator()");
			setState(494);
			((ThrowStatementContext)_localctx).argument = expression_single(0);
			setState(495);
			eos__();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class TryStatementContext extends ParserRuleContext {
		public BlockStatementContext block;
		public CatchClauseContext catchHandler;
		public BlockStatement_finallyContext finalizer;
		public TerminalNode Try() { return getToken(XLangParser.Try, 0); }
		public BlockStatementContext blockStatement() {
			return getRuleContext(BlockStatementContext.class,0);
		}
		public CatchClauseContext catchClause() {
			return getRuleContext(CatchClauseContext.class,0);
		}
		public BlockStatement_finallyContext blockStatement_finally() {
			return getRuleContext(BlockStatement_finallyContext.class,0);
		}
		public TryStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_tryStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterTryStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitTryStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitTryStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TryStatementContext tryStatement() throws RecognitionException {
		TryStatementContext _localctx = new TryStatementContext(_ctx, getState());
		enterRule(_localctx, 72, RULE_tryStatement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(497);
			match(Try);
			setState(498);
			((TryStatementContext)_localctx).block = blockStatement();
			setState(500);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,26,_ctx) ) {
			case 1:
				{
				setState(499);
				((TryStatementContext)_localctx).catchHandler = catchClause();
				}
				break;
			}
			setState(503);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,27,_ctx) ) {
			case 1:
				{
				setState(502);
				((TryStatementContext)_localctx).finalizer = blockStatement_finally();
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class CatchClauseContext extends ParserRuleContext {
		public IdentifierContext name;
		public ParameterizedTypeNodeContext varType;
		public BlockStatementContext body;
		public TerminalNode Catch() { return getToken(XLangParser.Catch, 0); }
		public TerminalNode OpenParen() { return getToken(XLangParser.OpenParen, 0); }
		public TerminalNode CloseParen() { return getToken(XLangParser.CloseParen, 0); }
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public BlockStatementContext blockStatement() {
			return getRuleContext(BlockStatementContext.class,0);
		}
		public TerminalNode Colon() { return getToken(XLangParser.Colon, 0); }
		public ParameterizedTypeNodeContext parameterizedTypeNode() {
			return getRuleContext(ParameterizedTypeNodeContext.class,0);
		}
		public CatchClauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_catchClause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterCatchClause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitCatchClause(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitCatchClause(this);
			else return visitor.visitChildren(this);
		}
	}

	public final CatchClauseContext catchClause() throws RecognitionException {
		CatchClauseContext _localctx = new CatchClauseContext(_ctx, getState());
		enterRule(_localctx, 74, RULE_catchClause);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(505);
			match(Catch);
			setState(506);
			match(OpenParen);
			setState(507);
			((CatchClauseContext)_localctx).name = identifier();
			setState(510);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Colon) {
				{
				setState(508);
				match(Colon);
				setState(509);
				((CatchClauseContext)_localctx).varType = parameterizedTypeNode();
				}
			}

			setState(512);
			match(CloseParen);
			setState(513);
			((CatchClauseContext)_localctx).body = blockStatement();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class BlockStatement_finallyContext extends ParserRuleContext {
		public TerminalNode Finally() { return getToken(XLangParser.Finally, 0); }
		public BlockStatementContext blockStatement() {
			return getRuleContext(BlockStatementContext.class,0);
		}
		public BlockStatement_finallyContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_blockStatement_finally; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterBlockStatement_finally(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitBlockStatement_finally(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitBlockStatement_finally(this);
			else return visitor.visitChildren(this);
		}
	}

	public final BlockStatement_finallyContext blockStatement_finally() throws RecognitionException {
		BlockStatement_finallyContext _localctx = new BlockStatement_finallyContext(_ctx, getState());
		enterRule(_localctx, 76, RULE_blockStatement_finally);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(515);
			match(Finally);
			setState(516);
			blockStatement();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class FunctionDeclarationContext extends ParserRuleContext {
		public DecoratorsContext decorators_;
		public IdentifierContext name;
		public ParameterList_Context params;
		public NamedTypeNode_annotationContext returnType;
		public BlockStatementContext body;
		public TerminalNode Function() { return getToken(XLangParser.Function, 0); }
		public TerminalNode OpenParen() { return getToken(XLangParser.OpenParen, 0); }
		public TerminalNode CloseParen() { return getToken(XLangParser.CloseParen, 0); }
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public BlockStatementContext blockStatement() {
			return getRuleContext(BlockStatementContext.class,0);
		}
		public DecoratorsContext decorators() {
			return getRuleContext(DecoratorsContext.class,0);
		}
		public ParameterList_Context parameterList_() {
			return getRuleContext(ParameterList_Context.class,0);
		}
		public NamedTypeNode_annotationContext namedTypeNode_annotation() {
			return getRuleContext(NamedTypeNode_annotationContext.class,0);
		}
		public FunctionDeclarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_functionDeclaration; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterFunctionDeclaration(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitFunctionDeclaration(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitFunctionDeclaration(this);
			else return visitor.visitChildren(this);
		}
	}

	public final FunctionDeclarationContext functionDeclaration() throws RecognitionException {
		FunctionDeclarationContext _localctx = new FunctionDeclarationContext(_ctx, getState());
		enterRule(_localctx, 78, RULE_functionDeclaration);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(519);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==At) {
				{
				setState(518);
				((FunctionDeclarationContext)_localctx).decorators_ = decorators();
				}
			}

			setState(521);
			match(Function);
			setState(522);
			((FunctionDeclarationContext)_localctx).name = identifier();
			setState(523);
			match(OpenParen);
			setState(525);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==OpenBracket || _la==OpenBrace || ((((_la - 92)) & ~0x3f) == 0 && ((1L << (_la - 92)) & 1224736769L) != 0)) {
				{
				setState(524);
				((FunctionDeclarationContext)_localctx).params = parameterList_();
				}
			}

			setState(527);
			match(CloseParen);
			setState(529);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Colon) {
				{
				setState(528);
				((FunctionDeclarationContext)_localctx).returnType = namedTypeNode_annotation();
				}
			}

			setState(531);
			((FunctionDeclarationContext)_localctx).body = blockStatement();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ParameterList_Context extends ParserRuleContext {
		public ParameterDeclarationContext e;
		public List<ParameterDeclarationContext> parameterDeclaration() {
			return getRuleContexts(ParameterDeclarationContext.class);
		}
		public ParameterDeclarationContext parameterDeclaration(int i) {
			return getRuleContext(ParameterDeclarationContext.class,i);
		}
		public List<TerminalNode> Comma() { return getTokens(XLangParser.Comma); }
		public TerminalNode Comma(int i) {
			return getToken(XLangParser.Comma, i);
		}
		public ParameterList_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_parameterList_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterParameterList_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitParameterList_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitParameterList_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ParameterList_Context parameterList_() throws RecognitionException {
		ParameterList_Context _localctx = new ParameterList_Context(_ctx, getState());
		enterRule(_localctx, 80, RULE_parameterList_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(533);
			((ParameterList_Context)_localctx).e = parameterDeclaration();
			setState(538);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==Comma) {
				{
				{
				setState(534);
				match(Comma);
				setState(535);
				((ParameterList_Context)_localctx).e = parameterDeclaration();
				}
				}
				setState(540);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ParameterDeclarationContext extends ParserRuleContext {
		public DecoratorsContext decorators_;
		public Ast_identifierOrPatternContext name;
		public NamedTypeNode_annotationContext type;
		public Expression_initializerContext initializer;
		public Ast_identifierOrPatternContext ast_identifierOrPattern() {
			return getRuleContext(Ast_identifierOrPatternContext.class,0);
		}
		public DecoratorsContext decorators() {
			return getRuleContext(DecoratorsContext.class,0);
		}
		public NamedTypeNode_annotationContext namedTypeNode_annotation() {
			return getRuleContext(NamedTypeNode_annotationContext.class,0);
		}
		public Expression_initializerContext expression_initializer() {
			return getRuleContext(Expression_initializerContext.class,0);
		}
		public ParameterDeclarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_parameterDeclaration; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterParameterDeclaration(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitParameterDeclaration(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitParameterDeclaration(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ParameterDeclarationContext parameterDeclaration() throws RecognitionException {
		ParameterDeclarationContext _localctx = new ParameterDeclarationContext(_ctx, getState());
		enterRule(_localctx, 82, RULE_parameterDeclaration);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(542);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==At) {
				{
				setState(541);
				((ParameterDeclarationContext)_localctx).decorators_ = decorators();
				}
			}

			setState(544);
			((ParameterDeclarationContext)_localctx).name = ast_identifierOrPattern();
			setState(546);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Colon) {
				{
				setState(545);
				((ParameterDeclarationContext)_localctx).type = namedTypeNode_annotation();
				}
			}

			setState(549);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Assign) {
				{
				setState(548);
				((ParameterDeclarationContext)_localctx).initializer = expression_initializer();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Ast_identifierOrPatternContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public ArrayBindingContext arrayBinding() {
			return getRuleContext(ArrayBindingContext.class,0);
		}
		public ObjectBindingContext objectBinding() {
			return getRuleContext(ObjectBindingContext.class,0);
		}
		public Ast_identifierOrPatternContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_ast_identifierOrPattern; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterAst_identifierOrPattern(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitAst_identifierOrPattern(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitAst_identifierOrPattern(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Ast_identifierOrPatternContext ast_identifierOrPattern() throws RecognitionException {
		Ast_identifierOrPatternContext _localctx = new Ast_identifierOrPatternContext(_ctx, getState());
		enterRule(_localctx, 84, RULE_ast_identifierOrPattern);
		try {
			setState(554);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case From:
			case TypeAlias:
			case Identifier:
				enterOuterAlt(_localctx, 1);
				{
				setState(551);
				identifier();
				}
				break;
			case OpenBracket:
				enterOuterAlt(_localctx, 2);
				{
				setState(552);
				arrayBinding();
				}
				break;
			case OpenBrace:
				enterOuterAlt(_localctx, 3);
				{
				setState(553);
				objectBinding();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Expression_initializerContext extends ParserRuleContext {
		public TerminalNode Assign() { return getToken(XLangParser.Assign, 0); }
		public Expression_singleContext expression_single() {
			return getRuleContext(Expression_singleContext.class,0);
		}
		public Expression_initializerContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expression_initializer; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterExpression_initializer(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitExpression_initializer(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitExpression_initializer(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Expression_initializerContext expression_initializer() throws RecognitionException {
		Expression_initializerContext _localctx = new Expression_initializerContext(_ctx, getState());
		enterRule(_localctx, 86, RULE_expression_initializer);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(556);
			match(Assign);
			setState(557);
			expression_single(0);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ArrayBindingContext extends ParserRuleContext {
		public ArrayBindingContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arrayBinding; }
	 
		public ArrayBindingContext() { }
		public void copyFrom(ArrayBindingContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ArrayBinding_restContext extends ArrayBindingContext {
		public RestBindingContext restBinding_;
		public TerminalNode OpenBracket() { return getToken(XLangParser.OpenBracket, 0); }
		public TerminalNode CloseBracket() { return getToken(XLangParser.CloseBracket, 0); }
		public RestBindingContext restBinding() {
			return getRuleContext(RestBindingContext.class,0);
		}
		public TerminalNode Comma() { return getToken(XLangParser.Comma, 0); }
		public ArrayBinding_restContext(ArrayBindingContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterArrayBinding_rest(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitArrayBinding_rest(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitArrayBinding_rest(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ArrayBinding_fullContext extends ArrayBindingContext {
		public ArrayElementBindings_Context elements;
		public RestBindingContext restBinding_;
		public TerminalNode OpenBracket() { return getToken(XLangParser.OpenBracket, 0); }
		public TerminalNode CloseBracket() { return getToken(XLangParser.CloseBracket, 0); }
		public ArrayElementBindings_Context arrayElementBindings_() {
			return getRuleContext(ArrayElementBindings_Context.class,0);
		}
		public TerminalNode Comma() { return getToken(XLangParser.Comma, 0); }
		public RestBindingContext restBinding() {
			return getRuleContext(RestBindingContext.class,0);
		}
		public ArrayBinding_fullContext(ArrayBindingContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterArrayBinding_full(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitArrayBinding_full(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitArrayBinding_full(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ArrayBindingContext arrayBinding() throws RecognitionException {
		ArrayBindingContext _localctx = new ArrayBindingContext(_ctx, getState());
		enterRule(_localctx, 88, RULE_arrayBinding);
		int _la;
		try {
			setState(575);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,39,_ctx) ) {
			case 1:
				_localctx = new ArrayBinding_fullContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(559);
				match(OpenBracket);
				setState(560);
				((ArrayBinding_fullContext)_localctx).elements = arrayElementBindings_();
				setState(564);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,37,_ctx) ) {
				case 1:
					{
					setState(561);
					match(Comma);
					setState(562);
					((ArrayBinding_fullContext)_localctx).restBinding_ = restBinding();
					}
					break;
				case 2:
					{
					setState(563);
					match(Comma);
					}
					break;
				}
				setState(566);
				match(CloseBracket);
				}
				break;
			case 2:
				_localctx = new ArrayBinding_restContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(568);
				match(OpenBracket);
				setState(569);
				((ArrayBinding_restContext)_localctx).restBinding_ = restBinding();
				setState(571);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==Comma) {
					{
					setState(570);
					match(Comma);
					}
				}

				setState(573);
				match(CloseBracket);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ArrayElementBindings_Context extends ParserRuleContext {
		public ArrayElementBindingContext e;
		public List<ArrayElementBindingContext> arrayElementBinding() {
			return getRuleContexts(ArrayElementBindingContext.class);
		}
		public ArrayElementBindingContext arrayElementBinding(int i) {
			return getRuleContext(ArrayElementBindingContext.class,i);
		}
		public List<TerminalNode> Comma() { return getTokens(XLangParser.Comma); }
		public TerminalNode Comma(int i) {
			return getToken(XLangParser.Comma, i);
		}
		public ArrayElementBindings_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arrayElementBindings_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterArrayElementBindings_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitArrayElementBindings_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitArrayElementBindings_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ArrayElementBindings_Context arrayElementBindings_() throws RecognitionException {
		ArrayElementBindings_Context _localctx = new ArrayElementBindings_Context(_ctx, getState());
		enterRule(_localctx, 90, RULE_arrayElementBindings_);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(577);
			((ArrayElementBindings_Context)_localctx).e = arrayElementBinding();
			setState(582);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,40,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(578);
					match(Comma);
					setState(579);
					((ArrayElementBindings_Context)_localctx).e = arrayElementBinding();
					}
					} 
				}
				setState(584);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,40,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ArrayElementBindingContext extends ParserRuleContext {
		public IdentifierContext identifier_;
		public Expression_initializerContext initializer;
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public Expression_initializerContext expression_initializer() {
			return getRuleContext(Expression_initializerContext.class,0);
		}
		public ArrayElementBindingContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arrayElementBinding; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterArrayElementBinding(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitArrayElementBinding(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitArrayElementBinding(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ArrayElementBindingContext arrayElementBinding() throws RecognitionException {
		ArrayElementBindingContext _localctx = new ArrayElementBindingContext(_ctx, getState());
		enterRule(_localctx, 92, RULE_arrayElementBinding);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(585);
			((ArrayElementBindingContext)_localctx).identifier_ = identifier();
			setState(587);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Assign) {
				{
				setState(586);
				((ArrayElementBindingContext)_localctx).initializer = expression_initializer();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ObjectBindingContext extends ParserRuleContext {
		public ObjectBindingContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_objectBinding; }
	 
		public ObjectBindingContext() { }
		public void copyFrom(ObjectBindingContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ObjectBinding_fullContext extends ObjectBindingContext {
		public PropertyBindings_Context properties;
		public RestBindingContext restBinding_;
		public TerminalNode OpenBrace() { return getToken(XLangParser.OpenBrace, 0); }
		public TerminalNode CloseBrace() { return getToken(XLangParser.CloseBrace, 0); }
		public TerminalNode Comma() { return getToken(XLangParser.Comma, 0); }
		public PropertyBindings_Context propertyBindings_() {
			return getRuleContext(PropertyBindings_Context.class,0);
		}
		public RestBindingContext restBinding() {
			return getRuleContext(RestBindingContext.class,0);
		}
		public ObjectBinding_fullContext(ObjectBindingContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterObjectBinding_full(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitObjectBinding_full(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitObjectBinding_full(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ObjectBinding_restContext extends ObjectBindingContext {
		public RestBindingContext restBinding_;
		public TerminalNode OpenBrace() { return getToken(XLangParser.OpenBrace, 0); }
		public TerminalNode CloseBrace() { return getToken(XLangParser.CloseBrace, 0); }
		public RestBindingContext restBinding() {
			return getRuleContext(RestBindingContext.class,0);
		}
		public TerminalNode Comma() { return getToken(XLangParser.Comma, 0); }
		public ObjectBinding_restContext(ObjectBindingContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterObjectBinding_rest(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitObjectBinding_rest(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitObjectBinding_rest(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ObjectBindingContext objectBinding() throws RecognitionException {
		ObjectBindingContext _localctx = new ObjectBindingContext(_ctx, getState());
		enterRule(_localctx, 94, RULE_objectBinding);
		int _la;
		try {
			setState(606);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,45,_ctx) ) {
			case 1:
				_localctx = new ObjectBinding_fullContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(589);
				match(OpenBrace);
				setState(591);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 2161727821137838080L) != 0) || ((((_la - 65)) & ~0x3f) == 0 && ((1L << (_la - 65)) & 184647583111577599L) != 0)) {
					{
					setState(590);
					((ObjectBinding_fullContext)_localctx).properties = propertyBindings_();
					}
				}

				setState(596);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,43,_ctx) ) {
				case 1:
					{
					setState(593);
					match(Comma);
					setState(594);
					((ObjectBinding_fullContext)_localctx).restBinding_ = restBinding();
					}
					break;
				case 2:
					{
					setState(595);
					match(Comma);
					}
					break;
				}
				setState(598);
				match(CloseBrace);
				}
				break;
			case 2:
				_localctx = new ObjectBinding_restContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(599);
				match(OpenBrace);
				setState(600);
				((ObjectBinding_restContext)_localctx).restBinding_ = restBinding();
				setState(602);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==Comma) {
					{
					setState(601);
					match(Comma);
					}
				}

				setState(604);
				match(CloseBrace);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class PropertyBindings_Context extends ParserRuleContext {
		public PropertyBindingContext e;
		public List<PropertyBindingContext> propertyBinding() {
			return getRuleContexts(PropertyBindingContext.class);
		}
		public PropertyBindingContext propertyBinding(int i) {
			return getRuleContext(PropertyBindingContext.class,i);
		}
		public List<TerminalNode> Comma() { return getTokens(XLangParser.Comma); }
		public TerminalNode Comma(int i) {
			return getToken(XLangParser.Comma, i);
		}
		public PropertyBindings_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_propertyBindings_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterPropertyBindings_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitPropertyBindings_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitPropertyBindings_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PropertyBindings_Context propertyBindings_() throws RecognitionException {
		PropertyBindings_Context _localctx = new PropertyBindings_Context(_ctx, getState());
		enterRule(_localctx, 96, RULE_propertyBindings_);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(608);
			((PropertyBindings_Context)_localctx).e = propertyBinding();
			setState(613);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,46,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(609);
					match(Comma);
					setState(610);
					((PropertyBindings_Context)_localctx).e = propertyBinding();
					}
					} 
				}
				setState(615);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,46,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class PropertyBindingContext extends ParserRuleContext {
		public PropertyBindingContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_propertyBinding; }
	 
		public PropertyBindingContext() { }
		public void copyFrom(PropertyBindingContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class PropertyBinding_fullContext extends PropertyBindingContext {
		public PropertyName_Context propName;
		public IdentifierContext identifier_;
		public Expression_initializerContext initializer;
		public TerminalNode Colon() { return getToken(XLangParser.Colon, 0); }
		public PropertyName_Context propertyName_() {
			return getRuleContext(PropertyName_Context.class,0);
		}
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public Expression_initializerContext expression_initializer() {
			return getRuleContext(Expression_initializerContext.class,0);
		}
		public PropertyBinding_fullContext(PropertyBindingContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterPropertyBinding_full(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitPropertyBinding_full(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitPropertyBinding_full(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class PropertyBinding_simpleContext extends PropertyBindingContext {
		public IdentifierContext identifier_;
		public Expression_initializerContext initializer;
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public Expression_initializerContext expression_initializer() {
			return getRuleContext(Expression_initializerContext.class,0);
		}
		public PropertyBinding_simpleContext(PropertyBindingContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterPropertyBinding_simple(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitPropertyBinding_simple(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitPropertyBinding_simple(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PropertyBindingContext propertyBinding() throws RecognitionException {
		PropertyBindingContext _localctx = new PropertyBindingContext(_ctx, getState());
		enterRule(_localctx, 98, RULE_propertyBinding);
		int _la;
		try {
			setState(626);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,49,_ctx) ) {
			case 1:
				_localctx = new PropertyBinding_fullContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(616);
				((PropertyBinding_fullContext)_localctx).propName = propertyName_();
				setState(617);
				match(Colon);
				setState(618);
				((PropertyBinding_fullContext)_localctx).identifier_ = identifier();
				setState(620);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==Assign) {
					{
					setState(619);
					((PropertyBinding_fullContext)_localctx).initializer = expression_initializer();
					}
				}

				}
				break;
			case 2:
				_localctx = new PropertyBinding_simpleContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(622);
				((PropertyBinding_simpleContext)_localctx).identifier_ = identifier();
				setState(624);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==Assign) {
					{
					setState(623);
					((PropertyBinding_simpleContext)_localctx).initializer = expression_initializer();
					}
				}

				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class RestBindingContext extends ParserRuleContext {
		public IdentifierContext identifier_;
		public Expression_initializerContext initializer;
		public TerminalNode Ellipsis() { return getToken(XLangParser.Ellipsis, 0); }
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public Expression_initializerContext expression_initializer() {
			return getRuleContext(Expression_initializerContext.class,0);
		}
		public RestBindingContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_restBinding; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterRestBinding(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitRestBinding(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitRestBinding(this);
			else return visitor.visitChildren(this);
		}
	}

	public final RestBindingContext restBinding() throws RecognitionException {
		RestBindingContext _localctx = new RestBindingContext(_ctx, getState());
		enterRule(_localctx, 100, RULE_restBinding);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(628);
			match(Ellipsis);
			setState(629);
			((RestBindingContext)_localctx).identifier_ = identifier();
			setState(631);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Assign) {
				{
				setState(630);
				((RestBindingContext)_localctx).initializer = expression_initializer();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ArrayExpressionContext extends ParserRuleContext {
		public ElementList_Context elements;
		public TerminalNode OpenBracket() { return getToken(XLangParser.OpenBracket, 0); }
		public TerminalNode CloseBracket() { return getToken(XLangParser.CloseBracket, 0); }
		public ElementList_Context elementList_() {
			return getRuleContext(ElementList_Context.class,0);
		}
		public ArrayExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arrayExpression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterArrayExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitArrayExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitArrayExpression(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ArrayExpressionContext arrayExpression() throws RecognitionException {
		ArrayExpressionContext _localctx = new ArrayExpressionContext(_ctx, getState());
		enterRule(_localctx, 102, RULE_arrayExpression);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(633);
			match(OpenBracket);
			setState(635);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & -1873497444919803560L) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & 5120592810965729425L) != 0)) {
				{
				setState(634);
				((ArrayExpressionContext)_localctx).elements = elementList_();
				}
			}

			setState(637);
			match(CloseBracket);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ElementList_Context extends ParserRuleContext {
		public Ast_arrayElementContext e;
		public List<Ast_arrayElementContext> ast_arrayElement() {
			return getRuleContexts(Ast_arrayElementContext.class);
		}
		public Ast_arrayElementContext ast_arrayElement(int i) {
			return getRuleContext(Ast_arrayElementContext.class,i);
		}
		public List<TerminalNode> Comma() { return getTokens(XLangParser.Comma); }
		public TerminalNode Comma(int i) {
			return getToken(XLangParser.Comma, i);
		}
		public ElementList_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_elementList_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterElementList_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitElementList_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitElementList_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ElementList_Context elementList_() throws RecognitionException {
		ElementList_Context _localctx = new ElementList_Context(_ctx, getState());
		enterRule(_localctx, 104, RULE_elementList_);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(639);
			((ElementList_Context)_localctx).e = ast_arrayElement();
			setState(644);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,52,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(640);
					match(Comma);
					setState(641);
					((ElementList_Context)_localctx).e = ast_arrayElement();
					}
					} 
				}
				setState(646);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,52,_ctx);
			}
			setState(648);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Comma) {
				{
				setState(647);
				match(Comma);
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Ast_arrayElementContext extends ParserRuleContext {
		public Expression_singleContext expression_single() {
			return getRuleContext(Expression_singleContext.class,0);
		}
		public SpreadElementContext spreadElement() {
			return getRuleContext(SpreadElementContext.class,0);
		}
		public Ast_arrayElementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_ast_arrayElement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterAst_arrayElement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitAst_arrayElement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitAst_arrayElement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Ast_arrayElementContext ast_arrayElement() throws RecognitionException {
		Ast_arrayElementContext _localctx = new Ast_arrayElementContext(_ctx, getState());
		enterRule(_localctx, 106, RULE_ast_arrayElement);
		try {
			setState(652);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case RegularExpressionLiteral:
			case OpenBracket:
			case OpenParen:
			case OpenBrace:
			case PlusPlus:
			case MinusMinus:
			case Plus:
			case Minus:
			case BitNot:
			case Not:
			case NullLiteral:
			case BooleanLiteral:
			case DecimalIntegerLiteral:
			case HexIntegerLiteral:
			case BinaryIntegerLiteral:
			case DecimalLiteral:
			case Typeof:
			case New:
			case This:
			case Delete:
			case From:
			case Super:
			case TypeAlias:
			case StringLiteral:
			case TemplateStringLiteral:
			case Identifier:
			case CpExprStart:
				enterOuterAlt(_localctx, 1);
				{
				setState(650);
				expression_single(0);
				}
				break;
			case Ellipsis:
				enterOuterAlt(_localctx, 2);
				{
				setState(651);
				spreadElement();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class SpreadElementContext extends ParserRuleContext {
		public Expression_singleContext argument;
		public TerminalNode Ellipsis() { return getToken(XLangParser.Ellipsis, 0); }
		public Expression_singleContext expression_single() {
			return getRuleContext(Expression_singleContext.class,0);
		}
		public SpreadElementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_spreadElement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterSpreadElement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitSpreadElement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitSpreadElement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SpreadElementContext spreadElement() throws RecognitionException {
		SpreadElementContext _localctx = new SpreadElementContext(_ctx, getState());
		enterRule(_localctx, 108, RULE_spreadElement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(654);
			match(Ellipsis);
			setState(655);
			((SpreadElementContext)_localctx).argument = expression_single(0);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ObjectExpressionContext extends ParserRuleContext {
		public ObjectProperties_Context properties;
		public TerminalNode OpenBrace() { return getToken(XLangParser.OpenBrace, 0); }
		public TerminalNode CloseBrace() { return getToken(XLangParser.CloseBrace, 0); }
		public TerminalNode Comma() { return getToken(XLangParser.Comma, 0); }
		public ObjectProperties_Context objectProperties_() {
			return getRuleContext(ObjectProperties_Context.class,0);
		}
		public ObjectExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_objectExpression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterObjectExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitObjectExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitObjectExpression(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ObjectExpressionContext objectExpression() throws RecognitionException {
		ObjectExpressionContext _localctx = new ObjectExpressionContext(_ctx, getState());
		enterRule(_localctx, 110, RULE_objectExpression);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(657);
			match(OpenBrace);
			setState(659);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 2161727821138100240L) != 0) || ((((_la - 65)) & ~0x3f) == 0 && ((1L << (_la - 65)) & 184647583111577599L) != 0)) {
				{
				setState(658);
				((ObjectExpressionContext)_localctx).properties = objectProperties_();
				}
			}

			setState(662);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Comma) {
				{
				setState(661);
				match(Comma);
				}
			}

			setState(664);
			match(CloseBrace);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ObjectProperties_Context extends ParserRuleContext {
		public Ast_objectPropertyContext e;
		public List<Ast_objectPropertyContext> ast_objectProperty() {
			return getRuleContexts(Ast_objectPropertyContext.class);
		}
		public Ast_objectPropertyContext ast_objectProperty(int i) {
			return getRuleContext(Ast_objectPropertyContext.class,i);
		}
		public List<TerminalNode> Comma() { return getTokens(XLangParser.Comma); }
		public TerminalNode Comma(int i) {
			return getToken(XLangParser.Comma, i);
		}
		public ObjectProperties_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_objectProperties_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterObjectProperties_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitObjectProperties_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitObjectProperties_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ObjectProperties_Context objectProperties_() throws RecognitionException {
		ObjectProperties_Context _localctx = new ObjectProperties_Context(_ctx, getState());
		enterRule(_localctx, 112, RULE_objectProperties_);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(666);
			((ObjectProperties_Context)_localctx).e = ast_objectProperty();
			setState(671);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,57,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(667);
					match(Comma);
					setState(668);
					((ObjectProperties_Context)_localctx).e = ast_objectProperty();
					}
					} 
				}
				setState(673);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,57,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Ast_objectPropertyContext extends ParserRuleContext {
		public PropertyAssignmentContext propertyAssignment() {
			return getRuleContext(PropertyAssignmentContext.class,0);
		}
		public SpreadElementContext spreadElement() {
			return getRuleContext(SpreadElementContext.class,0);
		}
		public Ast_objectPropertyContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_ast_objectProperty; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterAst_objectProperty(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitAst_objectProperty(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitAst_objectProperty(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Ast_objectPropertyContext ast_objectProperty() throws RecognitionException {
		Ast_objectPropertyContext _localctx = new Ast_objectPropertyContext(_ctx, getState());
		enterRule(_localctx, 114, RULE_ast_objectProperty);
		try {
			setState(676);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case OpenBracket:
			case NullLiteral:
			case BooleanLiteral:
			case AndLiteral:
			case OrLiteral:
			case Break:
			case Do:
			case Instanceof:
			case Typeof:
			case Case:
			case Else:
			case New:
			case Var:
			case Catch:
			case Finally:
			case Return:
			case Void:
			case Continue:
			case For:
			case Switch:
			case While:
			case Debugger:
			case Function:
			case This:
			case With:
			case Default:
			case If:
			case Throw:
			case Delete:
			case In:
			case Try:
			case As:
			case From:
			case ReadOnly:
			case Class:
			case Enum:
			case Extends:
			case Super:
			case Const:
			case Export:
			case Import:
			case Implements:
			case Let:
			case Private:
			case Public:
			case Interface:
			case Package:
			case Protected:
			case Static:
			case Any:
			case Number:
			case Boolean:
			case String:
			case Symbol:
			case TypeAlias:
			case StringLiteral:
			case Identifier:
				enterOuterAlt(_localctx, 1);
				{
				setState(674);
				propertyAssignment();
				}
				break;
			case Ellipsis:
				enterOuterAlt(_localctx, 2);
				{
				setState(675);
				spreadElement();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class PropertyAssignmentContext extends ParserRuleContext {
		public PropertyAssignmentContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_propertyAssignment; }
	 
		public PropertyAssignmentContext() { }
		public void copyFrom(PropertyAssignmentContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class PropertyAssignment_shorthandContext extends PropertyAssignmentContext {
		public Identifier_exContext key;
		public Identifier_exContext identifier_ex() {
			return getRuleContext(Identifier_exContext.class,0);
		}
		public PropertyAssignment_shorthandContext(PropertyAssignmentContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterPropertyAssignment_shorthand(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitPropertyAssignment_shorthand(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitPropertyAssignment_shorthand(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class PropertyAssignment_assignContext extends PropertyAssignmentContext {
		public Expression_propNameContext key;
		public Expression_singleContext value;
		public TerminalNode Colon() { return getToken(XLangParser.Colon, 0); }
		public Expression_propNameContext expression_propName() {
			return getRuleContext(Expression_propNameContext.class,0);
		}
		public Expression_singleContext expression_single() {
			return getRuleContext(Expression_singleContext.class,0);
		}
		public PropertyAssignment_assignContext(PropertyAssignmentContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterPropertyAssignment_assign(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitPropertyAssignment_assign(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitPropertyAssignment_assign(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class PropertyAssignment_computedContext extends PropertyAssignmentContext {
		public Expression_singleContext key;
		public Expression_singleContext value;
		public TerminalNode OpenBracket() { return getToken(XLangParser.OpenBracket, 0); }
		public TerminalNode CloseBracket() { return getToken(XLangParser.CloseBracket, 0); }
		public TerminalNode Colon() { return getToken(XLangParser.Colon, 0); }
		public List<Expression_singleContext> expression_single() {
			return getRuleContexts(Expression_singleContext.class);
		}
		public Expression_singleContext expression_single(int i) {
			return getRuleContext(Expression_singleContext.class,i);
		}
		public PropertyAssignment_computedContext(PropertyAssignmentContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterPropertyAssignment_computed(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitPropertyAssignment_computed(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitPropertyAssignment_computed(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PropertyAssignmentContext propertyAssignment() throws RecognitionException {
		PropertyAssignmentContext _localctx = new PropertyAssignmentContext(_ctx, getState());
		enterRule(_localctx, 116, RULE_propertyAssignment);
		try {
			setState(689);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,59,_ctx) ) {
			case 1:
				_localctx = new PropertyAssignment_assignContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(678);
				((PropertyAssignment_assignContext)_localctx).key = expression_propName();
				setState(679);
				match(Colon);
				setState(680);
				((PropertyAssignment_assignContext)_localctx).value = expression_single(0);
				}
				break;
			case 2:
				_localctx = new PropertyAssignment_computedContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(682);
				match(OpenBracket);
				setState(683);
				((PropertyAssignment_computedContext)_localctx).key = expression_single(0);
				setState(684);
				match(CloseBracket);
				setState(685);
				match(Colon);
				setState(686);
				((PropertyAssignment_computedContext)_localctx).value = expression_single(0);
				}
				break;
			case 3:
				_localctx = new PropertyAssignment_shorthandContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(688);
				((PropertyAssignment_shorthandContext)_localctx).key = identifier_ex();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Arguments_Context extends ParserRuleContext {
		public Expression_singleContext e;
		public TerminalNode OpenParen() { return getToken(XLangParser.OpenParen, 0); }
		public TerminalNode CloseParen() { return getToken(XLangParser.CloseParen, 0); }
		public List<Expression_singleContext> expression_single() {
			return getRuleContexts(Expression_singleContext.class);
		}
		public Expression_singleContext expression_single(int i) {
			return getRuleContext(Expression_singleContext.class,i);
		}
		public List<TerminalNode> Comma() { return getTokens(XLangParser.Comma); }
		public TerminalNode Comma(int i) {
			return getToken(XLangParser.Comma, i);
		}
		public Arguments_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arguments_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterArguments_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitArguments_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitArguments_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Arguments_Context arguments_() throws RecognitionException {
		Arguments_Context _localctx = new Arguments_Context(_ctx, getState());
		enterRule(_localctx, 118, RULE_arguments_);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(691);
			match(OpenParen);
			setState(703);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & -1873497444920065704L) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & 5120592810965729425L) != 0)) {
				{
				setState(692);
				((Arguments_Context)_localctx).e = expression_single(0);
				setState(697);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,60,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(693);
						match(Comma);
						setState(694);
						((Arguments_Context)_localctx).e = expression_single(0);
						}
						} 
					}
					setState(699);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,60,_ctx);
				}
				setState(701);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==Comma) {
					{
					setState(700);
					match(Comma);
					}
				}

				}
			}

			setState(705);
			match(CloseParen);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class SequenceExpressionContext extends ParserRuleContext {
		public SingleExpressions_Context expressions;
		public SingleExpressions_Context singleExpressions_() {
			return getRuleContext(SingleExpressions_Context.class,0);
		}
		public SequenceExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sequenceExpression; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterSequenceExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitSequenceExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitSequenceExpression(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SequenceExpressionContext sequenceExpression() throws RecognitionException {
		SequenceExpressionContext _localctx = new SequenceExpressionContext(_ctx, getState());
		enterRule(_localctx, 120, RULE_sequenceExpression);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(707);
			((SequenceExpressionContext)_localctx).expressions = singleExpressions_();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class SingleExpressions_Context extends ParserRuleContext {
		public Expression_singleContext e;
		public List<Expression_singleContext> expression_single() {
			return getRuleContexts(Expression_singleContext.class);
		}
		public Expression_singleContext expression_single(int i) {
			return getRuleContext(Expression_singleContext.class,i);
		}
		public List<TerminalNode> Comma() { return getTokens(XLangParser.Comma); }
		public TerminalNode Comma(int i) {
			return getToken(XLangParser.Comma, i);
		}
		public SingleExpressions_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_singleExpressions_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterSingleExpressions_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitSingleExpressions_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitSingleExpressions_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SingleExpressions_Context singleExpressions_() throws RecognitionException {
		SingleExpressions_Context _localctx = new SingleExpressions_Context(_ctx, getState());
		enterRule(_localctx, 122, RULE_singleExpressions_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(709);
			((SingleExpressions_Context)_localctx).e = expression_single(0);
			setState(714);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==Comma) {
				{
				{
				setState(710);
				match(Comma);
				setState(711);
				((SingleExpressions_Context)_localctx).e = expression_single(0);
				}
				}
				setState(716);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class AssignmentExpression_initContext extends ParserRuleContext {
		public IdentifierContext left;
		public Token operator;
		public Expression_singleContext right;
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TerminalNode Assign() { return getToken(XLangParser.Assign, 0); }
		public Expression_singleContext expression_single() {
			return getRuleContext(Expression_singleContext.class,0);
		}
		public AssignmentExpression_initContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_assignmentExpression_init; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterAssignmentExpression_init(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitAssignmentExpression_init(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitAssignmentExpression_init(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AssignmentExpression_initContext assignmentExpression_init() throws RecognitionException {
		AssignmentExpression_initContext _localctx = new AssignmentExpression_initContext(_ctx, getState());
		enterRule(_localctx, 124, RULE_assignmentExpression_init);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(717);
			((AssignmentExpression_initContext)_localctx).left = identifier();
			setState(718);
			((AssignmentExpression_initContext)_localctx).operator = match(Assign);
			setState(719);
			((AssignmentExpression_initContext)_localctx).right = expression_single(0);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Expression_forInitContext extends ParserRuleContext {
		public Expression_forInitContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expression_forInit; }
	 
		public Expression_forInitContext() { }
		public void copyFrom(Expression_forInitContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SequenceExpression_initContext extends Expression_forInitContext {
		public InitExpressions_Context expressions;
		public InitExpressions_Context initExpressions_() {
			return getRuleContext(InitExpressions_Context.class,0);
		}
		public SequenceExpression_initContext(Expression_forInitContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterSequenceExpression_init(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitSequenceExpression_init(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitSequenceExpression_init(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class VariableDeclaration_initContext extends Expression_forInitContext {
		public VarModifier_Context kind;
		public VariableDeclarators_Context declarators;
		public VarModifier_Context varModifier_() {
			return getRuleContext(VarModifier_Context.class,0);
		}
		public VariableDeclarators_Context variableDeclarators_() {
			return getRuleContext(VariableDeclarators_Context.class,0);
		}
		public VariableDeclaration_initContext(Expression_forInitContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterVariableDeclaration_init(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitVariableDeclaration_init(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitVariableDeclaration_init(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Expression_forInitContext expression_forInit() throws RecognitionException {
		Expression_forInitContext _localctx = new Expression_forInitContext(_ctx, getState());
		enterRule(_localctx, 126, RULE_expression_forInit);
		try {
			setState(725);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case From:
			case TypeAlias:
			case Identifier:
				_localctx = new SequenceExpression_initContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(721);
				((SequenceExpression_initContext)_localctx).expressions = initExpressions_();
				}
				break;
			case Const:
			case Let:
				_localctx = new VariableDeclaration_initContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(722);
				((VariableDeclaration_initContext)_localctx).kind = varModifier_();
				setState(723);
				((VariableDeclaration_initContext)_localctx).declarators = variableDeclarators_();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class InitExpressions_Context extends ParserRuleContext {
		public AssignmentExpression_initContext e;
		public List<AssignmentExpression_initContext> assignmentExpression_init() {
			return getRuleContexts(AssignmentExpression_initContext.class);
		}
		public AssignmentExpression_initContext assignmentExpression_init(int i) {
			return getRuleContext(AssignmentExpression_initContext.class,i);
		}
		public List<TerminalNode> Comma() { return getTokens(XLangParser.Comma); }
		public TerminalNode Comma(int i) {
			return getToken(XLangParser.Comma, i);
		}
		public InitExpressions_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_initExpressions_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterInitExpressions_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitInitExpressions_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitInitExpressions_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final InitExpressions_Context initExpressions_() throws RecognitionException {
		InitExpressions_Context _localctx = new InitExpressions_Context(_ctx, getState());
		enterRule(_localctx, 128, RULE_initExpressions_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(727);
			((InitExpressions_Context)_localctx).e = assignmentExpression_init();
			setState(732);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==Comma) {
				{
				{
				setState(728);
				match(Comma);
				setState(729);
				((InitExpressions_Context)_localctx).e = assignmentExpression_init();
				}
				}
				setState(734);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Expression_singleContext extends ParserRuleContext {
		public Expression_singleContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expression_single; }
	 
		public Expression_singleContext() { }
		public void copyFrom(Expression_singleContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class TemplateStringExpressionContext extends Expression_singleContext {
		public IdentifierContext id;
		public TemplateStringLiteralContext value;
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TemplateStringLiteralContext templateStringLiteral() {
			return getRuleContext(TemplateStringLiteralContext.class,0);
		}
		public TemplateStringExpressionContext(Expression_singleContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterTemplateStringExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitTemplateStringExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitTemplateStringExpression(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class MacroExpressionContext extends Expression_singleContext {
		public Expression_singleContext expr;
		public TerminalNode CpExprStart() { return getToken(XLangParser.CpExprStart, 0); }
		public TerminalNode CloseBrace() { return getToken(XLangParser.CloseBrace, 0); }
		public Expression_singleContext expression_single() {
			return getRuleContext(Expression_singleContext.class,0);
		}
		public MacroExpressionContext(Expression_singleContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterMacroExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitMacroExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitMacroExpression(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ChainExpressionContext extends Expression_singleContext {
		public Expression_singleContext expr;
		public Token optional;
		public Expression_singleContext expression_single() {
			return getRuleContext(Expression_singleContext.class,0);
		}
		public TerminalNode Not() { return getToken(XLangParser.Not, 0); }
		public ChainExpressionContext(Expression_singleContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterChainExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitChainExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitChainExpression(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class TypeOfExpressionContext extends Expression_singleContext {
		public Expression_singleContext argument;
		public TerminalNode Typeof() { return getToken(XLangParser.Typeof, 0); }
		public Expression_singleContext expression_single() {
			return getRuleContext(Expression_singleContext.class,0);
		}
		public TypeOfExpressionContext(Expression_singleContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterTypeOfExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitTypeOfExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitTypeOfExpression(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ObjectExpression_exprContext extends Expression_singleContext {
		public ObjectExpressionContext objectExpression() {
			return getRuleContext(ObjectExpressionContext.class,0);
		}
		public ObjectExpression_exprContext(Expression_singleContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterObjectExpression_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitObjectExpression_expr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitObjectExpression_expr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class NewExpressionContext extends Expression_singleContext {
		public ParameterizedTypeNodeContext callee;
		public Arguments_Context arguments;
		public TerminalNode New() { return getToken(XLangParser.New, 0); }
		public ParameterizedTypeNodeContext parameterizedTypeNode() {
			return getRuleContext(ParameterizedTypeNodeContext.class,0);
		}
		public Arguments_Context arguments_() {
			return getRuleContext(Arguments_Context.class,0);
		}
		public NewExpressionContext(Expression_singleContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterNewExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitNewExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitNewExpression(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class InExpressionContext extends Expression_singleContext {
		public Expression_singleContext left;
		public Expression_singleContext right;
		public TerminalNode In() { return getToken(XLangParser.In, 0); }
		public List<Expression_singleContext> expression_single() {
			return getRuleContexts(Expression_singleContext.class);
		}
		public Expression_singleContext expression_single(int i) {
			return getRuleContext(Expression_singleContext.class,i);
		}
		public InExpressionContext(Expression_singleContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterInExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitInExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitInExpression(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class Identifier_exprContext extends Expression_singleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public Identifier_exprContext(Expression_singleContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterIdentifier_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitIdentifier_expr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitIdentifier_expr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ArrayExpression_exprContext extends Expression_singleContext {
		public ArrayExpressionContext arrayExpression() {
			return getRuleContext(ArrayExpressionContext.class,0);
		}
		public ArrayExpression_exprContext(Expression_singleContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterArrayExpression_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitArrayExpression_expr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitArrayExpression_expr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class UnaryExpressionContext extends Expression_singleContext {
		public Token operator;
		public Expression_singleContext argument;
		public TerminalNode Plus() { return getToken(XLangParser.Plus, 0); }
		public Expression_singleContext expression_single() {
			return getRuleContext(Expression_singleContext.class,0);
		}
		public TerminalNode Minus() { return getToken(XLangParser.Minus, 0); }
		public TerminalNode BitNot() { return getToken(XLangParser.BitNot, 0); }
		public TerminalNode Not() { return getToken(XLangParser.Not, 0); }
		public UnaryExpressionContext(Expression_singleContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterUnaryExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitUnaryExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitUnaryExpression(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class Literal_exprContext extends Expression_singleContext {
		public LiteralContext literal() {
			return getRuleContext(LiteralContext.class,0);
		}
		public Literal_exprContext(Expression_singleContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterLiteral_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitLiteral_expr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitLiteral_expr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class DeleteStatementContext extends Expression_singleContext {
		public Expression_singleContext argument;
		public TerminalNode Delete() { return getToken(XLangParser.Delete, 0); }
		public Expression_singleContext expression_single() {
			return getRuleContext(Expression_singleContext.class,0);
		}
		public DeleteStatementContext(Expression_singleContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterDeleteStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitDeleteStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitDeleteStatement(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ThisExpressionContext extends Expression_singleContext {
		public TerminalNode This() { return getToken(XLangParser.This, 0); }
		public ThisExpressionContext(Expression_singleContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterThisExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitThisExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitThisExpression(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class BinaryExpressionContext extends Expression_singleContext {
		public Expression_singleContext left;
		public Token operator;
		public Expression_singleContext right;
		public List<Expression_singleContext> expression_single() {
			return getRuleContexts(Expression_singleContext.class);
		}
		public Expression_singleContext expression_single(int i) {
			return getRuleContext(Expression_singleContext.class,i);
		}
		public TerminalNode Multiply() { return getToken(XLangParser.Multiply, 0); }
		public TerminalNode Divide() { return getToken(XLangParser.Divide, 0); }
		public TerminalNode Modulus() { return getToken(XLangParser.Modulus, 0); }
		public TerminalNode Plus() { return getToken(XLangParser.Plus, 0); }
		public TerminalNode Minus() { return getToken(XLangParser.Minus, 0); }
		public TerminalNode NullCoalesce() { return getToken(XLangParser.NullCoalesce, 0); }
		public TerminalNode LeftShiftArithmetic() { return getToken(XLangParser.LeftShiftArithmetic, 0); }
		public TerminalNode RightShiftArithmetic() { return getToken(XLangParser.RightShiftArithmetic, 0); }
		public TerminalNode RightShiftLogical() { return getToken(XLangParser.RightShiftLogical, 0); }
		public TerminalNode LessThan() { return getToken(XLangParser.LessThan, 0); }
		public TerminalNode MoreThan() { return getToken(XLangParser.MoreThan, 0); }
		public TerminalNode LessThanEquals() { return getToken(XLangParser.LessThanEquals, 0); }
		public TerminalNode GreaterThanEquals() { return getToken(XLangParser.GreaterThanEquals, 0); }
		public TerminalNode Equals_() { return getToken(XLangParser.Equals_, 0); }
		public TerminalNode NotEquals() { return getToken(XLangParser.NotEquals, 0); }
		public TerminalNode IdentityEquals() { return getToken(XLangParser.IdentityEquals, 0); }
		public TerminalNode IdentityNotEquals() { return getToken(XLangParser.IdentityNotEquals, 0); }
		public TerminalNode BitAnd() { return getToken(XLangParser.BitAnd, 0); }
		public TerminalNode BitXOr() { return getToken(XLangParser.BitXOr, 0); }
		public TerminalNode BitOr() { return getToken(XLangParser.BitOr, 0); }
		public TerminalNode And() { return getToken(XLangParser.And, 0); }
		public TerminalNode AndLiteral() { return getToken(XLangParser.AndLiteral, 0); }
		public TerminalNode Or() { return getToken(XLangParser.Or, 0); }
		public TerminalNode OrLiteral() { return getToken(XLangParser.OrLiteral, 0); }
		public BinaryExpressionContext(Expression_singleContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterBinaryExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitBinaryExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitBinaryExpression(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class IfStatement_exprContext extends Expression_singleContext {
		public Expression_singleContext test;
		public Expression_singleContext consequent;
		public Expression_singleContext alternate;
		public TerminalNode Question() { return getToken(XLangParser.Question, 0); }
		public TerminalNode Colon() { return getToken(XLangParser.Colon, 0); }
		public List<Expression_singleContext> expression_single() {
			return getRuleContexts(Expression_singleContext.class);
		}
		public Expression_singleContext expression_single(int i) {
			return getRuleContext(Expression_singleContext.class,i);
		}
		public IfStatement_exprContext(Expression_singleContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterIfStatement_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitIfStatement_expr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitIfStatement_expr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class InstanceOfExpressionContext extends Expression_singleContext {
		public Expression_singleContext value;
		public NamedTypeNodeContext refType;
		public TerminalNode Instanceof() { return getToken(XLangParser.Instanceof, 0); }
		public Expression_singleContext expression_single() {
			return getRuleContext(Expression_singleContext.class,0);
		}
		public NamedTypeNodeContext namedTypeNode() {
			return getRuleContext(NamedTypeNodeContext.class,0);
		}
		public InstanceOfExpressionContext(Expression_singleContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterInstanceOfExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitInstanceOfExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitInstanceOfExpression(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class CastExpressionContext extends Expression_singleContext {
		public Expression_singleContext value;
		public NamedTypeNodeContext asType;
		public TerminalNode As() { return getToken(XLangParser.As, 0); }
		public Expression_singleContext expression_single() {
			return getRuleContext(Expression_singleContext.class,0);
		}
		public NamedTypeNodeContext namedTypeNode() {
			return getRuleContext(NamedTypeNodeContext.class,0);
		}
		public CastExpressionContext(Expression_singleContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterCastExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitCastExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitCastExpression(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ArrowFunctionExpression_exprContext extends Expression_singleContext {
		public ArrowFunctionExpressionContext arrowFunctionExpression() {
			return getRuleContext(ArrowFunctionExpressionContext.class,0);
		}
		public ArrowFunctionExpression_exprContext(Expression_singleContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterArrowFunctionExpression_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitArrowFunctionExpression_expr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitArrowFunctionExpression_expr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class MemberExpression_dotContext extends Expression_singleContext {
		public Expression_singleContext object;
		public Token optional;
		public Identifier_exContext property;
		public Expression_singleContext expression_single() {
			return getRuleContext(Expression_singleContext.class,0);
		}
		public Identifier_exContext identifier_ex() {
			return getRuleContext(Identifier_exContext.class,0);
		}
		public TerminalNode OptionalDot() { return getToken(XLangParser.OptionalDot, 0); }
		public TerminalNode Dot() { return getToken(XLangParser.Dot, 0); }
		public MemberExpression_dotContext(Expression_singleContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterMemberExpression_dot(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitMemberExpression_dot(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitMemberExpression_dot(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class UpdateExpressionContext extends Expression_singleContext {
		public Expression_singleContext argument;
		public Token operator;
		public TerminalNode PlusPlus() { return getToken(XLangParser.PlusPlus, 0); }
		public Expression_singleContext expression_single() {
			return getRuleContext(Expression_singleContext.class,0);
		}
		public TerminalNode MinusMinus() { return getToken(XLangParser.MinusMinus, 0); }
		public UpdateExpressionContext(Expression_singleContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterUpdateExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitUpdateExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitUpdateExpression(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SuperExpressionContext extends Expression_singleContext {
		public TerminalNode Super() { return getToken(XLangParser.Super, 0); }
		public SuperExpressionContext(Expression_singleContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterSuperExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitSuperExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitSuperExpression(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class CallExpressionContext extends Expression_singleContext {
		public Expression_singleContext callee;
		public Token optional;
		public Arguments_Context arguments;
		public Expression_singleContext expression_single() {
			return getRuleContext(Expression_singleContext.class,0);
		}
		public Arguments_Context arguments_() {
			return getRuleContext(Arguments_Context.class,0);
		}
		public TerminalNode OptionalDot() { return getToken(XLangParser.OptionalDot, 0); }
		public CallExpressionContext(Expression_singleContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterCallExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitCallExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitCallExpression(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class BraceExpressionContext extends Expression_singleContext {
		public Expression_singleContext expr;
		public TerminalNode OpenParen() { return getToken(XLangParser.OpenParen, 0); }
		public TerminalNode CloseParen() { return getToken(XLangParser.CloseParen, 0); }
		public Expression_singleContext expression_single() {
			return getRuleContext(Expression_singleContext.class,0);
		}
		public BraceExpressionContext(Expression_singleContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterBraceExpression(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitBraceExpression(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitBraceExpression(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class MemberExpression_indexContext extends Expression_singleContext {
		public Expression_singleContext object;
		public Token optional;
		public Expression_singleContext property;
		public TerminalNode OpenBracket() { return getToken(XLangParser.OpenBracket, 0); }
		public TerminalNode CloseBracket() { return getToken(XLangParser.CloseBracket, 0); }
		public List<Expression_singleContext> expression_single() {
			return getRuleContexts(Expression_singleContext.class);
		}
		public Expression_singleContext expression_single(int i) {
			return getRuleContext(Expression_singleContext.class,i);
		}
		public TerminalNode OptionalDot() { return getToken(XLangParser.OptionalDot, 0); }
		public MemberExpression_indexContext(Expression_singleContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterMemberExpression_index(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitMemberExpression_index(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitMemberExpression_index(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Expression_singleContext expression_single() throws RecognitionException {
		return expression_single(0);
	}

	private Expression_singleContext expression_single(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		Expression_singleContext _localctx = new Expression_singleContext(_ctx, _parentState);
		Expression_singleContext _prevctx = _localctx;
		int _startState = 130;
		enterRecursionRule(_localctx, 130, RULE_expression_single, _p);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(777);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,67,_ctx) ) {
			case 1:
				{
				_localctx = new ArrowFunctionExpression_exprContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;

				setState(736);
				arrowFunctionExpression();
				}
				break;
			case 2:
				{
				_localctx = new NewExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(737);
				match(New);
				setState(738);
				((NewExpressionContext)_localctx).callee = parameterizedTypeNode();
				setState(740);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,66,_ctx) ) {
				case 1:
					{
					setState(739);
					((NewExpressionContext)_localctx).arguments = arguments_();
					}
					break;
				}
				}
				break;
			case 3:
				{
				_localctx = new DeleteStatementContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(742);
				match(Delete);
				setState(743);
				((DeleteStatementContext)_localctx).argument = expression_single(32);
				}
				break;
			case 4:
				{
				_localctx = new TypeOfExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(744);
				match(Typeof);
				setState(745);
				((TypeOfExpressionContext)_localctx).argument = expression_single(31);
				}
				break;
			case 5:
				{
				_localctx = new UpdateExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(746);
				((UpdateExpressionContext)_localctx).operator = match(PlusPlus);
				setState(747);
				((UpdateExpressionContext)_localctx).argument = expression_single(30);
				}
				break;
			case 6:
				{
				_localctx = new UpdateExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(748);
				((UpdateExpressionContext)_localctx).operator = match(MinusMinus);
				setState(749);
				((UpdateExpressionContext)_localctx).argument = expression_single(29);
				}
				break;
			case 7:
				{
				_localctx = new UnaryExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(750);
				((UnaryExpressionContext)_localctx).operator = match(Plus);
				setState(751);
				((UnaryExpressionContext)_localctx).argument = expression_single(28);
				}
				break;
			case 8:
				{
				_localctx = new UnaryExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(752);
				((UnaryExpressionContext)_localctx).operator = match(Minus);
				setState(753);
				((UnaryExpressionContext)_localctx).argument = expression_single(27);
				}
				break;
			case 9:
				{
				_localctx = new UnaryExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(754);
				((UnaryExpressionContext)_localctx).operator = match(BitNot);
				setState(755);
				((UnaryExpressionContext)_localctx).argument = expression_single(26);
				}
				break;
			case 10:
				{
				_localctx = new UnaryExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(756);
				((UnaryExpressionContext)_localctx).operator = match(Not);
				setState(757);
				((UnaryExpressionContext)_localctx).argument = expression_single(25);
				}
				break;
			case 11:
				{
				_localctx = new TemplateStringExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(758);
				((TemplateStringExpressionContext)_localctx).id = identifier();
				setState(759);
				if (!(this.notLineTerminator())) throw new FailedPredicateException(this, "this.notLineTerminator()");
				setState(760);
				((TemplateStringExpressionContext)_localctx).value = templateStringLiteral();
				}
				break;
			case 12:
				{
				_localctx = new ThisExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(762);
				match(This);
				}
				break;
			case 13:
				{
				_localctx = new Literal_exprContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(763);
				literal();
				}
				break;
			case 14:
				{
				_localctx = new SuperExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(764);
				match(Super);
				}
				break;
			case 15:
				{
				_localctx = new Identifier_exprContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(765);
				identifier();
				}
				break;
			case 16:
				{
				_localctx = new ArrayExpression_exprContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(766);
				arrayExpression();
				}
				break;
			case 17:
				{
				_localctx = new ObjectExpression_exprContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(767);
				objectExpression();
				}
				break;
			case 18:
				{
				_localctx = new BraceExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(768);
				match(OpenParen);
				setState(769);
				((BraceExpressionContext)_localctx).expr = expression_single(0);
				setState(770);
				match(CloseParen);
				}
				break;
			case 19:
				{
				_localctx = new MacroExpressionContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(772);
				match(CpExprStart);
				setState(773);
				((MacroExpressionContext)_localctx).expr = expression_single(0);
				setState(774);
				match(CloseBrace);
				setState(775);
				if (!(this.supportCpExpr())) throw new FailedPredicateException(this, "this.supportCpExpr()");
				}
				break;
			}
			_ctx.stop = _input.LT(-1);
			setState(854);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,71,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(852);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,70,_ctx) ) {
					case 1:
						{
						_localctx = new BinaryExpressionContext(new Expression_singleContext(_parentctx, _parentState));
						((BinaryExpressionContext)_localctx).left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression_single);
						setState(779);
						if (!(precpred(_ctx, 24))) throw new FailedPredicateException(this, "precpred(_ctx, 24)");
						setState(780);
						((BinaryExpressionContext)_localctx).operator = _input.LT(1);
						_la = _input.LA(1);
						if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 469762048L) != 0)) ) {
							((BinaryExpressionContext)_localctx).operator = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(781);
						((BinaryExpressionContext)_localctx).right = expression_single(25);
						}
						break;
					case 2:
						{
						_localctx = new BinaryExpressionContext(new Expression_singleContext(_parentctx, _parentState));
						((BinaryExpressionContext)_localctx).left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression_single);
						setState(782);
						if (!(precpred(_ctx, 23))) throw new FailedPredicateException(this, "precpred(_ctx, 23)");
						setState(783);
						((BinaryExpressionContext)_localctx).operator = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==Plus || _la==Minus) ) {
							((BinaryExpressionContext)_localctx).operator = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(784);
						((BinaryExpressionContext)_localctx).right = expression_single(24);
						}
						break;
					case 3:
						{
						_localctx = new BinaryExpressionContext(new Expression_singleContext(_parentctx, _parentState));
						((BinaryExpressionContext)_localctx).left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression_single);
						setState(785);
						if (!(precpred(_ctx, 22))) throw new FailedPredicateException(this, "precpred(_ctx, 22)");
						setState(786);
						((BinaryExpressionContext)_localctx).operator = match(NullCoalesce);
						setState(787);
						((BinaryExpressionContext)_localctx).right = expression_single(23);
						}
						break;
					case 4:
						{
						_localctx = new BinaryExpressionContext(new Expression_singleContext(_parentctx, _parentState));
						((BinaryExpressionContext)_localctx).left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression_single);
						setState(788);
						if (!(precpred(_ctx, 21))) throw new FailedPredicateException(this, "precpred(_ctx, 21)");
						setState(789);
						((BinaryExpressionContext)_localctx).operator = _input.LT(1);
						_la = _input.LA(1);
						if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 3758096384L) != 0)) ) {
							((BinaryExpressionContext)_localctx).operator = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(790);
						((BinaryExpressionContext)_localctx).right = expression_single(22);
						}
						break;
					case 5:
						{
						_localctx = new BinaryExpressionContext(new Expression_singleContext(_parentctx, _parentState));
						((BinaryExpressionContext)_localctx).left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression_single);
						setState(791);
						if (!(precpred(_ctx, 20))) throw new FailedPredicateException(this, "precpred(_ctx, 20)");
						setState(792);
						((BinaryExpressionContext)_localctx).operator = _input.LT(1);
						_la = _input.LA(1);
						if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 64424509440L) != 0)) ) {
							((BinaryExpressionContext)_localctx).operator = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(793);
						((BinaryExpressionContext)_localctx).right = expression_single(21);
						}
						break;
					case 6:
						{
						_localctx = new InExpressionContext(new Expression_singleContext(_parentctx, _parentState));
						((InExpressionContext)_localctx).left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression_single);
						setState(794);
						if (!(precpred(_ctx, 18))) throw new FailedPredicateException(this, "precpred(_ctx, 18)");
						setState(795);
						match(In);
						setState(796);
						((InExpressionContext)_localctx).right = expression_single(19);
						}
						break;
					case 7:
						{
						_localctx = new BinaryExpressionContext(new Expression_singleContext(_parentctx, _parentState));
						((BinaryExpressionContext)_localctx).left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression_single);
						setState(797);
						if (!(precpred(_ctx, 17))) throw new FailedPredicateException(this, "precpred(_ctx, 17)");
						setState(798);
						((BinaryExpressionContext)_localctx).operator = _input.LT(1);
						_la = _input.LA(1);
						if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 1030792151040L) != 0)) ) {
							((BinaryExpressionContext)_localctx).operator = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(799);
						((BinaryExpressionContext)_localctx).right = expression_single(18);
						}
						break;
					case 8:
						{
						_localctx = new BinaryExpressionContext(new Expression_singleContext(_parentctx, _parentState));
						((BinaryExpressionContext)_localctx).left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression_single);
						setState(800);
						if (!(precpred(_ctx, 16))) throw new FailedPredicateException(this, "precpred(_ctx, 16)");
						setState(801);
						((BinaryExpressionContext)_localctx).operator = match(BitAnd);
						setState(802);
						((BinaryExpressionContext)_localctx).right = expression_single(17);
						}
						break;
					case 9:
						{
						_localctx = new BinaryExpressionContext(new Expression_singleContext(_parentctx, _parentState));
						((BinaryExpressionContext)_localctx).left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression_single);
						setState(803);
						if (!(precpred(_ctx, 15))) throw new FailedPredicateException(this, "precpred(_ctx, 15)");
						setState(804);
						((BinaryExpressionContext)_localctx).operator = match(BitXOr);
						setState(805);
						((BinaryExpressionContext)_localctx).right = expression_single(16);
						}
						break;
					case 10:
						{
						_localctx = new BinaryExpressionContext(new Expression_singleContext(_parentctx, _parentState));
						((BinaryExpressionContext)_localctx).left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression_single);
						setState(806);
						if (!(precpred(_ctx, 14))) throw new FailedPredicateException(this, "precpred(_ctx, 14)");
						setState(807);
						((BinaryExpressionContext)_localctx).operator = match(BitOr);
						setState(808);
						((BinaryExpressionContext)_localctx).right = expression_single(15);
						}
						break;
					case 11:
						{
						_localctx = new BinaryExpressionContext(new Expression_singleContext(_parentctx, _parentState));
						((BinaryExpressionContext)_localctx).left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression_single);
						setState(809);
						if (!(precpred(_ctx, 13))) throw new FailedPredicateException(this, "precpred(_ctx, 13)");
						setState(810);
						((BinaryExpressionContext)_localctx).operator = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==And || _la==AndLiteral) ) {
							((BinaryExpressionContext)_localctx).operator = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(811);
						((BinaryExpressionContext)_localctx).right = expression_single(14);
						}
						break;
					case 12:
						{
						_localctx = new BinaryExpressionContext(new Expression_singleContext(_parentctx, _parentState));
						((BinaryExpressionContext)_localctx).left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression_single);
						setState(812);
						if (!(precpred(_ctx, 12))) throw new FailedPredicateException(this, "precpred(_ctx, 12)");
						setState(813);
						((BinaryExpressionContext)_localctx).operator = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==Or || _la==OrLiteral) ) {
							((BinaryExpressionContext)_localctx).operator = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(814);
						((BinaryExpressionContext)_localctx).right = expression_single(13);
						}
						break;
					case 13:
						{
						_localctx = new IfStatement_exprContext(new Expression_singleContext(_parentctx, _parentState));
						((IfStatement_exprContext)_localctx).test = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression_single);
						setState(815);
						if (!(precpred(_ctx, 11))) throw new FailedPredicateException(this, "precpred(_ctx, 11)");
						setState(816);
						match(Question);
						setState(817);
						((IfStatement_exprContext)_localctx).consequent = expression_single(0);
						setState(818);
						match(Colon);
						setState(819);
						((IfStatement_exprContext)_localctx).alternate = expression_single(12);
						}
						break;
					case 14:
						{
						_localctx = new MemberExpression_indexContext(new Expression_singleContext(_parentctx, _parentState));
						((MemberExpression_indexContext)_localctx).object = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression_single);
						setState(821);
						if (!(precpred(_ctx, 39))) throw new FailedPredicateException(this, "precpred(_ctx, 39)");
						setState(823);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==OptionalDot) {
							{
							setState(822);
							((MemberExpression_indexContext)_localctx).optional = match(OptionalDot);
							}
						}

						setState(825);
						match(OpenBracket);
						setState(826);
						((MemberExpression_indexContext)_localctx).property = expression_single(0);
						setState(827);
						match(CloseBracket);
						}
						break;
					case 15:
						{
						_localctx = new MemberExpression_dotContext(new Expression_singleContext(_parentctx, _parentState));
						((MemberExpression_dotContext)_localctx).object = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression_single);
						setState(829);
						if (!(precpred(_ctx, 38))) throw new FailedPredicateException(this, "precpred(_ctx, 38)");
						setState(830);
						((MemberExpression_dotContext)_localctx).optional = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==OptionalDot || _la==Dot) ) {
							((MemberExpression_dotContext)_localctx).optional = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(831);
						((MemberExpression_dotContext)_localctx).property = identifier_ex();
						}
						break;
					case 16:
						{
						_localctx = new ChainExpressionContext(new Expression_singleContext(_parentctx, _parentState));
						((ChainExpressionContext)_localctx).expr = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression_single);
						setState(832);
						if (!(precpred(_ctx, 37))) throw new FailedPredicateException(this, "precpred(_ctx, 37)");
						setState(833);
						if (!(this.notLineTerminator())) throw new FailedPredicateException(this, "this.notLineTerminator()");
						setState(834);
						((ChainExpressionContext)_localctx).optional = match(Not);
						}
						break;
					case 17:
						{
						_localctx = new CallExpressionContext(new Expression_singleContext(_parentctx, _parentState));
						((CallExpressionContext)_localctx).callee = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression_single);
						setState(835);
						if (!(precpred(_ctx, 35))) throw new FailedPredicateException(this, "precpred(_ctx, 35)");
						setState(837);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==OptionalDot) {
							{
							setState(836);
							((CallExpressionContext)_localctx).optional = match(OptionalDot);
							}
						}

						setState(839);
						((CallExpressionContext)_localctx).arguments = arguments_();
						}
						break;
					case 18:
						{
						_localctx = new UpdateExpressionContext(new Expression_singleContext(_parentctx, _parentState));
						((UpdateExpressionContext)_localctx).argument = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression_single);
						setState(840);
						if (!(precpred(_ctx, 34))) throw new FailedPredicateException(this, "precpred(_ctx, 34)");
						setState(841);
						if (!(this.notLineTerminator())) throw new FailedPredicateException(this, "this.notLineTerminator()");
						setState(842);
						((UpdateExpressionContext)_localctx).operator = match(PlusPlus);
						}
						break;
					case 19:
						{
						_localctx = new UpdateExpressionContext(new Expression_singleContext(_parentctx, _parentState));
						((UpdateExpressionContext)_localctx).argument = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression_single);
						setState(843);
						if (!(precpred(_ctx, 33))) throw new FailedPredicateException(this, "precpred(_ctx, 33)");
						setState(844);
						if (!(this.notLineTerminator())) throw new FailedPredicateException(this, "this.notLineTerminator()");
						setState(845);
						((UpdateExpressionContext)_localctx).operator = match(MinusMinus);
						}
						break;
					case 20:
						{
						_localctx = new InstanceOfExpressionContext(new Expression_singleContext(_parentctx, _parentState));
						((InstanceOfExpressionContext)_localctx).value = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression_single);
						setState(846);
						if (!(precpred(_ctx, 19))) throw new FailedPredicateException(this, "precpred(_ctx, 19)");
						setState(847);
						match(Instanceof);
						setState(848);
						((InstanceOfExpressionContext)_localctx).refType = namedTypeNode(0);
						}
						break;
					case 21:
						{
						_localctx = new CastExpressionContext(new Expression_singleContext(_parentctx, _parentState));
						((CastExpressionContext)_localctx).value = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression_single);
						setState(849);
						if (!(precpred(_ctx, 1))) throw new FailedPredicateException(this, "precpred(_ctx, 1)");
						setState(850);
						match(As);
						setState(851);
						((CastExpressionContext)_localctx).asType = namedTypeNode(0);
						}
						break;
					}
					} 
				}
				setState(856);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,71,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			unrollRecursionContexts(_parentctx);
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class TemplateStringLiteralContext extends ParserRuleContext {
		public Token value;
		public TerminalNode TemplateStringLiteral() { return getToken(XLangParser.TemplateStringLiteral, 0); }
		public TemplateStringLiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_templateStringLiteral; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterTemplateStringLiteral(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitTemplateStringLiteral(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitTemplateStringLiteral(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TemplateStringLiteralContext templateStringLiteral() throws RecognitionException {
		TemplateStringLiteralContext _localctx = new TemplateStringLiteralContext(_ctx, getState());
		enterRule(_localctx, 132, RULE_templateStringLiteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(857);
			((TemplateStringLiteralContext)_localctx).value = match(TemplateStringLiteral);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ArrowFunctionExpressionContext extends ParserRuleContext {
		public ArrowFunctionExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arrowFunctionExpression; }
	 
		public ArrowFunctionExpressionContext() { }
		public void copyFrom(ArrowFunctionExpressionContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ArrowFunctionExpression_singleContext extends ArrowFunctionExpressionContext {
		public ParameterDeclaration_simpleContext params_single;
		public Expression_functionBodyContext body;
		public TerminalNode Arrow() { return getToken(XLangParser.Arrow, 0); }
		public ParameterDeclaration_simpleContext parameterDeclaration_simple() {
			return getRuleContext(ParameterDeclaration_simpleContext.class,0);
		}
		public Expression_functionBodyContext expression_functionBody() {
			return getRuleContext(Expression_functionBodyContext.class,0);
		}
		public ArrowFunctionExpression_singleContext(ArrowFunctionExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterArrowFunctionExpression_single(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitArrowFunctionExpression_single(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitArrowFunctionExpression_single(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ArrowFunctionExpression_fullContext extends ArrowFunctionExpressionContext {
		public ParameterList_Context params;
		public NamedTypeNode_annotationContext returnType;
		public Expression_functionBodyContext body;
		public TerminalNode Arrow() { return getToken(XLangParser.Arrow, 0); }
		public Expression_functionBodyContext expression_functionBody() {
			return getRuleContext(Expression_functionBodyContext.class,0);
		}
		public TerminalNode OpenParen() { return getToken(XLangParser.OpenParen, 0); }
		public TerminalNode CloseParen() { return getToken(XLangParser.CloseParen, 0); }
		public ParameterList_Context parameterList_() {
			return getRuleContext(ParameterList_Context.class,0);
		}
		public NamedTypeNode_annotationContext namedTypeNode_annotation() {
			return getRuleContext(NamedTypeNode_annotationContext.class,0);
		}
		public ArrowFunctionExpression_fullContext(ArrowFunctionExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterArrowFunctionExpression_full(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitArrowFunctionExpression_full(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitArrowFunctionExpression_full(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ArrowFunctionExpressionContext arrowFunctionExpression() throws RecognitionException {
		ArrowFunctionExpressionContext _localctx = new ArrowFunctionExpressionContext(_ctx, getState());
		enterRule(_localctx, 134, RULE_arrowFunctionExpression);
		int _la;
		try {
			setState(873);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case OpenParen:
				_localctx = new ArrowFunctionExpression_fullContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				{
				setState(859);
				match(OpenParen);
				setState(861);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==OpenBracket || _la==OpenBrace || ((((_la - 92)) & ~0x3f) == 0 && ((1L << (_la - 92)) & 1224736769L) != 0)) {
					{
					setState(860);
					((ArrowFunctionExpression_fullContext)_localctx).params = parameterList_();
					}
				}

				setState(863);
				match(CloseParen);
				setState(865);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==Colon) {
					{
					setState(864);
					((ArrowFunctionExpression_fullContext)_localctx).returnType = namedTypeNode_annotation();
					}
				}

				}
				setState(867);
				match(Arrow);
				setState(868);
				((ArrowFunctionExpression_fullContext)_localctx).body = expression_functionBody();
				}
				break;
			case From:
			case TypeAlias:
			case Identifier:
				_localctx = new ArrowFunctionExpression_singleContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(869);
				((ArrowFunctionExpression_singleContext)_localctx).params_single = parameterDeclaration_simple();
				setState(870);
				match(Arrow);
				setState(871);
				((ArrowFunctionExpression_singleContext)_localctx).body = expression_functionBody();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ParameterDeclaration_simpleContext extends ParserRuleContext {
		public IdentifierContext name;
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public ParameterDeclaration_simpleContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_parameterDeclaration_simple; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterParameterDeclaration_simple(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitParameterDeclaration_simple(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitParameterDeclaration_simple(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ParameterDeclaration_simpleContext parameterDeclaration_simple() throws RecognitionException {
		ParameterDeclaration_simpleContext _localctx = new ParameterDeclaration_simpleContext(_ctx, getState());
		enterRule(_localctx, 136, RULE_parameterDeclaration_simple);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(875);
			((ParameterDeclaration_simpleContext)_localctx).name = identifier();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Expression_functionBodyContext extends ParserRuleContext {
		public Expression_singleContext expression_single() {
			return getRuleContext(Expression_singleContext.class,0);
		}
		public BlockStatementContext blockStatement() {
			return getRuleContext(BlockStatementContext.class,0);
		}
		public Expression_functionBodyContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expression_functionBody; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterExpression_functionBody(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitExpression_functionBody(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitExpression_functionBody(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Expression_functionBodyContext expression_functionBody() throws RecognitionException {
		Expression_functionBodyContext _localctx = new Expression_functionBodyContext(_ctx, getState());
		enterRule(_localctx, 138, RULE_expression_functionBody);
		try {
			setState(879);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,75,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(877);
				expression_single(0);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(878);
				blockStatement();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class MemberExpressionContext extends ParserRuleContext {
		public MemberExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_memberExpression; }
	 
		public MemberExpressionContext() { }
		public void copyFrom(MemberExpressionContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class MemberExpression_index2Context extends MemberExpressionContext {
		public Expression_singleContext object;
		public Expression_singleContext property;
		public TerminalNode OpenBracket() { return getToken(XLangParser.OpenBracket, 0); }
		public TerminalNode CloseBracket() { return getToken(XLangParser.CloseBracket, 0); }
		public List<Expression_singleContext> expression_single() {
			return getRuleContexts(Expression_singleContext.class);
		}
		public Expression_singleContext expression_single(int i) {
			return getRuleContext(Expression_singleContext.class,i);
		}
		public MemberExpression_index2Context(MemberExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterMemberExpression_index2(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitMemberExpression_index2(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitMemberExpression_index2(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class MemberExpression_dot2Context extends MemberExpressionContext {
		public Expression_singleContext object;
		public Identifier_exContext property;
		public TerminalNode Dot() { return getToken(XLangParser.Dot, 0); }
		public Expression_singleContext expression_single() {
			return getRuleContext(Expression_singleContext.class,0);
		}
		public Identifier_exContext identifier_ex() {
			return getRuleContext(Identifier_exContext.class,0);
		}
		public MemberExpression_dot2Context(MemberExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterMemberExpression_dot2(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitMemberExpression_dot2(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitMemberExpression_dot2(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MemberExpressionContext memberExpression() throws RecognitionException {
		MemberExpressionContext _localctx = new MemberExpressionContext(_ctx, getState());
		enterRule(_localctx, 140, RULE_memberExpression);
		try {
			setState(890);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,76,_ctx) ) {
			case 1:
				_localctx = new MemberExpression_index2Context(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(881);
				((MemberExpression_index2Context)_localctx).object = expression_single(0);
				setState(882);
				match(OpenBracket);
				setState(883);
				((MemberExpression_index2Context)_localctx).property = expression_single(0);
				setState(884);
				match(CloseBracket);
				}
				break;
			case 2:
				_localctx = new MemberExpression_dot2Context(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(886);
				((MemberExpression_dot2Context)_localctx).object = expression_single(0);
				setState(887);
				match(Dot);
				setState(888);
				((MemberExpression_dot2Context)_localctx).property = identifier_ex();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class AssignmentOperator_Context extends ParserRuleContext {
		public TerminalNode Assign() { return getToken(XLangParser.Assign, 0); }
		public TerminalNode MultiplyAssign() { return getToken(XLangParser.MultiplyAssign, 0); }
		public TerminalNode DivideAssign() { return getToken(XLangParser.DivideAssign, 0); }
		public TerminalNode ModulusAssign() { return getToken(XLangParser.ModulusAssign, 0); }
		public TerminalNode PlusAssign() { return getToken(XLangParser.PlusAssign, 0); }
		public TerminalNode MinusAssign() { return getToken(XLangParser.MinusAssign, 0); }
		public TerminalNode LeftShiftArithmeticAssign() { return getToken(XLangParser.LeftShiftArithmeticAssign, 0); }
		public TerminalNode RightShiftArithmeticAssign() { return getToken(XLangParser.RightShiftArithmeticAssign, 0); }
		public TerminalNode RightShiftLogicalAssign() { return getToken(XLangParser.RightShiftLogicalAssign, 0); }
		public TerminalNode BitAndAssign() { return getToken(XLangParser.BitAndAssign, 0); }
		public TerminalNode BitXorAssign() { return getToken(XLangParser.BitXorAssign, 0); }
		public TerminalNode BitOrAssign() { return getToken(XLangParser.BitOrAssign, 0); }
		public AssignmentOperator_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_assignmentOperator_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterAssignmentOperator_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitAssignmentOperator_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitAssignmentOperator_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AssignmentOperator_Context assignmentOperator_() throws RecognitionException {
		AssignmentOperator_Context _localctx = new AssignmentOperator_Context(_ctx, getState());
		enterRule(_localctx, 142, RULE_assignmentOperator_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(892);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 72022409665843200L) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Eos__Context extends ParserRuleContext {
		public TerminalNode SemiColon() { return getToken(XLangParser.SemiColon, 0); }
		public TerminalNode EOF() { return getToken(XLangParser.EOF, 0); }
		public Eos__Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_eos__; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterEos__(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitEos__(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitEos__(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Eos__Context eos__() throws RecognitionException {
		Eos__Context _localctx = new Eos__Context(_ctx, getState());
		enterRule(_localctx, 144, RULE_eos__);
		try {
			setState(898);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,77,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(894);
				match(SemiColon);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(895);
				match(EOF);
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(896);
				if (!(this.lineTerminatorAhead())) throw new FailedPredicateException(this, "this.lineTerminatorAhead()");
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(897);
				if (!(this.closeBrace())) throw new FailedPredicateException(this, "this.closeBrace()");
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class TypeParameters_Context extends ParserRuleContext {
		public TypeParameterNodeContext e;
		public TerminalNode LessThan() { return getToken(XLangParser.LessThan, 0); }
		public TerminalNode MoreThan() { return getToken(XLangParser.MoreThan, 0); }
		public List<TypeParameterNodeContext> typeParameterNode() {
			return getRuleContexts(TypeParameterNodeContext.class);
		}
		public TypeParameterNodeContext typeParameterNode(int i) {
			return getRuleContext(TypeParameterNodeContext.class,i);
		}
		public List<TerminalNode> Comma() { return getTokens(XLangParser.Comma); }
		public TerminalNode Comma(int i) {
			return getToken(XLangParser.Comma, i);
		}
		public TypeParameters_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_typeParameters_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterTypeParameters_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitTypeParameters_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitTypeParameters_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TypeParameters_Context typeParameters_() throws RecognitionException {
		TypeParameters_Context _localctx = new TypeParameters_Context(_ctx, getState());
		enterRule(_localctx, 146, RULE_typeParameters_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(900);
			match(LessThan);
			setState(909);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (((((_la - 92)) & ~0x3f) == 0 && ((1L << (_la - 92)) & 1090519041L) != 0)) {
				{
				setState(901);
				((TypeParameters_Context)_localctx).e = typeParameterNode();
				setState(906);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==Comma) {
					{
					{
					setState(902);
					match(Comma);
					setState(903);
					((TypeParameters_Context)_localctx).e = typeParameterNode();
					}
					}
					setState(908);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
			}

			setState(911);
			match(MoreThan);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class TypeParameterNodeContext extends ParserRuleContext {
		public IdentifierContext name;
		public NamedTypeNodeContext upperBound;
		public NamedTypeNodeContext lowerBound;
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TerminalNode Extends() { return getToken(XLangParser.Extends, 0); }
		public TerminalNode Super() { return getToken(XLangParser.Super, 0); }
		public NamedTypeNodeContext namedTypeNode() {
			return getRuleContext(NamedTypeNodeContext.class,0);
		}
		public TypeParameterNodeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_typeParameterNode; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterTypeParameterNode(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitTypeParameterNode(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitTypeParameterNode(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TypeParameterNodeContext typeParameterNode() throws RecognitionException {
		TypeParameterNodeContext _localctx = new TypeParameterNodeContext(_ctx, getState());
		enterRule(_localctx, 148, RULE_typeParameterNode);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(913);
			((TypeParameterNodeContext)_localctx).name = identifier();
			setState(918);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case Extends:
				{
				setState(914);
				match(Extends);
				setState(915);
				((TypeParameterNodeContext)_localctx).upperBound = namedTypeNode(0);
				}
				break;
			case Super:
				{
				setState(916);
				match(Super);
				setState(917);
				((TypeParameterNodeContext)_localctx).lowerBound = namedTypeNode(0);
				}
				break;
			case Comma:
			case MoreThan:
				break;
			default:
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class TypeArguments_Context extends ParserRuleContext {
		public NamedTypeNodeContext e;
		public TerminalNode LessThan() { return getToken(XLangParser.LessThan, 0); }
		public TerminalNode MoreThan() { return getToken(XLangParser.MoreThan, 0); }
		public List<NamedTypeNodeContext> namedTypeNode() {
			return getRuleContexts(NamedTypeNodeContext.class);
		}
		public NamedTypeNodeContext namedTypeNode(int i) {
			return getRuleContext(NamedTypeNodeContext.class,i);
		}
		public List<TerminalNode> Comma() { return getTokens(XLangParser.Comma); }
		public TerminalNode Comma(int i) {
			return getToken(XLangParser.Comma, i);
		}
		public TypeArguments_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_typeArguments_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterTypeArguments_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitTypeArguments_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitTypeArguments_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TypeArguments_Context typeArguments_() throws RecognitionException {
		TypeArguments_Context _localctx = new TypeArguments_Context(_ctx, getState());
		enterRule(_localctx, 150, RULE_typeArguments_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(920);
			match(LessThan);
			setState(929);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (((((_la - 76)) & ~0x3f) == 0 && ((1L << (_la - 76)) & 72533407760385L) != 0)) {
				{
				setState(921);
				((TypeArguments_Context)_localctx).e = namedTypeNode(0);
				setState(926);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==Comma) {
					{
					{
					setState(922);
					match(Comma);
					setState(923);
					((TypeArguments_Context)_localctx).e = namedTypeNode(0);
					}
					}
					setState(928);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
			}

			setState(931);
			match(MoreThan);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class StructuredTypeDefContext extends ParserRuleContext {
		public TypeNode_unionOrIntersectionContext typeNode_unionOrIntersection() {
			return getRuleContext(TypeNode_unionOrIntersectionContext.class,0);
		}
		public ObjectTypeDefContext objectTypeDef() {
			return getRuleContext(ObjectTypeDefContext.class,0);
		}
		public TupleTypeDefContext tupleTypeDef() {
			return getRuleContext(TupleTypeDefContext.class,0);
		}
		public NamedTypeNodeContext namedTypeNode() {
			return getRuleContext(NamedTypeNodeContext.class,0);
		}
		public FunctionTypeDefContext functionTypeDef() {
			return getRuleContext(FunctionTypeDefContext.class,0);
		}
		public StructuredTypeDefContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_structuredTypeDef; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterStructuredTypeDef(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitStructuredTypeDef(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitStructuredTypeDef(this);
			else return visitor.visitChildren(this);
		}
	}

	public final StructuredTypeDefContext structuredTypeDef() throws RecognitionException {
		StructuredTypeDefContext _localctx = new StructuredTypeDefContext(_ctx, getState());
		enterRule(_localctx, 152, RULE_structuredTypeDef);
		try {
			setState(938);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,83,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(933);
				typeNode_unionOrIntersection();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(934);
				objectTypeDef();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(935);
				tupleTypeDef();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(936);
				namedTypeNode(0);
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(937);
				functionTypeDef();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class TypeNode_unionOrIntersectionContext extends ParserRuleContext {
		public TypeNode_unionOrIntersectionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_typeNode_unionOrIntersection; }
	 
		public TypeNode_unionOrIntersectionContext() { }
		public void copyFrom(TypeNode_unionOrIntersectionContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class IntersectionTypeDefContext extends TypeNode_unionOrIntersectionContext {
		public IntersectionTypeDef_Context types;
		public IntersectionTypeDef_Context intersectionTypeDef_() {
			return getRuleContext(IntersectionTypeDef_Context.class,0);
		}
		public IntersectionTypeDefContext(TypeNode_unionOrIntersectionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterIntersectionTypeDef(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitIntersectionTypeDef(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitIntersectionTypeDef(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class UnionTypeDefContext extends TypeNode_unionOrIntersectionContext {
		public UnionTypeDef_Context types;
		public UnionTypeDef_Context unionTypeDef_() {
			return getRuleContext(UnionTypeDef_Context.class,0);
		}
		public UnionTypeDefContext(TypeNode_unionOrIntersectionContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterUnionTypeDef(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitUnionTypeDef(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitUnionTypeDef(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TypeNode_unionOrIntersectionContext typeNode_unionOrIntersection() throws RecognitionException {
		TypeNode_unionOrIntersectionContext _localctx = new TypeNode_unionOrIntersectionContext(_ctx, getState());
		enterRule(_localctx, 154, RULE_typeNode_unionOrIntersection);
		try {
			setState(942);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,84,_ctx) ) {
			case 1:
				_localctx = new IntersectionTypeDefContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(940);
				((IntersectionTypeDefContext)_localctx).types = intersectionTypeDef_();
				}
				break;
			case 2:
				_localctx = new UnionTypeDefContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(941);
				((UnionTypeDefContext)_localctx).types = unionTypeDef_();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class IntersectionTypeDef_Context extends ParserRuleContext {
		public NamedTypeNodeContext e;
		public List<NamedTypeNodeContext> namedTypeNode() {
			return getRuleContexts(NamedTypeNodeContext.class);
		}
		public NamedTypeNodeContext namedTypeNode(int i) {
			return getRuleContext(NamedTypeNodeContext.class,i);
		}
		public List<TerminalNode> BitAnd() { return getTokens(XLangParser.BitAnd); }
		public TerminalNode BitAnd(int i) {
			return getToken(XLangParser.BitAnd, i);
		}
		public IntersectionTypeDef_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_intersectionTypeDef_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterIntersectionTypeDef_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitIntersectionTypeDef_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitIntersectionTypeDef_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final IntersectionTypeDef_Context intersectionTypeDef_() throws RecognitionException {
		IntersectionTypeDef_Context _localctx = new IntersectionTypeDef_Context(_ctx, getState());
		enterRule(_localctx, 156, RULE_intersectionTypeDef_);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(944);
			((IntersectionTypeDef_Context)_localctx).e = namedTypeNode(0);
			setState(947); 
			_errHandler.sync(this);
			_alt = 1;
			do {
				switch (_alt) {
				case 1:
					{
					{
					setState(945);
					match(BitAnd);
					setState(946);
					((IntersectionTypeDef_Context)_localctx).e = namedTypeNode(0);
					}
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(949); 
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,85,_ctx);
			} while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER );
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class UnionTypeDef_Context extends ParserRuleContext {
		public NamedTypeNodeContext e;
		public List<NamedTypeNodeContext> namedTypeNode() {
			return getRuleContexts(NamedTypeNodeContext.class);
		}
		public NamedTypeNodeContext namedTypeNode(int i) {
			return getRuleContext(NamedTypeNodeContext.class,i);
		}
		public List<TerminalNode> BitOr() { return getTokens(XLangParser.BitOr); }
		public TerminalNode BitOr(int i) {
			return getToken(XLangParser.BitOr, i);
		}
		public UnionTypeDef_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_unionTypeDef_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterUnionTypeDef_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitUnionTypeDef_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitUnionTypeDef_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final UnionTypeDef_Context unionTypeDef_() throws RecognitionException {
		UnionTypeDef_Context _localctx = new UnionTypeDef_Context(_ctx, getState());
		enterRule(_localctx, 158, RULE_unionTypeDef_);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(951);
			((UnionTypeDef_Context)_localctx).e = namedTypeNode(0);
			setState(954); 
			_errHandler.sync(this);
			_alt = 1;
			do {
				switch (_alt) {
				case 1:
					{
					{
					setState(952);
					match(BitOr);
					setState(953);
					((UnionTypeDef_Context)_localctx).e = namedTypeNode(0);
					}
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(956); 
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,86,_ctx);
			} while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER );
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class TupleTypeDefContext extends ParserRuleContext {
		public TupleTypeElements_Context types;
		public TerminalNode OpenBracket() { return getToken(XLangParser.OpenBracket, 0); }
		public TerminalNode CloseBracket() { return getToken(XLangParser.CloseBracket, 0); }
		public TupleTypeElements_Context tupleTypeElements_() {
			return getRuleContext(TupleTypeElements_Context.class,0);
		}
		public TupleTypeDefContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_tupleTypeDef; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterTupleTypeDef(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitTupleTypeDef(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitTupleTypeDef(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TupleTypeDefContext tupleTypeDef() throws RecognitionException {
		TupleTypeDefContext _localctx = new TupleTypeDefContext(_ctx, getState());
		enterRule(_localctx, 160, RULE_tupleTypeDef);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(958);
			match(OpenBracket);
			setState(959);
			((TupleTypeDefContext)_localctx).types = tupleTypeElements_();
			setState(960);
			match(CloseBracket);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class TupleTypeElements_Context extends ParserRuleContext {
		public StructuredTypeDefContext e;
		public List<StructuredTypeDefContext> structuredTypeDef() {
			return getRuleContexts(StructuredTypeDefContext.class);
		}
		public StructuredTypeDefContext structuredTypeDef(int i) {
			return getRuleContext(StructuredTypeDefContext.class,i);
		}
		public List<TerminalNode> Comma() { return getTokens(XLangParser.Comma); }
		public TerminalNode Comma(int i) {
			return getToken(XLangParser.Comma, i);
		}
		public TupleTypeElements_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_tupleTypeElements_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterTupleTypeElements_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitTupleTypeElements_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitTupleTypeElements_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TupleTypeElements_Context tupleTypeElements_() throws RecognitionException {
		TupleTypeElements_Context _localctx = new TupleTypeElements_Context(_ctx, getState());
		enterRule(_localctx, 162, RULE_tupleTypeElements_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(962);
			((TupleTypeElements_Context)_localctx).e = structuredTypeDef();
			setState(967);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==Comma) {
				{
				{
				setState(963);
				match(Comma);
				setState(964);
				((TupleTypeElements_Context)_localctx).e = structuredTypeDef();
				}
				}
				setState(969);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class NamedTypeNodeContext extends ParserRuleContext {
		public NamedTypeNodeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_namedTypeNode; }
	 
		public NamedTypeNodeContext() { }
		public void copyFrom(NamedTypeNodeContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class TypeNameNode_namedContext extends NamedTypeNodeContext {
		public QualifiedName_Context typeName;
		public QualifiedName_Context qualifiedName_() {
			return getRuleContext(QualifiedName_Context.class,0);
		}
		public TypeNameNode_namedContext(NamedTypeNodeContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterTypeNameNode_named(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitTypeNameNode_named(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitTypeNameNode_named(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ArrayTypeNodeContext extends NamedTypeNodeContext {
		public NamedTypeNodeContext componentType;
		public TerminalNode OpenBracket() { return getToken(XLangParser.OpenBracket, 0); }
		public TerminalNode CloseBracket() { return getToken(XLangParser.CloseBracket, 0); }
		public NamedTypeNodeContext namedTypeNode() {
			return getRuleContext(NamedTypeNodeContext.class,0);
		}
		public ArrayTypeNodeContext(NamedTypeNodeContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterArrayTypeNode(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitArrayTypeNode(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitArrayTypeNode(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class TypeNameNode_predefined_namedContext extends NamedTypeNodeContext {
		public TypeNameNode_predefinedContext typeNameNode_predefined() {
			return getRuleContext(TypeNameNode_predefinedContext.class,0);
		}
		public TypeNameNode_predefined_namedContext(NamedTypeNodeContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterTypeNameNode_predefined_named(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitTypeNameNode_predefined_named(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitTypeNameNode_predefined_named(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ParameterizedTypeNode_namedContext extends NamedTypeNodeContext {
		public ParameterizedTypeNodeContext parameterizedTypeNode() {
			return getRuleContext(ParameterizedTypeNodeContext.class,0);
		}
		public ParameterizedTypeNode_namedContext(NamedTypeNodeContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterParameterizedTypeNode_named(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitParameterizedTypeNode_named(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitParameterizedTypeNode_named(this);
			else return visitor.visitChildren(this);
		}
	}

	public final NamedTypeNodeContext namedTypeNode() throws RecognitionException {
		return namedTypeNode(0);
	}

	private NamedTypeNodeContext namedTypeNode(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		NamedTypeNodeContext _localctx = new NamedTypeNodeContext(_ctx, _parentState);
		NamedTypeNodeContext _prevctx = _localctx;
		int _startState = 164;
		enterRecursionRule(_localctx, 164, RULE_namedTypeNode, _p);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(974);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,88,_ctx) ) {
			case 1:
				{
				_localctx = new TypeNameNode_predefined_namedContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;

				setState(971);
				typeNameNode_predefined();
				}
				break;
			case 2:
				{
				_localctx = new TypeNameNode_namedContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(972);
				((TypeNameNode_namedContext)_localctx).typeName = qualifiedName_();
				}
				break;
			case 3:
				{
				_localctx = new ParameterizedTypeNode_namedContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(973);
				parameterizedTypeNode();
				}
				break;
			}
			_ctx.stop = _input.LT(-1);
			setState(982);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,89,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					{
					_localctx = new ArrayTypeNodeContext(new NamedTypeNodeContext(_parentctx, _parentState));
					((ArrayTypeNodeContext)_localctx).componentType = _prevctx;
					pushNewRecursionContext(_localctx, _startState, RULE_namedTypeNode);
					setState(976);
					if (!(precpred(_ctx, 1))) throw new FailedPredicateException(this, "precpred(_ctx, 1)");
					setState(977);
					if (!(notLineTerminator())) throw new FailedPredicateException(this, "notLineTerminator()");
					setState(978);
					match(OpenBracket);
					setState(979);
					match(CloseBracket);
					}
					} 
				}
				setState(984);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,89,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			unrollRecursionContexts(_parentctx);
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class TypeNameNode_predefinedContext extends ParserRuleContext {
		public TerminalNode Any() { return getToken(XLangParser.Any, 0); }
		public TerminalNode Number() { return getToken(XLangParser.Number, 0); }
		public TerminalNode Boolean() { return getToken(XLangParser.Boolean, 0); }
		public TerminalNode String() { return getToken(XLangParser.String, 0); }
		public TerminalNode Symbol() { return getToken(XLangParser.Symbol, 0); }
		public TerminalNode Void() { return getToken(XLangParser.Void, 0); }
		public TypeNameNode_predefinedContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_typeNameNode_predefined; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterTypeNameNode_predefined(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitTypeNameNode_predefined(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitTypeNameNode_predefined(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TypeNameNode_predefinedContext typeNameNode_predefined() throws RecognitionException {
		TypeNameNode_predefinedContext _localctx = new TypeNameNode_predefinedContext(_ctx, getState());
		enterRule(_localctx, 166, RULE_typeNameNode_predefined);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(985);
			_la = _input.LA(1);
			if ( !(((((_la - 76)) & ~0x3f) == 0 && ((1L << (_la - 76)) & 1065151889409L) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ParameterizedTypeNodeContext extends ParserRuleContext {
		public QualifiedName_Context typeName;
		public TypeArguments_Context typeArgs;
		public QualifiedName_Context qualifiedName_() {
			return getRuleContext(QualifiedName_Context.class,0);
		}
		public TypeArguments_Context typeArguments_() {
			return getRuleContext(TypeArguments_Context.class,0);
		}
		public ParameterizedTypeNodeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_parameterizedTypeNode; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterParameterizedTypeNode(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitParameterizedTypeNode(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitParameterizedTypeNode(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ParameterizedTypeNodeContext parameterizedTypeNode() throws RecognitionException {
		ParameterizedTypeNodeContext _localctx = new ParameterizedTypeNodeContext(_ctx, getState());
		enterRule(_localctx, 168, RULE_parameterizedTypeNode);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(987);
			((ParameterizedTypeNodeContext)_localctx).typeName = qualifiedName_();
			setState(989);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,90,_ctx) ) {
			case 1:
				{
				setState(988);
				((ParameterizedTypeNodeContext)_localctx).typeArgs = typeArguments_();
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ObjectTypeDefContext extends ParserRuleContext {
		public ObjectTypeElements_Context types;
		public TerminalNode OpenBrace() { return getToken(XLangParser.OpenBrace, 0); }
		public TerminalNode CloseBrace() { return getToken(XLangParser.CloseBrace, 0); }
		public ObjectTypeElements_Context objectTypeElements_() {
			return getRuleContext(ObjectTypeElements_Context.class,0);
		}
		public TerminalNode Comma() { return getToken(XLangParser.Comma, 0); }
		public ObjectTypeDefContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_objectTypeDef; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterObjectTypeDef(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitObjectTypeDef(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitObjectTypeDef(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ObjectTypeDefContext objectTypeDef() throws RecognitionException {
		ObjectTypeDefContext _localctx = new ObjectTypeDefContext(_ctx, getState());
		enterRule(_localctx, 170, RULE_objectTypeDef);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(991);
			match(OpenBrace);
			setState(992);
			((ObjectTypeDefContext)_localctx).types = objectTypeElements_();
			setState(994);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Comma) {
				{
				setState(993);
				match(Comma);
				}
			}

			setState(996);
			match(CloseBrace);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ObjectTypeElements_Context extends ParserRuleContext {
		public PropertyTypeDefContext e;
		public List<PropertyTypeDefContext> propertyTypeDef() {
			return getRuleContexts(PropertyTypeDefContext.class);
		}
		public PropertyTypeDefContext propertyTypeDef(int i) {
			return getRuleContext(PropertyTypeDefContext.class,i);
		}
		public List<TerminalNode> Comma() { return getTokens(XLangParser.Comma); }
		public TerminalNode Comma(int i) {
			return getToken(XLangParser.Comma, i);
		}
		public ObjectTypeElements_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_objectTypeElements_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterObjectTypeElements_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitObjectTypeElements_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitObjectTypeElements_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ObjectTypeElements_Context objectTypeElements_() throws RecognitionException {
		ObjectTypeElements_Context _localctx = new ObjectTypeElements_Context(_ctx, getState());
		enterRule(_localctx, 172, RULE_objectTypeElements_);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(998);
			((ObjectTypeElements_Context)_localctx).e = propertyTypeDef();
			setState(1003);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,92,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(999);
					match(Comma);
					setState(1000);
					((ObjectTypeElements_Context)_localctx).e = propertyTypeDef();
					}
					} 
				}
				setState(1005);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,92,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class FunctionTypeDefContext extends ParserRuleContext {
		public TypeParameters_Context typeParams;
		public FunctionParameterTypes_Context args;
		public NamedTypeNodeContext returnType;
		public TerminalNode OpenBrace() { return getToken(XLangParser.OpenBrace, 0); }
		public TerminalNode CloseBrace() { return getToken(XLangParser.CloseBrace, 0); }
		public TerminalNode Arrow() { return getToken(XLangParser.Arrow, 0); }
		public NamedTypeNodeContext namedTypeNode() {
			return getRuleContext(NamedTypeNodeContext.class,0);
		}
		public TypeParameters_Context typeParameters_() {
			return getRuleContext(TypeParameters_Context.class,0);
		}
		public FunctionParameterTypes_Context functionParameterTypes_() {
			return getRuleContext(FunctionParameterTypes_Context.class,0);
		}
		public FunctionTypeDefContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_functionTypeDef; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterFunctionTypeDef(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitFunctionTypeDef(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitFunctionTypeDef(this);
			else return visitor.visitChildren(this);
		}
	}

	public final FunctionTypeDefContext functionTypeDef() throws RecognitionException {
		FunctionTypeDefContext _localctx = new FunctionTypeDefContext(_ctx, getState());
		enterRule(_localctx, 174, RULE_functionTypeDef);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1007);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LessThan) {
				{
				setState(1006);
				((FunctionTypeDefContext)_localctx).typeParams = typeParameters_();
				}
			}

			setState(1009);
			match(OpenBrace);
			setState(1011);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (((((_la - 92)) & ~0x3f) == 0 && ((1L << (_la - 92)) & 1090519041L) != 0)) {
				{
				setState(1010);
				((FunctionTypeDefContext)_localctx).args = functionParameterTypes_();
				}
			}

			setState(1013);
			match(CloseBrace);
			setState(1014);
			match(Arrow);
			setState(1015);
			((FunctionTypeDefContext)_localctx).returnType = namedTypeNode(0);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class PropertyTypeDefContext extends ParserRuleContext {
		public Token readonly;
		public PropertyName_Context name;
		public Token optional;
		public StructuredTypeDef_annotationContext valueType;
		public PropertyName_Context propertyName_() {
			return getRuleContext(PropertyName_Context.class,0);
		}
		public DecoratorsContext decorators() {
			return getRuleContext(DecoratorsContext.class,0);
		}
		public TerminalNode ReadOnly() { return getToken(XLangParser.ReadOnly, 0); }
		public TerminalNode Question() { return getToken(XLangParser.Question, 0); }
		public StructuredTypeDef_annotationContext structuredTypeDef_annotation() {
			return getRuleContext(StructuredTypeDef_annotationContext.class,0);
		}
		public PropertyTypeDefContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_propertyTypeDef; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterPropertyTypeDef(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitPropertyTypeDef(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitPropertyTypeDef(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PropertyTypeDefContext propertyTypeDef() throws RecognitionException {
		PropertyTypeDefContext _localctx = new PropertyTypeDefContext(_ctx, getState());
		enterRule(_localctx, 176, RULE_propertyTypeDef);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1018);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==At) {
				{
				setState(1017);
				decorators();
				}
			}

			setState(1021);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,96,_ctx) ) {
			case 1:
				{
				setState(1020);
				((PropertyTypeDefContext)_localctx).readonly = match(ReadOnly);
				}
				break;
			}
			setState(1023);
			((PropertyTypeDefContext)_localctx).name = propertyName_();
			setState(1025);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Question) {
				{
				setState(1024);
				((PropertyTypeDefContext)_localctx).optional = match(Question);
				}
			}

			setState(1028);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Colon) {
				{
				setState(1027);
				((PropertyTypeDefContext)_localctx).valueType = structuredTypeDef_annotation();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class NamedTypeNode_annotationContext extends ParserRuleContext {
		public TerminalNode Colon() { return getToken(XLangParser.Colon, 0); }
		public NamedTypeNodeContext namedTypeNode() {
			return getRuleContext(NamedTypeNodeContext.class,0);
		}
		public NamedTypeNode_annotationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_namedTypeNode_annotation; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterNamedTypeNode_annotation(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitNamedTypeNode_annotation(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitNamedTypeNode_annotation(this);
			else return visitor.visitChildren(this);
		}
	}

	public final NamedTypeNode_annotationContext namedTypeNode_annotation() throws RecognitionException {
		NamedTypeNode_annotationContext _localctx = new NamedTypeNode_annotationContext(_ctx, getState());
		enterRule(_localctx, 178, RULE_namedTypeNode_annotation);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1030);
			match(Colon);
			setState(1031);
			namedTypeNode(0);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class StructuredTypeDef_annotationContext extends ParserRuleContext {
		public TerminalNode Colon() { return getToken(XLangParser.Colon, 0); }
		public StructuredTypeDefContext structuredTypeDef() {
			return getRuleContext(StructuredTypeDefContext.class,0);
		}
		public StructuredTypeDef_annotationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_structuredTypeDef_annotation; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterStructuredTypeDef_annotation(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitStructuredTypeDef_annotation(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitStructuredTypeDef_annotation(this);
			else return visitor.visitChildren(this);
		}
	}

	public final StructuredTypeDef_annotationContext structuredTypeDef_annotation() throws RecognitionException {
		StructuredTypeDef_annotationContext _localctx = new StructuredTypeDef_annotationContext(_ctx, getState());
		enterRule(_localctx, 180, RULE_structuredTypeDef_annotation);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1033);
			match(Colon);
			setState(1034);
			structuredTypeDef();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class FunctionParameterTypes_Context extends ParserRuleContext {
		public FunctionArgTypeDefContext e;
		public List<FunctionArgTypeDefContext> functionArgTypeDef() {
			return getRuleContexts(FunctionArgTypeDefContext.class);
		}
		public FunctionArgTypeDefContext functionArgTypeDef(int i) {
			return getRuleContext(FunctionArgTypeDefContext.class,i);
		}
		public List<TerminalNode> Comma() { return getTokens(XLangParser.Comma); }
		public TerminalNode Comma(int i) {
			return getToken(XLangParser.Comma, i);
		}
		public FunctionParameterTypes_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_functionParameterTypes_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterFunctionParameterTypes_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitFunctionParameterTypes_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitFunctionParameterTypes_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final FunctionParameterTypes_Context functionParameterTypes_() throws RecognitionException {
		FunctionParameterTypes_Context _localctx = new FunctionParameterTypes_Context(_ctx, getState());
		enterRule(_localctx, 182, RULE_functionParameterTypes_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1036);
			((FunctionParameterTypes_Context)_localctx).e = functionArgTypeDef();
			setState(1041);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==Comma) {
				{
				{
				setState(1037);
				match(Comma);
				setState(1038);
				((FunctionParameterTypes_Context)_localctx).e = functionArgTypeDef();
				}
				}
				setState(1043);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class FunctionArgTypeDefContext extends ParserRuleContext {
		public IdentifierContext argName;
		public Token optional;
		public NamedTypeNode_annotationContext argType;
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TerminalNode Question() { return getToken(XLangParser.Question, 0); }
		public NamedTypeNode_annotationContext namedTypeNode_annotation() {
			return getRuleContext(NamedTypeNode_annotationContext.class,0);
		}
		public FunctionArgTypeDefContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_functionArgTypeDef; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterFunctionArgTypeDef(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitFunctionArgTypeDef(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitFunctionArgTypeDef(this);
			else return visitor.visitChildren(this);
		}
	}

	public final FunctionArgTypeDefContext functionArgTypeDef() throws RecognitionException {
		FunctionArgTypeDefContext _localctx = new FunctionArgTypeDefContext(_ctx, getState());
		enterRule(_localctx, 184, RULE_functionArgTypeDef);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1044);
			((FunctionArgTypeDefContext)_localctx).argName = identifier();
			setState(1046);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Question) {
				{
				setState(1045);
				((FunctionArgTypeDefContext)_localctx).optional = match(Question);
				}
			}

			setState(1049);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Colon) {
				{
				setState(1048);
				((FunctionArgTypeDefContext)_localctx).argType = namedTypeNode_annotation();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class TypeAliasDeclarationContext extends ParserRuleContext {
		public IdentifierContext typeName;
		public TypeParameters_Context typeParams;
		public StructuredTypeDefContext defType;
		public TerminalNode TypeAlias() { return getToken(XLangParser.TypeAlias, 0); }
		public TerminalNode Assign() { return getToken(XLangParser.Assign, 0); }
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public StructuredTypeDefContext structuredTypeDef() {
			return getRuleContext(StructuredTypeDefContext.class,0);
		}
		public DecoratorsContext decorators() {
			return getRuleContext(DecoratorsContext.class,0);
		}
		public TerminalNode SemiColon() { return getToken(XLangParser.SemiColon, 0); }
		public TypeParameters_Context typeParameters_() {
			return getRuleContext(TypeParameters_Context.class,0);
		}
		public TypeAliasDeclarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_typeAliasDeclaration; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterTypeAliasDeclaration(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitTypeAliasDeclaration(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitTypeAliasDeclaration(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TypeAliasDeclarationContext typeAliasDeclaration() throws RecognitionException {
		TypeAliasDeclarationContext _localctx = new TypeAliasDeclarationContext(_ctx, getState());
		enterRule(_localctx, 186, RULE_typeAliasDeclaration);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1052);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==At) {
				{
				setState(1051);
				decorators();
				}
			}

			setState(1054);
			match(TypeAlias);
			setState(1055);
			((TypeAliasDeclarationContext)_localctx).typeName = identifier();
			setState(1057);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LessThan) {
				{
				setState(1056);
				((TypeAliasDeclarationContext)_localctx).typeParams = typeParameters_();
				}
			}

			setState(1059);
			match(Assign);
			setState(1060);
			((TypeAliasDeclarationContext)_localctx).defType = structuredTypeDef();
			setState(1062);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,104,_ctx) ) {
			case 1:
				{
				setState(1061);
				match(SemiColon);
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class EnumDeclarationContext extends ParserRuleContext {
		public IdentifierContext name;
		public EnumMembers_Context members;
		public TerminalNode Enum() { return getToken(XLangParser.Enum, 0); }
		public TerminalNode OpenBrace() { return getToken(XLangParser.OpenBrace, 0); }
		public TerminalNode CloseBrace() { return getToken(XLangParser.CloseBrace, 0); }
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public EnumMembers_Context enumMembers_() {
			return getRuleContext(EnumMembers_Context.class,0);
		}
		public TerminalNode Comma() { return getToken(XLangParser.Comma, 0); }
		public EnumDeclarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_enumDeclaration; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterEnumDeclaration(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitEnumDeclaration(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitEnumDeclaration(this);
			else return visitor.visitChildren(this);
		}
	}

	public final EnumDeclarationContext enumDeclaration() throws RecognitionException {
		EnumDeclarationContext _localctx = new EnumDeclarationContext(_ctx, getState());
		enterRule(_localctx, 188, RULE_enumDeclaration);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1064);
			match(Enum);
			setState(1065);
			((EnumDeclarationContext)_localctx).name = identifier();
			setState(1066);
			match(OpenBrace);
			setState(1067);
			((EnumDeclarationContext)_localctx).members = enumMembers_();
			setState(1069);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Comma) {
				{
				setState(1068);
				match(Comma);
				}
			}

			setState(1071);
			match(CloseBrace);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class EnumMembers_Context extends ParserRuleContext {
		public EnumMemberContext e;
		public List<EnumMemberContext> enumMember() {
			return getRuleContexts(EnumMemberContext.class);
		}
		public EnumMemberContext enumMember(int i) {
			return getRuleContext(EnumMemberContext.class,i);
		}
		public List<TerminalNode> Comma() { return getTokens(XLangParser.Comma); }
		public TerminalNode Comma(int i) {
			return getToken(XLangParser.Comma, i);
		}
		public EnumMembers_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_enumMembers_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterEnumMembers_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitEnumMembers_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitEnumMembers_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final EnumMembers_Context enumMembers_() throws RecognitionException {
		EnumMembers_Context _localctx = new EnumMembers_Context(_ctx, getState());
		enterRule(_localctx, 190, RULE_enumMembers_);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(1073);
			((EnumMembers_Context)_localctx).e = enumMember();
			setState(1078);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,106,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(1074);
					match(Comma);
					setState(1075);
					((EnumMembers_Context)_localctx).e = enumMember();
					}
					} 
				}
				setState(1080);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,106,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class EnumMemberContext extends ParserRuleContext {
		public IdentifierContext name;
		public LiteralContext value;
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TerminalNode Assign() { return getToken(XLangParser.Assign, 0); }
		public LiteralContext literal() {
			return getRuleContext(LiteralContext.class,0);
		}
		public EnumMemberContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_enumMember; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterEnumMember(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitEnumMember(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitEnumMember(this);
			else return visitor.visitChildren(this);
		}
	}

	public final EnumMemberContext enumMember() throws RecognitionException {
		EnumMemberContext _localctx = new EnumMemberContext(_ctx, getState());
		enterRule(_localctx, 192, RULE_enumMember);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1081);
			((EnumMemberContext)_localctx).name = identifier();
			setState(1084);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Assign) {
				{
				setState(1082);
				match(Assign);
				setState(1083);
				((EnumMemberContext)_localctx).value = literal();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class DecoratorsContext extends ParserRuleContext {
		public DecoratorElements_Context decorators_;
		public DecoratorElements_Context decoratorElements_() {
			return getRuleContext(DecoratorElements_Context.class,0);
		}
		public DecoratorsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_decorators; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterDecorators(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitDecorators(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitDecorators(this);
			else return visitor.visitChildren(this);
		}
	}

	public final DecoratorsContext decorators() throws RecognitionException {
		DecoratorsContext _localctx = new DecoratorsContext(_ctx, getState());
		enterRule(_localctx, 194, RULE_decorators);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1086);
			((DecoratorsContext)_localctx).decorators_ = decoratorElements_();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class DecoratorElements_Context extends ParserRuleContext {
		public DecoratorContext e;
		public List<DecoratorContext> decorator() {
			return getRuleContexts(DecoratorContext.class);
		}
		public DecoratorContext decorator(int i) {
			return getRuleContext(DecoratorContext.class,i);
		}
		public DecoratorElements_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_decoratorElements_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterDecoratorElements_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitDecoratorElements_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitDecoratorElements_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final DecoratorElements_Context decoratorElements_() throws RecognitionException {
		DecoratorElements_Context _localctx = new DecoratorElements_Context(_ctx, getState());
		enterRule(_localctx, 196, RULE_decoratorElements_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1089); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(1088);
				((DecoratorElements_Context)_localctx).e = decorator();
				}
				}
				setState(1091); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( _la==At );
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class DecoratorContext extends ParserRuleContext {
		public QualifiedNameContext name;
		public MetaObjectContext value;
		public TerminalNode At() { return getToken(XLangParser.At, 0); }
		public QualifiedNameContext qualifiedName() {
			return getRuleContext(QualifiedNameContext.class,0);
		}
		public TerminalNode OpenBrace() { return getToken(XLangParser.OpenBrace, 0); }
		public TerminalNode CloseBrace() { return getToken(XLangParser.CloseBrace, 0); }
		public MetaObjectContext metaObject() {
			return getRuleContext(MetaObjectContext.class,0);
		}
		public DecoratorContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_decorator; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterDecorator(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitDecorator(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitDecorator(this);
			else return visitor.visitChildren(this);
		}
	}

	public final DecoratorContext decorator() throws RecognitionException {
		DecoratorContext _localctx = new DecoratorContext(_ctx, getState());
		enterRule(_localctx, 198, RULE_decorator);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1093);
			match(At);
			setState(1094);
			((DecoratorContext)_localctx).name = qualifiedName();
			setState(1099);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,109,_ctx) ) {
			case 1:
				{
				setState(1095);
				match(OpenBrace);
				setState(1096);
				((DecoratorContext)_localctx).value = metaObject();
				setState(1097);
				match(CloseBrace);
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class MetaObjectContext extends ParserRuleContext {
		public MetaObjectProperties_Context properties;
		public TerminalNode OpenBrace() { return getToken(XLangParser.OpenBrace, 0); }
		public TerminalNode CloseBrace() { return getToken(XLangParser.CloseBrace, 0); }
		public MetaObjectProperties_Context metaObjectProperties_() {
			return getRuleContext(MetaObjectProperties_Context.class,0);
		}
		public TerminalNode Comma() { return getToken(XLangParser.Comma, 0); }
		public MetaObjectContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_metaObject; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterMetaObject(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitMetaObject(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitMetaObject(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MetaObjectContext metaObject() throws RecognitionException {
		MetaObjectContext _localctx = new MetaObjectContext(_ctx, getState());
		enterRule(_localctx, 200, RULE_metaObject);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1101);
			match(OpenBrace);
			setState(1106);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (((((_la - 92)) & ~0x3f) == 0 && ((1L << (_la - 92)) & 1090519041L) != 0)) {
				{
				setState(1102);
				((MetaObjectContext)_localctx).properties = metaObjectProperties_();
				setState(1104);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==Comma) {
					{
					setState(1103);
					match(Comma);
					}
				}

				}
			}

			setState(1108);
			match(CloseBrace);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class MetaObjectProperties_Context extends ParserRuleContext {
		public MetaPropertyContext e;
		public List<MetaPropertyContext> metaProperty() {
			return getRuleContexts(MetaPropertyContext.class);
		}
		public MetaPropertyContext metaProperty(int i) {
			return getRuleContext(MetaPropertyContext.class,i);
		}
		public List<TerminalNode> Comma() { return getTokens(XLangParser.Comma); }
		public TerminalNode Comma(int i) {
			return getToken(XLangParser.Comma, i);
		}
		public MetaObjectProperties_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_metaObjectProperties_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterMetaObjectProperties_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitMetaObjectProperties_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitMetaObjectProperties_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MetaObjectProperties_Context metaObjectProperties_() throws RecognitionException {
		MetaObjectProperties_Context _localctx = new MetaObjectProperties_Context(_ctx, getState());
		enterRule(_localctx, 202, RULE_metaObjectProperties_);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(1110);
			((MetaObjectProperties_Context)_localctx).e = metaProperty();
			setState(1115);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,112,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(1111);
					match(Comma);
					setState(1112);
					((MetaObjectProperties_Context)_localctx).e = metaProperty();
					}
					} 
				}
				setState(1117);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,112,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class MetaPropertyContext extends ParserRuleContext {
		public IdentifierContext name;
		public Ast_metaValueContext value;
		public TerminalNode Colon() { return getToken(XLangParser.Colon, 0); }
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public Ast_metaValueContext ast_metaValue() {
			return getRuleContext(Ast_metaValueContext.class,0);
		}
		public MetaPropertyContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_metaProperty; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterMetaProperty(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitMetaProperty(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitMetaProperty(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MetaPropertyContext metaProperty() throws RecognitionException {
		MetaPropertyContext _localctx = new MetaPropertyContext(_ctx, getState());
		enterRule(_localctx, 204, RULE_metaProperty);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1118);
			((MetaPropertyContext)_localctx).name = identifier();
			setState(1119);
			match(Colon);
			setState(1120);
			((MetaPropertyContext)_localctx).value = ast_metaValue();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class MetaArrayContext extends ParserRuleContext {
		public MetaArrayElements_Context elements;
		public TerminalNode OpenBracket() { return getToken(XLangParser.OpenBracket, 0); }
		public TerminalNode CloseBracket() { return getToken(XLangParser.CloseBracket, 0); }
		public TerminalNode Comma() { return getToken(XLangParser.Comma, 0); }
		public MetaArrayElements_Context metaArrayElements_() {
			return getRuleContext(MetaArrayElements_Context.class,0);
		}
		public MetaArrayContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_metaArray; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterMetaArray(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitMetaArray(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitMetaArray(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MetaArrayContext metaArray() throws RecognitionException {
		MetaArrayContext _localctx = new MetaArrayContext(_ctx, getState());
		enterRule(_localctx, 206, RULE_metaArray);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1122);
			match(OpenBracket);
			setState(1124);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & -1873497444986126056L) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & 508906758161301505L) != 0)) {
				{
				setState(1123);
				((MetaArrayContext)_localctx).elements = metaArrayElements_();
				}
			}

			setState(1127);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Comma) {
				{
				setState(1126);
				match(Comma);
				}
			}

			setState(1129);
			match(CloseBracket);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class MetaArrayElements_Context extends ParserRuleContext {
		public Ast_metaValueContext e;
		public List<Ast_metaValueContext> ast_metaValue() {
			return getRuleContexts(Ast_metaValueContext.class);
		}
		public Ast_metaValueContext ast_metaValue(int i) {
			return getRuleContext(Ast_metaValueContext.class,i);
		}
		public List<TerminalNode> Comma() { return getTokens(XLangParser.Comma); }
		public TerminalNode Comma(int i) {
			return getToken(XLangParser.Comma, i);
		}
		public MetaArrayElements_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_metaArrayElements_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterMetaArrayElements_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitMetaArrayElements_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitMetaArrayElements_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MetaArrayElements_Context metaArrayElements_() throws RecognitionException {
		MetaArrayElements_Context _localctx = new MetaArrayElements_Context(_ctx, getState());
		enterRule(_localctx, 208, RULE_metaArrayElements_);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(1131);
			((MetaArrayElements_Context)_localctx).e = ast_metaValue();
			setState(1136);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,115,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(1132);
					match(Comma);
					setState(1133);
					((MetaArrayElements_Context)_localctx).e = ast_metaValue();
					}
					} 
				}
				setState(1138);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,115,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Ast_metaValueContext extends ParserRuleContext {
		public LiteralContext literal() {
			return getRuleContext(LiteralContext.class,0);
		}
		public MetaObjectContext metaObject() {
			return getRuleContext(MetaObjectContext.class,0);
		}
		public MetaArrayContext metaArray() {
			return getRuleContext(MetaArrayContext.class,0);
		}
		public QualifiedNameContext qualifiedName() {
			return getRuleContext(QualifiedNameContext.class,0);
		}
		public Ast_metaValueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_ast_metaValue; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterAst_metaValue(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitAst_metaValue(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitAst_metaValue(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Ast_metaValueContext ast_metaValue() throws RecognitionException {
		Ast_metaValueContext _localctx = new Ast_metaValueContext(_ctx, getState());
		enterRule(_localctx, 210, RULE_ast_metaValue);
		try {
			setState(1143);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case RegularExpressionLiteral:
			case NullLiteral:
			case BooleanLiteral:
			case DecimalIntegerLiteral:
			case HexIntegerLiteral:
			case BinaryIntegerLiteral:
			case DecimalLiteral:
			case StringLiteral:
			case TemplateStringLiteral:
				enterOuterAlt(_localctx, 1);
				{
				setState(1139);
				literal();
				}
				break;
			case OpenBrace:
				enterOuterAlt(_localctx, 2);
				{
				setState(1140);
				metaObject();
				}
				break;
			case OpenBracket:
				enterOuterAlt(_localctx, 3);
				{
				setState(1141);
				metaArray();
				}
				break;
			case From:
			case TypeAlias:
			case Identifier:
				enterOuterAlt(_localctx, 4);
				{
				setState(1142);
				qualifiedName();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class QualifiedNameContext extends ParserRuleContext {
		public QualifiedName_name_Context name;
		public QualifiedNameContext next;
		public QualifiedName_name_Context qualifiedName_name_() {
			return getRuleContext(QualifiedName_name_Context.class,0);
		}
		public TerminalNode Dot() { return getToken(XLangParser.Dot, 0); }
		public QualifiedNameContext qualifiedName() {
			return getRuleContext(QualifiedNameContext.class,0);
		}
		public QualifiedNameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_qualifiedName; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterQualifiedName(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitQualifiedName(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitQualifiedName(this);
			else return visitor.visitChildren(this);
		}
	}

	public final QualifiedNameContext qualifiedName() throws RecognitionException {
		QualifiedNameContext _localctx = new QualifiedNameContext(_ctx, getState());
		enterRule(_localctx, 212, RULE_qualifiedName);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1145);
			((QualifiedNameContext)_localctx).name = qualifiedName_name_();
			setState(1148);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,117,_ctx) ) {
			case 1:
				{
				setState(1146);
				match(Dot);
				setState(1147);
				((QualifiedNameContext)_localctx).next = qualifiedName();
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class QualifiedName_name_Context extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public QualifiedName_name_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_qualifiedName_name_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterQualifiedName_name_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitQualifiedName_name_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitQualifiedName_name_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final QualifiedName_name_Context qualifiedName_name_() throws RecognitionException {
		QualifiedName_name_Context _localctx = new QualifiedName_name_Context(_ctx, getState());
		enterRule(_localctx, 214, RULE_qualifiedName_name_);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1150);
			identifier();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class QualifiedName_Context extends ParserRuleContext {
		public QualifiedNameContext qualifiedName() {
			return getRuleContext(QualifiedNameContext.class,0);
		}
		public QualifiedName_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_qualifiedName_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterQualifiedName_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitQualifiedName_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitQualifiedName_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final QualifiedName_Context qualifiedName_() throws RecognitionException {
		QualifiedName_Context _localctx = new QualifiedName_Context(_ctx, getState());
		enterRule(_localctx, 216, RULE_qualifiedName_);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1152);
			qualifiedName();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class PropertyName_Context extends ParserRuleContext {
		public IdentifierOrKeyword_Context identifierOrKeyword_() {
			return getRuleContext(IdentifierOrKeyword_Context.class,0);
		}
		public TerminalNode StringLiteral() { return getToken(XLangParser.StringLiteral, 0); }
		public PropertyName_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_propertyName_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterPropertyName_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitPropertyName_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitPropertyName_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PropertyName_Context propertyName_() throws RecognitionException {
		PropertyName_Context _localctx = new PropertyName_Context(_ctx, getState());
		enterRule(_localctx, 218, RULE_propertyName_);
		try {
			setState(1156);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case NullLiteral:
			case BooleanLiteral:
			case AndLiteral:
			case OrLiteral:
			case Break:
			case Do:
			case Instanceof:
			case Typeof:
			case Case:
			case Else:
			case New:
			case Var:
			case Catch:
			case Finally:
			case Return:
			case Void:
			case Continue:
			case For:
			case Switch:
			case While:
			case Debugger:
			case Function:
			case This:
			case With:
			case Default:
			case If:
			case Throw:
			case Delete:
			case In:
			case Try:
			case As:
			case From:
			case ReadOnly:
			case Class:
			case Enum:
			case Extends:
			case Super:
			case Const:
			case Export:
			case Import:
			case Implements:
			case Let:
			case Private:
			case Public:
			case Interface:
			case Package:
			case Protected:
			case Static:
			case Any:
			case Number:
			case Boolean:
			case String:
			case Symbol:
			case TypeAlias:
			case Identifier:
				enterOuterAlt(_localctx, 1);
				{
				setState(1154);
				identifierOrKeyword_();
				}
				break;
			case StringLiteral:
				enterOuterAlt(_localctx, 2);
				{
				setState(1155);
				match(StringLiteral);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Expression_propNameContext extends ParserRuleContext {
		public Identifier_exContext identifier_ex() {
			return getRuleContext(Identifier_exContext.class,0);
		}
		public Literal_stringContext literal_string() {
			return getRuleContext(Literal_stringContext.class,0);
		}
		public Expression_propNameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expression_propName; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterExpression_propName(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitExpression_propName(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitExpression_propName(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Expression_propNameContext expression_propName() throws RecognitionException {
		Expression_propNameContext _localctx = new Expression_propNameContext(_ctx, getState());
		enterRule(_localctx, 220, RULE_expression_propName);
		try {
			setState(1160);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case NullLiteral:
			case BooleanLiteral:
			case AndLiteral:
			case OrLiteral:
			case Break:
			case Do:
			case Instanceof:
			case Typeof:
			case Case:
			case Else:
			case New:
			case Var:
			case Catch:
			case Finally:
			case Return:
			case Void:
			case Continue:
			case For:
			case Switch:
			case While:
			case Debugger:
			case Function:
			case This:
			case With:
			case Default:
			case If:
			case Throw:
			case Delete:
			case In:
			case Try:
			case As:
			case From:
			case ReadOnly:
			case Class:
			case Enum:
			case Extends:
			case Super:
			case Const:
			case Export:
			case Import:
			case Implements:
			case Let:
			case Private:
			case Public:
			case Interface:
			case Package:
			case Protected:
			case Static:
			case Any:
			case Number:
			case Boolean:
			case String:
			case Symbol:
			case TypeAlias:
			case Identifier:
				enterOuterAlt(_localctx, 1);
				{
				setState(1158);
				identifier_ex();
				}
				break;
			case StringLiteral:
				enterOuterAlt(_localctx, 2);
				{
				setState(1159);
				literal_string();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Identifier_exContext extends ParserRuleContext {
		public IdentifierOrKeyword_Context name;
		public IdentifierOrKeyword_Context identifierOrKeyword_() {
			return getRuleContext(IdentifierOrKeyword_Context.class,0);
		}
		public Identifier_exContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_identifier_ex; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterIdentifier_ex(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitIdentifier_ex(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitIdentifier_ex(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Identifier_exContext identifier_ex() throws RecognitionException {
		Identifier_exContext _localctx = new Identifier_exContext(_ctx, getState());
		enterRule(_localctx, 222, RULE_identifier_ex);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1162);
			((Identifier_exContext)_localctx).name = identifierOrKeyword_();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class IdentifierContext extends ParserRuleContext {
		public TerminalNode From() { return getToken(XLangParser.From, 0); }
		public TerminalNode TypeAlias() { return getToken(XLangParser.TypeAlias, 0); }
		public TerminalNode Identifier() { return getToken(XLangParser.Identifier, 0); }
		public IdentifierContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_identifier; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterIdentifier(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitIdentifier(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitIdentifier(this);
			else return visitor.visitChildren(this);
		}
	}

	public final IdentifierContext identifier() throws RecognitionException {
		IdentifierContext _localctx = new IdentifierContext(_ctx, getState());
		enterRule(_localctx, 224, RULE_identifier);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1164);
			_la = _input.LA(1);
			if ( !(((((_la - 92)) & ~0x3f) == 0 && ((1L << (_la - 92)) & 1090519041L) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class IdentifierOrKeyword_Context extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public ReservedWord_Context reservedWord_() {
			return getRuleContext(ReservedWord_Context.class,0);
		}
		public IdentifierOrKeyword_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_identifierOrKeyword_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterIdentifierOrKeyword_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitIdentifierOrKeyword_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitIdentifierOrKeyword_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final IdentifierOrKeyword_Context identifierOrKeyword_() throws RecognitionException {
		IdentifierOrKeyword_Context _localctx = new IdentifierOrKeyword_Context(_ctx, getState());
		enterRule(_localctx, 226, RULE_identifierOrKeyword_);
		try {
			setState(1168);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,120,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1166);
				identifier();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(1167);
				reservedWord_();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ReservedWord_Context extends ParserRuleContext {
		public Keyword_Context keyword_() {
			return getRuleContext(Keyword_Context.class,0);
		}
		public TerminalNode NullLiteral() { return getToken(XLangParser.NullLiteral, 0); }
		public TerminalNode BooleanLiteral() { return getToken(XLangParser.BooleanLiteral, 0); }
		public TerminalNode AndLiteral() { return getToken(XLangParser.AndLiteral, 0); }
		public TerminalNode OrLiteral() { return getToken(XLangParser.OrLiteral, 0); }
		public ReservedWord_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_reservedWord_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterReservedWord_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitReservedWord_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitReservedWord_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ReservedWord_Context reservedWord_() throws RecognitionException {
		ReservedWord_Context _localctx = new ReservedWord_Context(_ctx, getState());
		enterRule(_localctx, 228, RULE_reservedWord_);
		try {
			setState(1175);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case Break:
			case Do:
			case Instanceof:
			case Typeof:
			case Case:
			case Else:
			case New:
			case Var:
			case Catch:
			case Finally:
			case Return:
			case Void:
			case Continue:
			case For:
			case Switch:
			case While:
			case Debugger:
			case Function:
			case This:
			case With:
			case Default:
			case If:
			case Throw:
			case Delete:
			case In:
			case Try:
			case As:
			case ReadOnly:
			case Class:
			case Enum:
			case Extends:
			case Super:
			case Const:
			case Export:
			case Import:
			case Implements:
			case Let:
			case Private:
			case Public:
			case Interface:
			case Package:
			case Protected:
			case Static:
			case Any:
			case Number:
			case Boolean:
			case String:
			case Symbol:
			case TypeAlias:
				enterOuterAlt(_localctx, 1);
				{
				setState(1170);
				keyword_();
				}
				break;
			case NullLiteral:
				enterOuterAlt(_localctx, 2);
				{
				setState(1171);
				match(NullLiteral);
				}
				break;
			case BooleanLiteral:
				enterOuterAlt(_localctx, 3);
				{
				setState(1172);
				match(BooleanLiteral);
				}
				break;
			case AndLiteral:
				enterOuterAlt(_localctx, 4);
				{
				setState(1173);
				match(AndLiteral);
				}
				break;
			case OrLiteral:
				enterOuterAlt(_localctx, 5);
				{
				setState(1174);
				match(OrLiteral);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Keyword_Context extends ParserRuleContext {
		public TerminalNode Break() { return getToken(XLangParser.Break, 0); }
		public TerminalNode As() { return getToken(XLangParser.As, 0); }
		public TerminalNode Do() { return getToken(XLangParser.Do, 0); }
		public TerminalNode Instanceof() { return getToken(XLangParser.Instanceof, 0); }
		public TerminalNode Typeof() { return getToken(XLangParser.Typeof, 0); }
		public TerminalNode Case() { return getToken(XLangParser.Case, 0); }
		public TerminalNode Else() { return getToken(XLangParser.Else, 0); }
		public TerminalNode New() { return getToken(XLangParser.New, 0); }
		public TerminalNode Var() { return getToken(XLangParser.Var, 0); }
		public TerminalNode Catch() { return getToken(XLangParser.Catch, 0); }
		public TerminalNode Finally() { return getToken(XLangParser.Finally, 0); }
		public TerminalNode Return() { return getToken(XLangParser.Return, 0); }
		public TerminalNode Void() { return getToken(XLangParser.Void, 0); }
		public TerminalNode Continue() { return getToken(XLangParser.Continue, 0); }
		public TerminalNode For() { return getToken(XLangParser.For, 0); }
		public TerminalNode Switch() { return getToken(XLangParser.Switch, 0); }
		public TerminalNode While() { return getToken(XLangParser.While, 0); }
		public TerminalNode Debugger() { return getToken(XLangParser.Debugger, 0); }
		public TerminalNode Function() { return getToken(XLangParser.Function, 0); }
		public TerminalNode This() { return getToken(XLangParser.This, 0); }
		public TerminalNode With() { return getToken(XLangParser.With, 0); }
		public TerminalNode Default() { return getToken(XLangParser.Default, 0); }
		public TerminalNode If() { return getToken(XLangParser.If, 0); }
		public TerminalNode Throw() { return getToken(XLangParser.Throw, 0); }
		public TerminalNode Delete() { return getToken(XLangParser.Delete, 0); }
		public TerminalNode In() { return getToken(XLangParser.In, 0); }
		public TerminalNode Try() { return getToken(XLangParser.Try, 0); }
		public TerminalNode ReadOnly() { return getToken(XLangParser.ReadOnly, 0); }
		public TerminalNode Class() { return getToken(XLangParser.Class, 0); }
		public TerminalNode Enum() { return getToken(XLangParser.Enum, 0); }
		public TerminalNode Extends() { return getToken(XLangParser.Extends, 0); }
		public TerminalNode Super() { return getToken(XLangParser.Super, 0); }
		public TerminalNode Const() { return getToken(XLangParser.Const, 0); }
		public TerminalNode Export() { return getToken(XLangParser.Export, 0); }
		public TerminalNode Import() { return getToken(XLangParser.Import, 0); }
		public TerminalNode Implements() { return getToken(XLangParser.Implements, 0); }
		public TerminalNode Let() { return getToken(XLangParser.Let, 0); }
		public TerminalNode Private() { return getToken(XLangParser.Private, 0); }
		public TerminalNode Public() { return getToken(XLangParser.Public, 0); }
		public TerminalNode Interface() { return getToken(XLangParser.Interface, 0); }
		public TerminalNode Package() { return getToken(XLangParser.Package, 0); }
		public TerminalNode Protected() { return getToken(XLangParser.Protected, 0); }
		public TerminalNode Static() { return getToken(XLangParser.Static, 0); }
		public TerminalNode TypeAlias() { return getToken(XLangParser.TypeAlias, 0); }
		public TerminalNode String() { return getToken(XLangParser.String, 0); }
		public TerminalNode Boolean() { return getToken(XLangParser.Boolean, 0); }
		public TerminalNode Number() { return getToken(XLangParser.Number, 0); }
		public TerminalNode Any() { return getToken(XLangParser.Any, 0); }
		public TerminalNode Symbol() { return getToken(XLangParser.Symbol, 0); }
		public Keyword_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_keyword_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterKeyword_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitKeyword_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitKeyword_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Keyword_Context keyword_() throws RecognitionException {
		Keyword_Context _localctx = new Keyword_Context(_ctx, getState());
		enterRule(_localctx, 230, RULE_keyword_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1177);
			_la = _input.LA(1);
			if ( !(((((_la - 65)) & ~0x3f) == 0 && ((1L << (_la - 65)) & 4503597882540031L) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class LiteralContext extends ParserRuleContext {
		public TerminalNode NullLiteral() { return getToken(XLangParser.NullLiteral, 0); }
		public TerminalNode BooleanLiteral() { return getToken(XLangParser.BooleanLiteral, 0); }
		public Literal_stringContext literal_string() {
			return getRuleContext(Literal_stringContext.class,0);
		}
		public TerminalNode TemplateStringLiteral() { return getToken(XLangParser.TemplateStringLiteral, 0); }
		public TerminalNode RegularExpressionLiteral() { return getToken(XLangParser.RegularExpressionLiteral, 0); }
		public Literal_numericContext literal_numeric() {
			return getRuleContext(Literal_numericContext.class,0);
		}
		public LiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_literal; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterLiteral(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitLiteral(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitLiteral(this);
			else return visitor.visitChildren(this);
		}
	}

	public final LiteralContext literal() throws RecognitionException {
		LiteralContext _localctx = new LiteralContext(_ctx, getState());
		enterRule(_localctx, 232, RULE_literal);
		try {
			setState(1185);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case NullLiteral:
				enterOuterAlt(_localctx, 1);
				{
				setState(1179);
				match(NullLiteral);
				}
				break;
			case BooleanLiteral:
				enterOuterAlt(_localctx, 2);
				{
				setState(1180);
				match(BooleanLiteral);
				}
				break;
			case StringLiteral:
				enterOuterAlt(_localctx, 3);
				{
				setState(1181);
				literal_string();
				}
				break;
			case TemplateStringLiteral:
				enterOuterAlt(_localctx, 4);
				{
				setState(1182);
				match(TemplateStringLiteral);
				}
				break;
			case RegularExpressionLiteral:
				enterOuterAlt(_localctx, 5);
				{
				setState(1183);
				match(RegularExpressionLiteral);
				}
				break;
			case DecimalIntegerLiteral:
			case HexIntegerLiteral:
			case BinaryIntegerLiteral:
			case DecimalLiteral:
				enterOuterAlt(_localctx, 6);
				{
				setState(1184);
				literal_numeric();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Literal_numericContext extends ParserRuleContext {
		public TerminalNode DecimalIntegerLiteral() { return getToken(XLangParser.DecimalIntegerLiteral, 0); }
		public TerminalNode HexIntegerLiteral() { return getToken(XLangParser.HexIntegerLiteral, 0); }
		public TerminalNode DecimalLiteral() { return getToken(XLangParser.DecimalLiteral, 0); }
		public TerminalNode BinaryIntegerLiteral() { return getToken(XLangParser.BinaryIntegerLiteral, 0); }
		public Literal_numericContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_literal_numeric; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterLiteral_numeric(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitLiteral_numeric(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitLiteral_numeric(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Literal_numericContext literal_numeric() throws RecognitionException {
		Literal_numericContext _localctx = new Literal_numericContext(_ctx, getState());
		enterRule(_localctx, 234, RULE_literal_numeric);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1187);
			_la = _input.LA(1);
			if ( !(((((_la - 61)) & ~0x3f) == 0 && ((1L << (_la - 61)) & 15L) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Literal_stringContext extends ParserRuleContext {
		public TerminalNode StringLiteral() { return getToken(XLangParser.StringLiteral, 0); }
		public Literal_stringContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_literal_string; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).enterLiteral_string(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof XLangParserListener ) ((XLangParserListener)listener).exitLiteral_string(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof XLangParserVisitor ) return ((XLangParserVisitor<? extends T>)visitor).visitLiteral_string(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Literal_stringContext literal_string() throws RecognitionException {
		Literal_stringContext _localctx = new Literal_stringContext(_ctx, getState());
		enterRule(_localctx, 236, RULE_literal_string);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1189);
			match(StringLiteral);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public boolean sempred(RuleContext _localctx, int ruleIndex, int predIndex) {
		switch (ruleIndex) {
		case 23:
			return expressionStatement_sempred((ExpressionStatementContext)_localctx, predIndex);
		case 25:
			return statement_iteration_sempred((Statement_iterationContext)_localctx, predIndex);
		case 29:
			return returnStatement_sempred((ReturnStatementContext)_localctx, predIndex);
		case 35:
			return throwStatement_sempred((ThrowStatementContext)_localctx, predIndex);
		case 65:
			return expression_single_sempred((Expression_singleContext)_localctx, predIndex);
		case 72:
			return eos___sempred((Eos__Context)_localctx, predIndex);
		case 82:
			return namedTypeNode_sempred((NamedTypeNodeContext)_localctx, predIndex);
		}
		return true;
	}
	private boolean expressionStatement_sempred(ExpressionStatementContext _localctx, int predIndex) {
		switch (predIndex) {
		case 0:
			return this.notOpenBraceAndNotFunction();
		}
		return true;
	}
	private boolean statement_iteration_sempred(Statement_iterationContext _localctx, int predIndex) {
		switch (predIndex) {
		case 1:
			return this.p("of");
		}
		return true;
	}
	private boolean returnStatement_sempred(ReturnStatementContext _localctx, int predIndex) {
		switch (predIndex) {
		case 2:
			return this.notLineTerminator();
		}
		return true;
	}
	private boolean throwStatement_sempred(ThrowStatementContext _localctx, int predIndex) {
		switch (predIndex) {
		case 3:
			return this.notLineTerminator();
		}
		return true;
	}
	private boolean expression_single_sempred(Expression_singleContext _localctx, int predIndex) {
		switch (predIndex) {
		case 4:
			return this.notLineTerminator();
		case 5:
			return this.supportCpExpr();
		case 6:
			return precpred(_ctx, 24);
		case 7:
			return precpred(_ctx, 23);
		case 8:
			return precpred(_ctx, 22);
		case 9:
			return precpred(_ctx, 21);
		case 10:
			return precpred(_ctx, 20);
		case 11:
			return precpred(_ctx, 18);
		case 12:
			return precpred(_ctx, 17);
		case 13:
			return precpred(_ctx, 16);
		case 14:
			return precpred(_ctx, 15);
		case 15:
			return precpred(_ctx, 14);
		case 16:
			return precpred(_ctx, 13);
		case 17:
			return precpred(_ctx, 12);
		case 18:
			return precpred(_ctx, 11);
		case 19:
			return precpred(_ctx, 39);
		case 20:
			return precpred(_ctx, 38);
		case 21:
			return precpred(_ctx, 37);
		case 22:
			return this.notLineTerminator();
		case 23:
			return precpred(_ctx, 35);
		case 24:
			return precpred(_ctx, 34);
		case 25:
			return this.notLineTerminator();
		case 26:
			return precpred(_ctx, 33);
		case 27:
			return this.notLineTerminator();
		case 28:
			return precpred(_ctx, 19);
		case 29:
			return precpred(_ctx, 1);
		}
		return true;
	}
	private boolean eos___sempred(Eos__Context _localctx, int predIndex) {
		switch (predIndex) {
		case 30:
			return this.lineTerminatorAhead();
		case 31:
			return this.closeBrace();
		}
		return true;
	}
	private boolean namedTypeNode_sempred(NamedTypeNodeContext _localctx, int predIndex) {
		switch (predIndex) {
		case 32:
			return precpred(_ctx, 1);
		case 33:
			return notLineTerminator();
		}
		return true;
	}

	public static final String _serializedATN =
		"\u0004\u0001~\u04a8\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
		"\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002"+
		"\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007\u0002"+
		"\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002\u000b\u0007\u000b\u0002"+
		"\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e\u0002\u000f\u0007\u000f"+
		"\u0002\u0010\u0007\u0010\u0002\u0011\u0007\u0011\u0002\u0012\u0007\u0012"+
		"\u0002\u0013\u0007\u0013\u0002\u0014\u0007\u0014\u0002\u0015\u0007\u0015"+
		"\u0002\u0016\u0007\u0016\u0002\u0017\u0007\u0017\u0002\u0018\u0007\u0018"+
		"\u0002\u0019\u0007\u0019\u0002\u001a\u0007\u001a\u0002\u001b\u0007\u001b"+
		"\u0002\u001c\u0007\u001c\u0002\u001d\u0007\u001d\u0002\u001e\u0007\u001e"+
		"\u0002\u001f\u0007\u001f\u0002 \u0007 \u0002!\u0007!\u0002\"\u0007\"\u0002"+
		"#\u0007#\u0002$\u0007$\u0002%\u0007%\u0002&\u0007&\u0002\'\u0007\'\u0002"+
		"(\u0007(\u0002)\u0007)\u0002*\u0007*\u0002+\u0007+\u0002,\u0007,\u0002"+
		"-\u0007-\u0002.\u0007.\u0002/\u0007/\u00020\u00070\u00021\u00071\u0002"+
		"2\u00072\u00023\u00073\u00024\u00074\u00025\u00075\u00026\u00076\u0002"+
		"7\u00077\u00028\u00078\u00029\u00079\u0002:\u0007:\u0002;\u0007;\u0002"+
		"<\u0007<\u0002=\u0007=\u0002>\u0007>\u0002?\u0007?\u0002@\u0007@\u0002"+
		"A\u0007A\u0002B\u0007B\u0002C\u0007C\u0002D\u0007D\u0002E\u0007E\u0002"+
		"F\u0007F\u0002G\u0007G\u0002H\u0007H\u0002I\u0007I\u0002J\u0007J\u0002"+
		"K\u0007K\u0002L\u0007L\u0002M\u0007M\u0002N\u0007N\u0002O\u0007O\u0002"+
		"P\u0007P\u0002Q\u0007Q\u0002R\u0007R\u0002S\u0007S\u0002T\u0007T\u0002"+
		"U\u0007U\u0002V\u0007V\u0002W\u0007W\u0002X\u0007X\u0002Y\u0007Y\u0002"+
		"Z\u0007Z\u0002[\u0007[\u0002\\\u0007\\\u0002]\u0007]\u0002^\u0007^\u0002"+
		"_\u0007_\u0002`\u0007`\u0002a\u0007a\u0002b\u0007b\u0002c\u0007c\u0002"+
		"d\u0007d\u0002e\u0007e\u0002f\u0007f\u0002g\u0007g\u0002h\u0007h\u0002"+
		"i\u0007i\u0002j\u0007j\u0002k\u0007k\u0002l\u0007l\u0002m\u0007m\u0002"+
		"n\u0007n\u0002o\u0007o\u0002p\u0007p\u0002q\u0007q\u0002r\u0007r\u0002"+
		"s\u0007s\u0002t\u0007t\u0002u\u0007u\u0002v\u0007v\u0001\u0000\u0001\u0000"+
		"\u0001\u0000\u0001\u0001\u0005\u0001\u00f3\b\u0001\n\u0001\f\u0001\u00f6"+
		"\t\u0001\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0003\u0002\u0101\b\u0002\u0001"+
		"\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001"+
		"\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001"+
		"\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0003\u0003\u0113\b\u0003\u0001"+
		"\u0004\u0001\u0004\u0003\u0004\u0117\b\u0004\u0001\u0005\u0001\u0005\u0001"+
		"\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001"+
		"\u0006\u0001\u0006\u0001\u0006\u0005\u0006\u0124\b\u0006\n\u0006\f\u0006"+
		"\u0127\t\u0006\u0001\u0007\u0001\u0007\u0001\u0007\u0003\u0007\u012c\b"+
		"\u0007\u0001\b\u0001\b\u0001\b\u0001\b\u0003\b\u0132\b\b\u0001\b\u0001"+
		"\b\u0001\t\u0001\t\u0003\t\u0138\b\t\u0001\n\u0001\n\u0001\u000b\u0001"+
		"\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001"+
		"\u000b\u0001\f\u0001\f\u0001\f\u0005\f\u0147\b\f\n\f\f\f\u014a\t\f\u0001"+
		"\r\u0001\r\u0001\r\u0003\r\u014f\b\r\u0001\u000e\u0001\u000e\u0001\u000e"+
		"\u0001\u000e\u0001\u000f\u0005\u000f\u0156\b\u000f\n\u000f\f\u000f\u0159"+
		"\t\u000f\u0001\u0010\u0005\u0010\u015c\b\u0010\n\u0010\f\u0010\u015f\t"+
		"\u0010\u0001\u0011\u0001\u0011\u0001\u0011\u0001\u0011\u0001\u0012\u0001"+
		"\u0012\u0001\u0012\u0001\u0012\u0001\u0013\u0001\u0013\u0001\u0014\u0001"+
		"\u0014\u0001\u0014\u0005\u0014\u016e\b\u0014\n\u0014\f\u0014\u0171\t\u0014"+
		"\u0001\u0015\u0001\u0015\u0003\u0015\u0175\b\u0015\u0001\u0015\u0003\u0015"+
		"\u0178\b\u0015\u0001\u0016\u0001\u0016\u0001\u0017\u0001\u0017\u0001\u0017"+
		"\u0001\u0017\u0001\u0018\u0001\u0018\u0001\u0018\u0001\u0018\u0001\u0018"+
		"\u0001\u0018\u0001\u0018\u0003\u0018\u0187\b\u0018\u0001\u0019\u0001\u0019"+
		"\u0001\u0019\u0001\u0019\u0001\u0019\u0001\u0019\u0001\u0019\u0001\u0019"+
		"\u0001\u0019\u0001\u0019\u0001\u0019\u0001\u0019\u0001\u0019\u0001\u0019"+
		"\u0001\u0019\u0001\u0019\u0001\u0019\u0003\u0019\u019a\b\u0019\u0001\u0019"+
		"\u0001\u0019\u0003\u0019\u019e\b\u0019\u0001\u0019\u0001\u0019\u0003\u0019"+
		"\u01a2\b\u0019\u0001\u0019\u0001\u0019\u0001\u0019\u0001\u0019\u0001\u0019"+
		"\u0001\u0019\u0001\u0019\u0001\u0019\u0001\u0019\u0001\u0019\u0001\u0019"+
		"\u0001\u0019\u0001\u0019\u0001\u0019\u0001\u0019\u0001\u0019\u0001\u0019"+
		"\u0001\u0019\u0001\u0019\u0003\u0019\u01b7\b\u0019\u0001\u001a\u0001\u001a"+
		"\u0001\u001a\u0001\u001a\u0003\u001a\u01bd\b\u001a\u0001\u001b\u0001\u001b"+
		"\u0001\u001b\u0001\u001c\u0001\u001c\u0001\u001c\u0001\u001d\u0001\u001d"+
		"\u0001\u001d\u0003\u001d\u01c8\b\u001d\u0001\u001d\u0001\u001d\u0001\u001e"+
		"\u0001\u001e\u0001\u001e\u0001\u001e\u0001\u001e\u0001\u001f\u0001\u001f"+
		"\u0003\u001f\u01d3\b\u001f\u0001 \u0001 \u0001 \u0001 \u0001 \u0001 \u0001"+
		" \u0001 \u0001 \u0003 \u01de\b \u0001 \u0001 \u0001!\u0004!\u01e3\b!\u000b"+
		"!\f!\u01e4\u0001\"\u0001\"\u0001\"\u0001\"\u0003\"\u01eb\b\"\u0001#\u0001"+
		"#\u0001#\u0001#\u0001#\u0001$\u0001$\u0001$\u0003$\u01f5\b$\u0001$\u0003"+
		"$\u01f8\b$\u0001%\u0001%\u0001%\u0001%\u0001%\u0003%\u01ff\b%\u0001%\u0001"+
		"%\u0001%\u0001&\u0001&\u0001&\u0001\'\u0003\'\u0208\b\'\u0001\'\u0001"+
		"\'\u0001\'\u0001\'\u0003\'\u020e\b\'\u0001\'\u0001\'\u0003\'\u0212\b\'"+
		"\u0001\'\u0001\'\u0001(\u0001(\u0001(\u0005(\u0219\b(\n(\f(\u021c\t(\u0001"+
		")\u0003)\u021f\b)\u0001)\u0001)\u0003)\u0223\b)\u0001)\u0003)\u0226\b"+
		")\u0001*\u0001*\u0001*\u0003*\u022b\b*\u0001+\u0001+\u0001+\u0001,\u0001"+
		",\u0001,\u0001,\u0001,\u0003,\u0235\b,\u0001,\u0001,\u0001,\u0001,\u0001"+
		",\u0003,\u023c\b,\u0001,\u0001,\u0003,\u0240\b,\u0001-\u0001-\u0001-\u0005"+
		"-\u0245\b-\n-\f-\u0248\t-\u0001.\u0001.\u0003.\u024c\b.\u0001/\u0001/"+
		"\u0003/\u0250\b/\u0001/\u0001/\u0001/\u0003/\u0255\b/\u0001/\u0001/\u0001"+
		"/\u0001/\u0003/\u025b\b/\u0001/\u0001/\u0003/\u025f\b/\u00010\u00010\u0001"+
		"0\u00050\u0264\b0\n0\f0\u0267\t0\u00011\u00011\u00011\u00011\u00031\u026d"+
		"\b1\u00011\u00011\u00031\u0271\b1\u00031\u0273\b1\u00012\u00012\u0001"+
		"2\u00032\u0278\b2\u00013\u00013\u00033\u027c\b3\u00013\u00013\u00014\u0001"+
		"4\u00014\u00054\u0283\b4\n4\f4\u0286\t4\u00014\u00034\u0289\b4\u00015"+
		"\u00015\u00035\u028d\b5\u00016\u00016\u00016\u00017\u00017\u00037\u0294"+
		"\b7\u00017\u00037\u0297\b7\u00017\u00017\u00018\u00018\u00018\u00058\u029e"+
		"\b8\n8\f8\u02a1\t8\u00019\u00019\u00039\u02a5\b9\u0001:\u0001:\u0001:"+
		"\u0001:\u0001:\u0001:\u0001:\u0001:\u0001:\u0001:\u0001:\u0003:\u02b2"+
		"\b:\u0001;\u0001;\u0001;\u0001;\u0005;\u02b8\b;\n;\f;\u02bb\t;\u0001;"+
		"\u0003;\u02be\b;\u0003;\u02c0\b;\u0001;\u0001;\u0001<\u0001<\u0001=\u0001"+
		"=\u0001=\u0005=\u02c9\b=\n=\f=\u02cc\t=\u0001>\u0001>\u0001>\u0001>\u0001"+
		"?\u0001?\u0001?\u0001?\u0003?\u02d6\b?\u0001@\u0001@\u0001@\u0005@\u02db"+
		"\b@\n@\f@\u02de\t@\u0001A\u0001A\u0001A\u0001A\u0001A\u0003A\u02e5\bA"+
		"\u0001A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001"+
		"A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001"+
		"A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001"+
		"A\u0001A\u0001A\u0001A\u0001A\u0001A\u0003A\u030a\bA\u0001A\u0001A\u0001"+
		"A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001"+
		"A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001"+
		"A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001"+
		"A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001"+
		"A\u0001A\u0003A\u0338\bA\u0001A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001"+
		"A\u0001A\u0001A\u0001A\u0001A\u0001A\u0003A\u0346\bA\u0001A\u0001A\u0001"+
		"A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001A\u0001"+
		"A\u0005A\u0355\bA\nA\fA\u0358\tA\u0001B\u0001B\u0001C\u0001C\u0003C\u035e"+
		"\bC\u0001C\u0001C\u0003C\u0362\bC\u0001C\u0001C\u0001C\u0001C\u0001C\u0001"+
		"C\u0003C\u036a\bC\u0001D\u0001D\u0001E\u0001E\u0003E\u0370\bE\u0001F\u0001"+
		"F\u0001F\u0001F\u0001F\u0001F\u0001F\u0001F\u0001F\u0003F\u037b\bF\u0001"+
		"G\u0001G\u0001H\u0001H\u0001H\u0001H\u0003H\u0383\bH\u0001I\u0001I\u0001"+
		"I\u0001I\u0005I\u0389\bI\nI\fI\u038c\tI\u0003I\u038e\bI\u0001I\u0001I"+
		"\u0001J\u0001J\u0001J\u0001J\u0001J\u0003J\u0397\bJ\u0001K\u0001K\u0001"+
		"K\u0001K\u0005K\u039d\bK\nK\fK\u03a0\tK\u0003K\u03a2\bK\u0001K\u0001K"+
		"\u0001L\u0001L\u0001L\u0001L\u0001L\u0003L\u03ab\bL\u0001M\u0001M\u0003"+
		"M\u03af\bM\u0001N\u0001N\u0001N\u0004N\u03b4\bN\u000bN\fN\u03b5\u0001"+
		"O\u0001O\u0001O\u0004O\u03bb\bO\u000bO\fO\u03bc\u0001P\u0001P\u0001P\u0001"+
		"P\u0001Q\u0001Q\u0001Q\u0005Q\u03c6\bQ\nQ\fQ\u03c9\tQ\u0001R\u0001R\u0001"+
		"R\u0001R\u0003R\u03cf\bR\u0001R\u0001R\u0001R\u0001R\u0005R\u03d5\bR\n"+
		"R\fR\u03d8\tR\u0001S\u0001S\u0001T\u0001T\u0003T\u03de\bT\u0001U\u0001"+
		"U\u0001U\u0003U\u03e3\bU\u0001U\u0001U\u0001V\u0001V\u0001V\u0005V\u03ea"+
		"\bV\nV\fV\u03ed\tV\u0001W\u0003W\u03f0\bW\u0001W\u0001W\u0003W\u03f4\b"+
		"W\u0001W\u0001W\u0001W\u0001W\u0001X\u0003X\u03fb\bX\u0001X\u0003X\u03fe"+
		"\bX\u0001X\u0001X\u0003X\u0402\bX\u0001X\u0003X\u0405\bX\u0001Y\u0001"+
		"Y\u0001Y\u0001Z\u0001Z\u0001Z\u0001[\u0001[\u0001[\u0005[\u0410\b[\n["+
		"\f[\u0413\t[\u0001\\\u0001\\\u0003\\\u0417\b\\\u0001\\\u0003\\\u041a\b"+
		"\\\u0001]\u0003]\u041d\b]\u0001]\u0001]\u0001]\u0003]\u0422\b]\u0001]"+
		"\u0001]\u0001]\u0003]\u0427\b]\u0001^\u0001^\u0001^\u0001^\u0001^\u0003"+
		"^\u042e\b^\u0001^\u0001^\u0001_\u0001_\u0001_\u0005_\u0435\b_\n_\f_\u0438"+
		"\t_\u0001`\u0001`\u0001`\u0003`\u043d\b`\u0001a\u0001a\u0001b\u0004b\u0442"+
		"\bb\u000bb\fb\u0443\u0001c\u0001c\u0001c\u0001c\u0001c\u0001c\u0003c\u044c"+
		"\bc\u0001d\u0001d\u0001d\u0003d\u0451\bd\u0003d\u0453\bd\u0001d\u0001"+
		"d\u0001e\u0001e\u0001e\u0005e\u045a\be\ne\fe\u045d\te\u0001f\u0001f\u0001"+
		"f\u0001f\u0001g\u0001g\u0003g\u0465\bg\u0001g\u0003g\u0468\bg\u0001g\u0001"+
		"g\u0001h\u0001h\u0001h\u0005h\u046f\bh\nh\fh\u0472\th\u0001i\u0001i\u0001"+
		"i\u0001i\u0003i\u0478\bi\u0001j\u0001j\u0001j\u0003j\u047d\bj\u0001k\u0001"+
		"k\u0001l\u0001l\u0001m\u0001m\u0003m\u0485\bm\u0001n\u0001n\u0003n\u0489"+
		"\bn\u0001o\u0001o\u0001p\u0001p\u0001q\u0001q\u0003q\u0491\bq\u0001r\u0001"+
		"r\u0001r\u0001r\u0001r\u0003r\u0498\br\u0001s\u0001s\u0001t\u0001t\u0001"+
		"t\u0001t\u0001t\u0001t\u0003t\u04a2\bt\u0001u\u0001u\u0001v\u0001v\u0001"+
		"v\u0000\u0002\u0082\u00a4w\u0000\u0002\u0004\u0006\b\n\f\u000e\u0010\u0012"+
		"\u0014\u0016\u0018\u001a\u001c\u001e \"$&(*,.02468:<>@BDFHJLNPRTVXZ\\"+
		"^`bdfhjlnprtvxz|~\u0080\u0082\u0084\u0086\u0088\u008a\u008c\u008e\u0090"+
		"\u0092\u0094\u0096\u0098\u009a\u009c\u009e\u00a0\u00a2\u00a4\u00a6\u00a8"+
		"\u00aa\u00ac\u00ae\u00b0\u00b2\u00b4\u00b6\u00b8\u00ba\u00bc\u00be\u00c0"+
		"\u00c2\u00c4\u00c6\u00c8\u00ca\u00cc\u00ce\u00d0\u00d2\u00d4\u00d6\u00d8"+
		"\u00da\u00dc\u00de\u00e0\u00e2\u00e4\u00e6\u00e8\u00ea\u00ec\u0000\u000e"+
		"\u0002\u0000ddhh\u0001\u0000\u001a\u001c\u0001\u0000\u0016\u0017\u0001"+
		"\u0000\u001d\u001f\u0001\u0000 #\u0001\u0000$\'\u0002\u0000++;;\u0002"+
		"\u0000,,<<\u0002\u0000\u000f\u000f\u0013\u0013\u0002\u0000\f\f-7\u0002"+
		"\u0000LLos\u0003\u0000\\\\ttzz\u0003\u0000A[]]`t\u0001\u0000=@\u04f8\u0000"+
		"\u00ee\u0001\u0000\u0000\u0000\u0002\u00f4\u0001\u0000\u0000\u0000\u0004"+
		"\u0100\u0001\u0000\u0000\u0000\u0006\u0112\u0001\u0000\u0000\u0000\b\u0116"+
		"\u0001\u0000\u0000\u0000\n\u0118\u0001\u0000\u0000\u0000\f\u0120\u0001"+
		"\u0000\u0000\u0000\u000e\u0128\u0001\u0000\u0000\u0000\u0010\u012d\u0001"+
		"\u0000\u0000\u0000\u0012\u0137\u0001\u0000\u0000\u0000\u0014\u0139\u0001"+
		"\u0000\u0000\u0000\u0016\u013b\u0001\u0000\u0000\u0000\u0018\u0143\u0001"+
		"\u0000\u0000\u0000\u001a\u014b\u0001\u0000\u0000\u0000\u001c\u0150\u0001"+
		"\u0000\u0000\u0000\u001e\u0157\u0001\u0000\u0000\u0000 \u015d\u0001\u0000"+
		"\u0000\u0000\"\u0160\u0001\u0000\u0000\u0000$\u0164\u0001\u0000\u0000"+
		"\u0000&\u0168\u0001\u0000\u0000\u0000(\u016a\u0001\u0000\u0000\u0000*"+
		"\u0172\u0001\u0000\u0000\u0000,\u0179\u0001\u0000\u0000\u0000.\u017b\u0001"+
		"\u0000\u0000\u00000\u017f\u0001\u0000\u0000\u00002\u01b6\u0001\u0000\u0000"+
		"\u00004\u01bc\u0001\u0000\u0000\u00006\u01be\u0001\u0000\u0000\u00008"+
		"\u01c1\u0001\u0000\u0000\u0000:\u01c4\u0001\u0000\u0000\u0000<\u01cb\u0001"+
		"\u0000\u0000\u0000>\u01d2\u0001\u0000\u0000\u0000@\u01d4\u0001\u0000\u0000"+
		"\u0000B\u01e2\u0001\u0000\u0000\u0000D\u01e6\u0001\u0000\u0000\u0000F"+
		"\u01ec\u0001\u0000\u0000\u0000H\u01f1\u0001\u0000\u0000\u0000J\u01f9\u0001"+
		"\u0000\u0000\u0000L\u0203\u0001\u0000\u0000\u0000N\u0207\u0001\u0000\u0000"+
		"\u0000P\u0215\u0001\u0000\u0000\u0000R\u021e\u0001\u0000\u0000\u0000T"+
		"\u022a\u0001\u0000\u0000\u0000V\u022c\u0001\u0000\u0000\u0000X\u023f\u0001"+
		"\u0000\u0000\u0000Z\u0241\u0001\u0000\u0000\u0000\\\u0249\u0001\u0000"+
		"\u0000\u0000^\u025e\u0001\u0000\u0000\u0000`\u0260\u0001\u0000\u0000\u0000"+
		"b\u0272\u0001\u0000\u0000\u0000d\u0274\u0001\u0000\u0000\u0000f\u0279"+
		"\u0001\u0000\u0000\u0000h\u027f\u0001\u0000\u0000\u0000j\u028c\u0001\u0000"+
		"\u0000\u0000l\u028e\u0001\u0000\u0000\u0000n\u0291\u0001\u0000\u0000\u0000"+
		"p\u029a\u0001\u0000\u0000\u0000r\u02a4\u0001\u0000\u0000\u0000t\u02b1"+
		"\u0001\u0000\u0000\u0000v\u02b3\u0001\u0000\u0000\u0000x\u02c3\u0001\u0000"+
		"\u0000\u0000z\u02c5\u0001\u0000\u0000\u0000|\u02cd\u0001\u0000\u0000\u0000"+
		"~\u02d5\u0001\u0000\u0000\u0000\u0080\u02d7\u0001\u0000\u0000\u0000\u0082"+
		"\u0309\u0001\u0000\u0000\u0000\u0084\u0359\u0001\u0000\u0000\u0000\u0086"+
		"\u0369\u0001\u0000\u0000\u0000\u0088\u036b\u0001\u0000\u0000\u0000\u008a"+
		"\u036f\u0001\u0000\u0000\u0000\u008c\u037a\u0001\u0000\u0000\u0000\u008e"+
		"\u037c\u0001\u0000\u0000\u0000\u0090\u0382\u0001\u0000\u0000\u0000\u0092"+
		"\u0384\u0001\u0000\u0000\u0000\u0094\u0391\u0001\u0000\u0000\u0000\u0096"+
		"\u0398\u0001\u0000\u0000\u0000\u0098\u03aa\u0001\u0000\u0000\u0000\u009a"+
		"\u03ae\u0001\u0000\u0000\u0000\u009c\u03b0\u0001\u0000\u0000\u0000\u009e"+
		"\u03b7\u0001\u0000\u0000\u0000\u00a0\u03be\u0001\u0000\u0000\u0000\u00a2"+
		"\u03c2\u0001\u0000\u0000\u0000\u00a4\u03ce\u0001\u0000\u0000\u0000\u00a6"+
		"\u03d9\u0001\u0000\u0000\u0000\u00a8\u03db\u0001\u0000\u0000\u0000\u00aa"+
		"\u03df\u0001\u0000\u0000\u0000\u00ac\u03e6\u0001\u0000\u0000\u0000\u00ae"+
		"\u03ef\u0001\u0000\u0000\u0000\u00b0\u03fa\u0001\u0000\u0000\u0000\u00b2"+
		"\u0406\u0001\u0000\u0000\u0000\u00b4\u0409\u0001\u0000\u0000\u0000\u00b6"+
		"\u040c\u0001\u0000\u0000\u0000\u00b8\u0414\u0001\u0000\u0000\u0000\u00ba"+
		"\u041c\u0001\u0000\u0000\u0000\u00bc\u0428\u0001\u0000\u0000\u0000\u00be"+
		"\u0431\u0001\u0000\u0000\u0000\u00c0\u0439\u0001\u0000\u0000\u0000\u00c2"+
		"\u043e\u0001\u0000\u0000\u0000\u00c4\u0441\u0001\u0000\u0000\u0000\u00c6"+
		"\u0445\u0001\u0000\u0000\u0000\u00c8\u044d\u0001\u0000\u0000\u0000\u00ca"+
		"\u0456\u0001\u0000\u0000\u0000\u00cc\u045e\u0001\u0000\u0000\u0000\u00ce"+
		"\u0462\u0001\u0000\u0000\u0000\u00d0\u046b\u0001\u0000\u0000\u0000\u00d2"+
		"\u0477\u0001\u0000\u0000\u0000\u00d4\u0479\u0001\u0000\u0000\u0000\u00d6"+
		"\u047e\u0001\u0000\u0000\u0000\u00d8\u0480\u0001\u0000\u0000\u0000\u00da"+
		"\u0484\u0001\u0000\u0000\u0000\u00dc\u0488\u0001\u0000\u0000\u0000\u00de"+
		"\u048a\u0001\u0000\u0000\u0000\u00e0\u048c\u0001\u0000\u0000\u0000\u00e2"+
		"\u0490\u0001\u0000\u0000\u0000\u00e4\u0497\u0001\u0000\u0000\u0000\u00e6"+
		"\u0499\u0001\u0000\u0000\u0000\u00e8\u04a1\u0001\u0000\u0000\u0000\u00ea"+
		"\u04a3\u0001\u0000\u0000\u0000\u00ec\u04a5\u0001\u0000\u0000\u0000\u00ee"+
		"\u00ef\u0003\u0002\u0001\u0000\u00ef\u00f0\u0005\u0000\u0000\u0001\u00f0"+
		"\u0001\u0001\u0000\u0000\u0000\u00f1\u00f3\u0003\u0004\u0002\u0000\u00f2"+
		"\u00f1\u0001\u0000\u0000\u0000\u00f3\u00f6\u0001\u0000\u0000\u0000\u00f4"+
		"\u00f2\u0001\u0000\u0000\u0000\u00f4\u00f5\u0001\u0000\u0000\u0000\u00f5"+
		"\u0003\u0001\u0000\u0000\u0000\u00f6\u00f4\u0001\u0000\u0000\u0000\u00f7"+
		"\u0101\u0003\b\u0004\u0000\u00f8\u0101\u0003\u0014\n\u0000\u00f9\u00fa"+
		"\u0005e\u0000\u0000\u00fa\u0101\u0003N\'\u0000\u00fb\u00fc\u0005e\u0000"+
		"\u0000\u00fc\u0101\u0003\u00ba]\u0000\u00fd\u00fe\u0005e\u0000\u0000\u00fe"+
		"\u0101\u0003\"\u0011\u0000\u00ff\u0101\u0003\u0006\u0003\u0000\u0100\u00f7"+
		"\u0001\u0000\u0000\u0000\u0100\u00f8\u0001\u0000\u0000\u0000\u0100\u00f9"+
		"\u0001\u0000\u0000\u0000\u0100\u00fb\u0001\u0000\u0000\u0000\u0100\u00fd"+
		"\u0001\u0000\u0000\u0000\u0100\u00ff\u0001\u0000\u0000\u0000\u0101\u0005"+
		"\u0001\u0000\u0000\u0000\u0102\u0113\u0003\u001c\u000e\u0000\u0103\u0113"+
		"\u0003$\u0012\u0000\u0104\u0113\u0003,\u0016\u0000\u0105\u0113\u00030"+
		"\u0018\u0000\u0106\u0113\u00032\u0019\u0000\u0107\u0113\u00036\u001b\u0000"+
		"\u0108\u0113\u00038\u001c\u0000\u0109\u0113\u0003:\u001d\u0000\u010a\u0113"+
		"\u0003@ \u0000\u010b\u0113\u0003F#\u0000\u010c\u0113\u0003H$\u0000\u010d"+
		"\u0113\u0003N\'\u0000\u010e\u0113\u0003\u00ba]\u0000\u010f\u0113\u0003"+
		"\u00bc^\u0000\u0110\u0113\u0003.\u0017\u0000\u0111\u0113\u0003<\u001e"+
		"\u0000\u0112\u0102\u0001\u0000\u0000\u0000\u0112\u0103\u0001\u0000\u0000"+
		"\u0000\u0112\u0104\u0001\u0000\u0000\u0000\u0112\u0105\u0001\u0000\u0000"+
		"\u0000\u0112\u0106\u0001\u0000\u0000\u0000\u0112\u0107\u0001\u0000\u0000"+
		"\u0000\u0112\u0108\u0001\u0000\u0000\u0000\u0112\u0109\u0001\u0000\u0000"+
		"\u0000\u0112\u010a\u0001\u0000\u0000\u0000\u0112\u010b\u0001\u0000\u0000"+
		"\u0000\u0112\u010c\u0001\u0000\u0000\u0000\u0112\u010d\u0001\u0000\u0000"+
		"\u0000\u0112\u010e\u0001\u0000\u0000\u0000\u0112\u010f\u0001\u0000\u0000"+
		"\u0000\u0112\u0110\u0001\u0000\u0000\u0000\u0112\u0111\u0001\u0000\u0000"+
		"\u0000\u0113\u0007\u0001\u0000\u0000\u0000\u0114\u0117\u0003\n\u0005\u0000"+
		"\u0115\u0117\u0003\u0010\b\u0000\u0116\u0114\u0001\u0000\u0000\u0000\u0116"+
		"\u0115\u0001\u0000\u0000\u0000\u0117\t\u0001\u0000\u0000\u0000\u0118\u0119"+
		"\u0005f\u0000\u0000\u0119\u011a\u0005\b\u0000\u0000\u011a\u011b\u0003"+
		"\f\u0006\u0000\u011b\u011c\u0005\t\u0000\u0000\u011c\u011d\u0005\\\u0000"+
		"\u0000\u011d\u011e\u0003\u00ecv\u0000\u011e\u011f\u0003\u0090H\u0000\u011f"+
		"\u000b\u0001\u0000\u0000\u0000\u0120\u0125\u0003\u000e\u0007\u0000\u0121"+
		"\u0122\u0005\u000b\u0000\u0000\u0122\u0124\u0003\u000e\u0007\u0000\u0123"+
		"\u0121\u0001\u0000\u0000\u0000\u0124\u0127\u0001\u0000\u0000\u0000\u0125"+
		"\u0123\u0001\u0000\u0000\u0000\u0125\u0126\u0001\u0000\u0000\u0000\u0126"+
		"\r\u0001\u0000\u0000\u0000\u0127\u0125\u0001\u0000\u0000\u0000\u0128\u012b"+
		"\u0003\u00e0p\u0000\u0129\u012a\u0005[\u0000\u0000\u012a\u012c\u0003\u00e0"+
		"p\u0000\u012b\u0129\u0001\u0000\u0000\u0000\u012b\u012c\u0001\u0000\u0000"+
		"\u0000\u012c\u000f\u0001\u0000\u0000\u0000\u012d\u012e\u0005f\u0000\u0000"+
		"\u012e\u0131\u0003\u0012\t\u0000\u012f\u0130\u0005[\u0000\u0000\u0130"+
		"\u0132\u0003\u00e0p\u0000\u0131\u012f\u0001\u0000\u0000\u0000\u0131\u0132"+
		"\u0001\u0000\u0000\u0000\u0132\u0133\u0001\u0000\u0000\u0000\u0133\u0134"+
		"\u0003\u0090H\u0000\u0134\u0011\u0001\u0000\u0000\u0000\u0135\u0138\u0003"+
		"\u00d4j\u0000\u0136\u0138\u0003\u00ecv\u0000\u0137\u0135\u0001\u0000\u0000"+
		"\u0000\u0137\u0136\u0001\u0000\u0000\u0000\u0138\u0013\u0001\u0000\u0000"+
		"\u0000\u0139\u013a\u0003\u0016\u000b\u0000\u013a\u0015\u0001\u0000\u0000"+
		"\u0000\u013b\u013c\u0005e\u0000\u0000\u013c\u013d\u0005\b\u0000\u0000"+
		"\u013d\u013e\u0003\u0018\f\u0000\u013e\u013f\u0005\t\u0000\u0000\u013f"+
		"\u0140\u0005\\\u0000\u0000\u0140\u0141\u0003\u00ecv\u0000\u0141\u0142"+
		"\u0003\u0090H\u0000\u0142\u0017\u0001\u0000\u0000\u0000\u0143\u0148\u0003"+
		"\u001a\r\u0000\u0144\u0145\u0005\u000b\u0000\u0000\u0145\u0147\u0003\u001a"+
		"\r\u0000\u0146\u0144\u0001\u0000\u0000\u0000\u0147\u014a\u0001\u0000\u0000"+
		"\u0000\u0148\u0146\u0001\u0000\u0000\u0000\u0148\u0149\u0001\u0000\u0000"+
		"\u0000\u0149\u0019\u0001\u0000\u0000\u0000\u014a\u0148\u0001\u0000\u0000"+
		"\u0000\u014b\u014e\u0003\u00e0p\u0000\u014c\u014d\u0005[\u0000\u0000\u014d"+
		"\u014f\u0003\u00e0p\u0000\u014e\u014c\u0001\u0000\u0000\u0000\u014e\u014f"+
		"\u0001\u0000\u0000\u0000\u014f\u001b\u0001\u0000\u0000\u0000\u0150\u0151"+
		"\u0005\b\u0000\u0000\u0151\u0152\u0003\u001e\u000f\u0000\u0152\u0153\u0005"+
		"\t\u0000\u0000\u0153\u001d\u0001\u0000\u0000\u0000\u0154\u0156\u0003\u0006"+
		"\u0003\u0000\u0155\u0154\u0001\u0000\u0000\u0000\u0156\u0159\u0001\u0000"+
		"\u0000\u0000\u0157\u0155\u0001\u0000\u0000\u0000\u0157\u0158\u0001\u0000"+
		"\u0000\u0000\u0158\u001f\u0001\u0000\u0000\u0000\u0159\u0157\u0001\u0000"+
		"\u0000\u0000\u015a\u015c\u0003\u0006\u0003\u0000\u015b\u015a\u0001\u0000"+
		"\u0000\u0000\u015c\u015f\u0001\u0000\u0000\u0000\u015d\u015b\u0001\u0000"+
		"\u0000\u0000\u015d\u015e\u0001\u0000\u0000\u0000\u015e!\u0001\u0000\u0000"+
		"\u0000\u015f\u015d\u0001\u0000\u0000\u0000\u0160\u0161\u0005d\u0000\u0000"+
		"\u0161\u0162\u0003(\u0014\u0000\u0162\u0163\u0003\u0090H\u0000\u0163#"+
		"\u0001\u0000\u0000\u0000\u0164\u0165\u0003&\u0013\u0000\u0165\u0166\u0003"+
		"(\u0014\u0000\u0166\u0167\u0003\u0090H\u0000\u0167%\u0001\u0000\u0000"+
		"\u0000\u0168\u0169\u0007\u0000\u0000\u0000\u0169\'\u0001\u0000\u0000\u0000"+
		"\u016a\u016f\u0003*\u0015\u0000\u016b\u016c\u0005\u000b\u0000\u0000\u016c"+
		"\u016e\u0003*\u0015\u0000\u016d\u016b\u0001\u0000\u0000\u0000\u016e\u0171"+
		"\u0001\u0000\u0000\u0000\u016f\u016d\u0001\u0000\u0000\u0000\u016f\u0170"+
		"\u0001\u0000\u0000\u0000\u0170)\u0001\u0000\u0000\u0000\u0171\u016f\u0001"+
		"\u0000\u0000\u0000\u0172\u0174\u0003T*\u0000\u0173\u0175\u0003\u00b2Y"+
		"\u0000\u0174\u0173\u0001\u0000\u0000\u0000\u0174\u0175\u0001\u0000\u0000"+
		"\u0000\u0175\u0177\u0001\u0000\u0000\u0000\u0176\u0178\u0003V+\u0000\u0177"+
		"\u0176\u0001\u0000\u0000\u0000\u0177\u0178\u0001\u0000\u0000\u0000\u0178"+
		"+\u0001\u0000\u0000\u0000\u0179\u017a\u0005\n\u0000\u0000\u017a-\u0001"+
		"\u0000\u0000\u0000\u017b\u017c\u0004\u0017\u0000\u0000\u017c\u017d\u0003"+
		"\u0082A\u0000\u017d\u017e\u0003\u0090H\u0000\u017e/\u0001\u0000\u0000"+
		"\u0000\u017f\u0180\u0005V\u0000\u0000\u0180\u0181\u0005\u0006\u0000\u0000"+
		"\u0181\u0182\u0003\u0082A\u0000\u0182\u0183\u0005\u0007\u0000\u0000\u0183"+
		"\u0186\u0003\u0006\u0003\u0000\u0184\u0185\u0005F\u0000\u0000\u0185\u0187"+
		"\u0003\u0006\u0003\u0000\u0186\u0184\u0001\u0000\u0000\u0000\u0186\u0187"+
		"\u0001\u0000\u0000\u0000\u01871\u0001\u0000\u0000\u0000\u0188\u0189\u0005"+
		"B\u0000\u0000\u0189\u018a\u0003\u0006\u0003\u0000\u018a\u018b\u0005P\u0000"+
		"\u0000\u018b\u018c\u0005\u0006\u0000\u0000\u018c\u018d\u0003\u0082A\u0000"+
		"\u018d\u018e\u0005\u0007\u0000\u0000\u018e\u018f\u0003\u0090H\u0000\u018f"+
		"\u01b7\u0001\u0000\u0000\u0000\u0190\u0191\u0005P\u0000\u0000\u0191\u0192"+
		"\u0005\u0006\u0000\u0000\u0192\u0193\u0003\u0082A\u0000\u0193\u0194\u0005"+
		"\u0007\u0000\u0000\u0194\u0195\u0003\u0006\u0003\u0000\u0195\u01b7\u0001"+
		"\u0000\u0000\u0000\u0196\u0197\u0005N\u0000\u0000\u0197\u0199\u0005\u0006"+
		"\u0000\u0000\u0198\u019a\u0003~?\u0000\u0199\u0198\u0001\u0000\u0000\u0000"+
		"\u0199\u019a\u0001\u0000\u0000\u0000\u019a\u019b\u0001\u0000\u0000\u0000"+
		"\u019b\u019d\u0005\n\u0000\u0000\u019c\u019e\u0003\u0082A\u0000\u019d"+
		"\u019c\u0001\u0000\u0000\u0000\u019d\u019e\u0001\u0000\u0000\u0000\u019e"+
		"\u019f\u0001\u0000\u0000\u0000\u019f\u01a1\u0005\n\u0000\u0000\u01a0\u01a2"+
		"\u0003x<\u0000\u01a1\u01a0\u0001\u0000\u0000\u0000\u01a1\u01a2\u0001\u0000"+
		"\u0000\u0000\u01a2\u01a3\u0001\u0000\u0000\u0000\u01a3\u01a4\u0005\u0007"+
		"\u0000\u0000\u01a4\u01b7\u0003\u0006\u0003\u0000\u01a5\u01a6\u0005N\u0000"+
		"\u0000\u01a6\u01a7\u0005\u0006\u0000\u0000\u01a7\u01a8\u00034\u001a\u0000"+
		"\u01a8\u01a9\u0005Y\u0000\u0000\u01a9\u01aa\u0003\u0082A\u0000\u01aa\u01ab"+
		"\u0005\u0007\u0000\u0000\u01ab\u01ac\u0003\u0006\u0003\u0000\u01ac\u01b7"+
		"\u0001\u0000\u0000\u0000\u01ad\u01ae\u0005N\u0000\u0000\u01ae\u01af\u0005"+
		"\u0006\u0000\u0000\u01af\u01b0\u00034\u001a\u0000\u01b0\u01b1\u0005z\u0000"+
		"\u0000\u01b1\u01b2\u0004\u0019\u0001\u0000\u01b2\u01b3\u0003\u0082A\u0000"+
		"\u01b3\u01b4\u0005\u0007\u0000\u0000\u01b4\u01b5\u0003\u0006\u0003\u0000"+
		"\u01b5\u01b7\u0001\u0000\u0000\u0000\u01b6\u0188\u0001\u0000\u0000\u0000"+
		"\u01b6\u0190\u0001\u0000\u0000\u0000\u01b6\u0196\u0001\u0000\u0000\u0000"+
		"\u01b6\u01a5\u0001\u0000\u0000\u0000\u01b6\u01ad\u0001\u0000\u0000\u0000"+
		"\u01b73\u0001\u0000\u0000\u0000\u01b8\u01bd\u0003\u00e0p\u0000\u01b9\u01ba"+
		"\u0003&\u0013\u0000\u01ba\u01bb\u0003*\u0015\u0000\u01bb\u01bd\u0001\u0000"+
		"\u0000\u0000\u01bc\u01b8\u0001\u0000\u0000\u0000\u01bc\u01b9\u0001\u0000"+
		"\u0000\u0000\u01bd5\u0001\u0000\u0000\u0000\u01be\u01bf\u0005M\u0000\u0000"+
		"\u01bf\u01c0\u0003\u0090H\u0000\u01c07\u0001\u0000\u0000\u0000\u01c1\u01c2"+
		"\u0005A\u0000\u0000\u01c2\u01c3\u0003\u0090H\u0000\u01c39\u0001\u0000"+
		"\u0000\u0000\u01c4\u01c7\u0005K\u0000\u0000\u01c5\u01c6\u0004\u001d\u0002"+
		"\u0000\u01c6\u01c8\u0003\u0082A\u0000\u01c7\u01c5\u0001\u0000\u0000\u0000"+
		"\u01c7\u01c8\u0001\u0000\u0000\u0000\u01c8\u01c9\u0001\u0000\u0000\u0000"+
		"\u01c9\u01ca\u0003\u0090H\u0000\u01ca;\u0001\u0000\u0000\u0000\u01cb\u01cc"+
		"\u0003>\u001f\u0000\u01cc\u01cd\u0003\u008eG\u0000\u01cd\u01ce\u0003\u0082"+
		"A\u0000\u01ce\u01cf\u0003\u0090H\u0000\u01cf=\u0001\u0000\u0000\u0000"+
		"\u01d0\u01d3\u0003\u00e0p\u0000\u01d1\u01d3\u0003\u008cF\u0000\u01d2\u01d0"+
		"\u0001\u0000\u0000\u0000\u01d2\u01d1\u0001\u0000\u0000\u0000\u01d3?\u0001"+
		"\u0000\u0000\u0000\u01d4\u01d5\u0005O\u0000\u0000\u01d5\u01d6\u0005\u0006"+
		"\u0000\u0000\u01d6\u01d7\u0003\u0082A\u0000\u01d7\u01d8\u0005\u0007\u0000"+
		"\u0000\u01d8\u01d9\u0005\b\u0000\u0000\u01d9\u01dd\u0003B!\u0000\u01da"+
		"\u01db\u0005U\u0000\u0000\u01db\u01dc\u0005\u0010\u0000\u0000\u01dc\u01de"+
		"\u0003 \u0010\u0000\u01dd\u01da\u0001\u0000\u0000\u0000\u01dd\u01de\u0001"+
		"\u0000\u0000\u0000\u01de\u01df\u0001\u0000\u0000\u0000\u01df\u01e0\u0005"+
		"\t\u0000\u0000\u01e0A\u0001\u0000\u0000\u0000\u01e1\u01e3\u0003D\"\u0000"+
		"\u01e2\u01e1\u0001\u0000\u0000\u0000\u01e3\u01e4\u0001\u0000\u0000\u0000"+
		"\u01e4\u01e2\u0001\u0000\u0000\u0000\u01e4\u01e5\u0001\u0000\u0000\u0000"+
		"\u01e5C\u0001\u0000\u0000\u0000\u01e6\u01e7\u0005E\u0000\u0000\u01e7\u01e8"+
		"\u0003\u0082A\u0000\u01e8\u01ea\u0005\u0010\u0000\u0000\u01e9\u01eb\u0003"+
		" \u0010\u0000\u01ea\u01e9\u0001\u0000\u0000\u0000\u01ea\u01eb\u0001\u0000"+
		"\u0000\u0000\u01ebE\u0001\u0000\u0000\u0000\u01ec\u01ed\u0005W\u0000\u0000"+
		"\u01ed\u01ee\u0004#\u0003\u0000\u01ee\u01ef\u0003\u0082A\u0000\u01ef\u01f0"+
		"\u0003\u0090H\u0000\u01f0G\u0001\u0000\u0000\u0000\u01f1\u01f2\u0005Z"+
		"\u0000\u0000\u01f2\u01f4\u0003\u001c\u000e\u0000\u01f3\u01f5\u0003J%\u0000"+
		"\u01f4\u01f3\u0001\u0000\u0000\u0000\u01f4\u01f5\u0001\u0000\u0000\u0000"+
		"\u01f5\u01f7\u0001\u0000\u0000\u0000\u01f6\u01f8\u0003L&\u0000\u01f7\u01f6"+
		"\u0001\u0000\u0000\u0000\u01f7\u01f8\u0001\u0000\u0000\u0000\u01f8I\u0001"+
		"\u0000\u0000\u0000\u01f9\u01fa\u0005I\u0000\u0000\u01fa\u01fb\u0005\u0006"+
		"\u0000\u0000\u01fb\u01fe\u0003\u00e0p\u0000\u01fc\u01fd\u0005\u0010\u0000"+
		"\u0000\u01fd\u01ff\u0003\u00a8T\u0000\u01fe\u01fc\u0001\u0000\u0000\u0000"+
		"\u01fe\u01ff\u0001\u0000\u0000\u0000\u01ff\u0200\u0001\u0000\u0000\u0000"+
		"\u0200\u0201\u0005\u0007\u0000\u0000\u0201\u0202\u0003\u001c\u000e\u0000"+
		"\u0202K\u0001\u0000\u0000\u0000\u0203\u0204\u0005J\u0000\u0000\u0204\u0205"+
		"\u0003\u001c\u000e\u0000\u0205M\u0001\u0000\u0000\u0000\u0206\u0208\u0003"+
		"\u00c2a\u0000\u0207\u0206\u0001\u0000\u0000\u0000\u0207\u0208\u0001\u0000"+
		"\u0000\u0000\u0208\u0209\u0001\u0000\u0000\u0000\u0209\u020a\u0005R\u0000"+
		"\u0000\u020a\u020b\u0003\u00e0p\u0000\u020b\u020d\u0005\u0006\u0000\u0000"+
		"\u020c\u020e\u0003P(\u0000\u020d\u020c\u0001\u0000\u0000\u0000\u020d\u020e"+
		"\u0001\u0000\u0000\u0000\u020e\u020f\u0001\u0000\u0000\u0000\u020f\u0211"+
		"\u0005\u0007\u0000\u0000\u0210\u0212\u0003\u00b2Y\u0000\u0211\u0210\u0001"+
		"\u0000\u0000\u0000\u0211\u0212\u0001\u0000\u0000\u0000\u0212\u0213\u0001"+
		"\u0000\u0000\u0000\u0213\u0214\u0003\u001c\u000e\u0000\u0214O\u0001\u0000"+
		"\u0000\u0000\u0215\u021a\u0003R)\u0000\u0216\u0217\u0005\u000b\u0000\u0000"+
		"\u0217\u0219\u0003R)\u0000\u0218\u0216\u0001\u0000\u0000\u0000\u0219\u021c"+
		"\u0001\u0000\u0000\u0000\u021a\u0218\u0001\u0000\u0000\u0000\u021a\u021b"+
		"\u0001\u0000\u0000\u0000\u021bQ\u0001\u0000\u0000\u0000\u021c\u021a\u0001"+
		"\u0000\u0000\u0000\u021d\u021f\u0003\u00c2a\u0000\u021e\u021d\u0001\u0000"+
		"\u0000\u0000\u021e\u021f\u0001\u0000\u0000\u0000\u021f\u0220\u0001\u0000"+
		"\u0000\u0000\u0220\u0222\u0003T*\u0000\u0221\u0223\u0003\u00b2Y\u0000"+
		"\u0222\u0221\u0001\u0000\u0000\u0000\u0222\u0223\u0001\u0000\u0000\u0000"+
		"\u0223\u0225\u0001\u0000\u0000\u0000\u0224\u0226\u0003V+\u0000\u0225\u0224"+
		"\u0001\u0000\u0000\u0000\u0225\u0226\u0001\u0000\u0000\u0000\u0226S\u0001"+
		"\u0000\u0000\u0000\u0227\u022b\u0003\u00e0p\u0000\u0228\u022b\u0003X,"+
		"\u0000\u0229\u022b\u0003^/\u0000\u022a\u0227\u0001\u0000\u0000\u0000\u022a"+
		"\u0228\u0001\u0000\u0000\u0000\u022a\u0229\u0001\u0000\u0000\u0000\u022b"+
		"U\u0001\u0000\u0000\u0000\u022c\u022d\u0005\f\u0000\u0000\u022d\u022e"+
		"\u0003\u0082A\u0000\u022eW\u0001\u0000\u0000\u0000\u022f\u0230\u0005\u0004"+
		"\u0000\u0000\u0230\u0234\u0003Z-\u0000\u0231\u0232\u0005\u000b\u0000\u0000"+
		"\u0232\u0235\u0003d2\u0000\u0233\u0235\u0005\u000b\u0000\u0000\u0234\u0231"+
		"\u0001\u0000\u0000\u0000\u0234\u0233\u0001\u0000\u0000\u0000\u0234\u0235"+
		"\u0001\u0000\u0000\u0000\u0235\u0236\u0001\u0000\u0000\u0000\u0236\u0237"+
		"\u0005\u0005\u0000\u0000\u0237\u0240\u0001\u0000\u0000\u0000\u0238\u0239"+
		"\u0005\u0004\u0000\u0000\u0239\u023b\u0003d2\u0000\u023a\u023c\u0005\u000b"+
		"\u0000\u0000\u023b\u023a\u0001\u0000\u0000\u0000\u023b\u023c\u0001\u0000"+
		"\u0000\u0000\u023c\u023d\u0001\u0000\u0000\u0000\u023d\u023e\u0005\u0005"+
		"\u0000\u0000\u023e\u0240\u0001\u0000\u0000\u0000\u023f\u022f\u0001\u0000"+
		"\u0000\u0000\u023f\u0238\u0001\u0000\u0000\u0000\u0240Y\u0001\u0000\u0000"+
		"\u0000\u0241\u0246\u0003\\.\u0000\u0242\u0243\u0005\u000b\u0000\u0000"+
		"\u0243\u0245\u0003\\.\u0000\u0244\u0242\u0001\u0000\u0000\u0000\u0245"+
		"\u0248\u0001\u0000\u0000\u0000\u0246\u0244\u0001\u0000\u0000\u0000\u0246"+
		"\u0247\u0001\u0000\u0000\u0000\u0247[\u0001\u0000\u0000\u0000\u0248\u0246"+
		"\u0001\u0000\u0000\u0000\u0249\u024b\u0003\u00e0p\u0000\u024a\u024c\u0003"+
		"V+\u0000\u024b\u024a\u0001\u0000\u0000\u0000\u024b\u024c\u0001\u0000\u0000"+
		"\u0000\u024c]\u0001\u0000\u0000\u0000\u024d\u024f\u0005\b\u0000\u0000"+
		"\u024e\u0250\u0003`0\u0000\u024f\u024e\u0001\u0000\u0000\u0000\u024f\u0250"+
		"\u0001\u0000\u0000\u0000\u0250\u0254\u0001\u0000\u0000\u0000\u0251\u0252"+
		"\u0005\u000b\u0000\u0000\u0252\u0255\u0003d2\u0000\u0253\u0255\u0005\u000b"+
		"\u0000\u0000\u0254\u0251\u0001\u0000\u0000\u0000\u0254\u0253\u0001\u0000"+
		"\u0000\u0000\u0254\u0255\u0001\u0000\u0000\u0000\u0255\u0256\u0001\u0000"+
		"\u0000\u0000\u0256\u025f\u0005\t\u0000\u0000\u0257\u0258\u0005\b\u0000"+
		"\u0000\u0258\u025a\u0003d2\u0000\u0259\u025b\u0005\u000b\u0000\u0000\u025a"+
		"\u0259\u0001\u0000\u0000\u0000\u025a\u025b\u0001\u0000\u0000\u0000\u025b"+
		"\u025c\u0001\u0000\u0000\u0000\u025c\u025d\u0005\t\u0000\u0000\u025d\u025f"+
		"\u0001\u0000\u0000\u0000\u025e\u024d\u0001\u0000\u0000\u0000\u025e\u0257"+
		"\u0001\u0000\u0000\u0000\u025f_\u0001\u0000\u0000\u0000\u0260\u0265\u0003"+
		"b1\u0000\u0261\u0262\u0005\u000b\u0000\u0000\u0262\u0264\u0003b1\u0000"+
		"\u0263\u0261\u0001\u0000\u0000\u0000\u0264\u0267\u0001\u0000\u0000\u0000"+
		"\u0265\u0263\u0001\u0000\u0000\u0000\u0265\u0266\u0001\u0000\u0000\u0000"+
		"\u0266a\u0001\u0000\u0000\u0000\u0267\u0265\u0001\u0000\u0000\u0000\u0268"+
		"\u0269\u0003\u00dam\u0000\u0269\u026a\u0005\u0010\u0000\u0000\u026a\u026c"+
		"\u0003\u00e0p\u0000\u026b\u026d\u0003V+\u0000\u026c\u026b\u0001\u0000"+
		"\u0000\u0000\u026c\u026d\u0001\u0000\u0000\u0000\u026d\u0273\u0001\u0000"+
		"\u0000\u0000\u026e\u0270\u0003\u00e0p\u0000\u026f\u0271\u0003V+\u0000"+
		"\u0270\u026f\u0001\u0000\u0000\u0000\u0270\u0271\u0001\u0000\u0000\u0000"+
		"\u0271\u0273\u0001\u0000\u0000\u0000\u0272\u0268\u0001\u0000\u0000\u0000"+
		"\u0272\u026e\u0001\u0000\u0000\u0000\u0273c\u0001\u0000\u0000\u0000\u0274"+
		"\u0275\u0005\u0012\u0000\u0000\u0275\u0277\u0003\u00e0p\u0000\u0276\u0278"+
		"\u0003V+\u0000\u0277\u0276\u0001\u0000\u0000\u0000\u0277\u0278\u0001\u0000"+
		"\u0000\u0000\u0278e\u0001\u0000\u0000\u0000\u0279\u027b\u0005\u0004\u0000"+
		"\u0000\u027a\u027c\u0003h4\u0000\u027b\u027a\u0001\u0000\u0000\u0000\u027b"+
		"\u027c\u0001\u0000\u0000\u0000\u027c\u027d\u0001\u0000\u0000\u0000\u027d"+
		"\u027e\u0005\u0005\u0000\u0000\u027eg\u0001\u0000\u0000\u0000\u027f\u0284"+
		"\u0003j5\u0000\u0280\u0281\u0005\u000b\u0000\u0000\u0281\u0283\u0003j"+
		"5\u0000\u0282\u0280\u0001\u0000\u0000\u0000\u0283\u0286\u0001\u0000\u0000"+
		"\u0000\u0284\u0282\u0001\u0000\u0000\u0000\u0284\u0285\u0001\u0000\u0000"+
		"\u0000\u0285\u0288\u0001\u0000\u0000\u0000\u0286\u0284\u0001\u0000\u0000"+
		"\u0000\u0287\u0289\u0005\u000b\u0000\u0000\u0288\u0287\u0001\u0000\u0000"+
		"\u0000\u0288\u0289\u0001\u0000\u0000\u0000\u0289i\u0001\u0000\u0000\u0000"+
		"\u028a\u028d\u0003\u0082A\u0000\u028b\u028d\u0003l6\u0000\u028c\u028a"+
		"\u0001\u0000\u0000\u0000\u028c\u028b\u0001\u0000\u0000\u0000\u028dk\u0001"+
		"\u0000\u0000\u0000\u028e\u028f\u0005\u0012\u0000\u0000\u028f\u0290\u0003"+
		"\u0082A\u0000\u0290m\u0001\u0000\u0000\u0000\u0291\u0293\u0005\b\u0000"+
		"\u0000\u0292\u0294\u0003p8\u0000\u0293\u0292\u0001\u0000\u0000\u0000\u0293"+
		"\u0294\u0001\u0000\u0000\u0000\u0294\u0296\u0001\u0000\u0000\u0000\u0295"+
		"\u0297\u0005\u000b\u0000\u0000\u0296\u0295\u0001\u0000\u0000\u0000\u0296"+
		"\u0297\u0001\u0000\u0000\u0000\u0297\u0298\u0001\u0000\u0000\u0000\u0298"+
		"\u0299\u0005\t\u0000\u0000\u0299o\u0001\u0000\u0000\u0000\u029a\u029f"+
		"\u0003r9\u0000\u029b\u029c\u0005\u000b\u0000\u0000\u029c\u029e\u0003r"+
		"9\u0000\u029d\u029b\u0001\u0000\u0000\u0000\u029e\u02a1\u0001\u0000\u0000"+
		"\u0000\u029f\u029d\u0001\u0000\u0000\u0000\u029f\u02a0\u0001\u0000\u0000"+
		"\u0000\u02a0q\u0001\u0000\u0000\u0000\u02a1\u029f\u0001\u0000\u0000\u0000"+
		"\u02a2\u02a5\u0003t:\u0000\u02a3\u02a5\u0003l6\u0000\u02a4\u02a2\u0001"+
		"\u0000\u0000\u0000\u02a4\u02a3\u0001\u0000\u0000\u0000\u02a5s\u0001\u0000"+
		"\u0000\u0000\u02a6\u02a7\u0003\u00dcn\u0000\u02a7\u02a8\u0005\u0010\u0000"+
		"\u0000\u02a8\u02a9\u0003\u0082A\u0000\u02a9\u02b2\u0001\u0000\u0000\u0000"+
		"\u02aa\u02ab\u0005\u0004\u0000\u0000\u02ab\u02ac\u0003\u0082A\u0000\u02ac"+
		"\u02ad\u0005\u0005\u0000\u0000\u02ad\u02ae\u0005\u0010\u0000\u0000\u02ae"+
		"\u02af\u0003\u0082A\u0000\u02af\u02b2\u0001\u0000\u0000\u0000\u02b0\u02b2"+
		"\u0003\u00deo\u0000\u02b1\u02a6\u0001\u0000\u0000\u0000\u02b1\u02aa\u0001"+
		"\u0000\u0000\u0000\u02b1\u02b0\u0001\u0000\u0000\u0000\u02b2u\u0001\u0000"+
		"\u0000\u0000\u02b3\u02bf\u0005\u0006\u0000\u0000\u02b4\u02b9\u0003\u0082"+
		"A\u0000\u02b5\u02b6\u0005\u000b\u0000\u0000\u02b6\u02b8\u0003\u0082A\u0000"+
		"\u02b7\u02b5\u0001\u0000\u0000\u0000\u02b8\u02bb\u0001\u0000\u0000\u0000"+
		"\u02b9\u02b7\u0001\u0000\u0000\u0000\u02b9\u02ba\u0001\u0000\u0000\u0000"+
		"\u02ba\u02bd\u0001\u0000\u0000\u0000\u02bb\u02b9\u0001\u0000\u0000\u0000"+
		"\u02bc\u02be\u0005\u000b\u0000\u0000\u02bd\u02bc\u0001\u0000\u0000\u0000"+
		"\u02bd\u02be\u0001\u0000\u0000\u0000\u02be\u02c0\u0001\u0000\u0000\u0000"+
		"\u02bf\u02b4\u0001\u0000\u0000\u0000\u02bf\u02c0\u0001\u0000\u0000\u0000"+
		"\u02c0\u02c1\u0001\u0000\u0000\u0000\u02c1\u02c2\u0005\u0007\u0000\u0000"+
		"\u02c2w\u0001\u0000\u0000\u0000\u02c3\u02c4\u0003z=\u0000\u02c4y\u0001"+
		"\u0000\u0000\u0000\u02c5\u02ca\u0003\u0082A\u0000\u02c6\u02c7\u0005\u000b"+
		"\u0000\u0000\u02c7\u02c9\u0003\u0082A\u0000\u02c8\u02c6\u0001\u0000\u0000"+
		"\u0000\u02c9\u02cc\u0001\u0000\u0000\u0000\u02ca\u02c8\u0001\u0000\u0000"+
		"\u0000\u02ca\u02cb\u0001\u0000\u0000\u0000\u02cb{\u0001\u0000\u0000\u0000"+
		"\u02cc\u02ca\u0001\u0000\u0000\u0000\u02cd\u02ce\u0003\u00e0p\u0000\u02ce"+
		"\u02cf\u0005\f\u0000\u0000\u02cf\u02d0\u0003\u0082A\u0000\u02d0}\u0001"+
		"\u0000\u0000\u0000\u02d1\u02d6\u0003\u0080@\u0000\u02d2\u02d3\u0003&\u0013"+
		"\u0000\u02d3\u02d4\u0003(\u0014\u0000\u02d4\u02d6\u0001\u0000\u0000\u0000"+
		"\u02d5\u02d1\u0001\u0000\u0000\u0000\u02d5\u02d2\u0001\u0000\u0000\u0000"+
		"\u02d6\u007f\u0001\u0000\u0000\u0000\u02d7\u02dc\u0003|>\u0000\u02d8\u02d9"+
		"\u0005\u000b\u0000\u0000\u02d9\u02db\u0003|>\u0000\u02da\u02d8\u0001\u0000"+
		"\u0000\u0000\u02db\u02de\u0001\u0000\u0000\u0000\u02dc\u02da\u0001\u0000"+
		"\u0000\u0000\u02dc\u02dd\u0001\u0000\u0000\u0000\u02dd\u0081\u0001\u0000"+
		"\u0000\u0000\u02de\u02dc\u0001\u0000\u0000\u0000\u02df\u02e0\u0006A\uffff"+
		"\uffff\u0000\u02e0\u030a\u0003\u0086C\u0000\u02e1\u02e2\u0005G\u0000\u0000"+
		"\u02e2\u02e4\u0003\u00a8T\u0000\u02e3\u02e5\u0003v;\u0000\u02e4\u02e3"+
		"\u0001\u0000\u0000\u0000\u02e4\u02e5\u0001\u0000\u0000\u0000\u02e5\u030a"+
		"\u0001\u0000\u0000\u0000\u02e6\u02e7\u0005X\u0000\u0000\u02e7\u030a\u0003"+
		"\u0082A \u02e8\u02e9\u0005D\u0000\u0000\u02e9\u030a\u0003\u0082A\u001f"+
		"\u02ea\u02eb\u0005\u0014\u0000\u0000\u02eb\u030a\u0003\u0082A\u001e\u02ec"+
		"\u02ed\u0005\u0015\u0000\u0000\u02ed\u030a\u0003\u0082A\u001d\u02ee\u02ef"+
		"\u0005\u0016\u0000\u0000\u02ef\u030a\u0003\u0082A\u001c\u02f0\u02f1\u0005"+
		"\u0017\u0000\u0000\u02f1\u030a\u0003\u0082A\u001b\u02f2\u02f3\u0005\u0018"+
		"\u0000\u0000\u02f3\u030a\u0003\u0082A\u001a\u02f4\u02f5\u0005\u0019\u0000"+
		"\u0000\u02f5\u030a\u0003\u0082A\u0019\u02f6\u02f7\u0003\u00e0p\u0000\u02f7"+
		"\u02f8\u0004A\u0004\u0000\u02f8\u02f9\u0003\u0084B\u0000\u02f9\u030a\u0001"+
		"\u0000\u0000\u0000\u02fa\u030a\u0005S\u0000\u0000\u02fb\u030a\u0003\u00e8"+
		"t\u0000\u02fc\u030a\u0005c\u0000\u0000\u02fd\u030a\u0003\u00e0p\u0000"+
		"\u02fe\u030a\u0003f3\u0000\u02ff\u030a\u0003n7\u0000\u0300\u0301\u0005"+
		"\u0006\u0000\u0000\u0301\u0302\u0003\u0082A\u0000\u0302\u0303\u0005\u0007"+
		"\u0000\u0000\u0303\u030a\u0001\u0000\u0000\u0000\u0304\u0305\u0005~\u0000"+
		"\u0000\u0305\u0306\u0003\u0082A\u0000\u0306\u0307\u0005\t\u0000\u0000"+
		"\u0307\u0308\u0004A\u0005\u0000\u0308\u030a\u0001\u0000\u0000\u0000\u0309"+
		"\u02df\u0001\u0000\u0000\u0000\u0309\u02e1\u0001\u0000\u0000\u0000\u0309"+
		"\u02e6\u0001\u0000\u0000\u0000\u0309\u02e8\u0001\u0000\u0000\u0000\u0309"+
		"\u02ea\u0001\u0000\u0000\u0000\u0309\u02ec\u0001\u0000\u0000\u0000\u0309"+
		"\u02ee\u0001\u0000\u0000\u0000\u0309\u02f0\u0001\u0000\u0000\u0000\u0309"+
		"\u02f2\u0001\u0000\u0000\u0000\u0309\u02f4\u0001\u0000\u0000\u0000\u0309"+
		"\u02f6\u0001\u0000\u0000\u0000\u0309\u02fa\u0001\u0000\u0000\u0000\u0309"+
		"\u02fb\u0001\u0000\u0000\u0000\u0309\u02fc\u0001\u0000\u0000\u0000\u0309"+
		"\u02fd\u0001\u0000\u0000\u0000\u0309\u02fe\u0001\u0000\u0000\u0000\u0309"+
		"\u02ff\u0001\u0000\u0000\u0000\u0309\u0300\u0001\u0000\u0000\u0000\u0309"+
		"\u0304\u0001\u0000\u0000\u0000\u030a\u0356\u0001\u0000\u0000\u0000\u030b"+
		"\u030c\n\u0018\u0000\u0000\u030c\u030d\u0007\u0001\u0000\u0000\u030d\u0355"+
		"\u0003\u0082A\u0019\u030e\u030f\n\u0017\u0000\u0000\u030f\u0310\u0007"+
		"\u0002\u0000\u0000\u0310\u0355\u0003\u0082A\u0018\u0311\u0312\n\u0016"+
		"\u0000\u0000\u0312\u0313\u0005\r\u0000\u0000\u0313\u0355\u0003\u0082A"+
		"\u0017\u0314\u0315\n\u0015\u0000\u0000\u0315\u0316\u0007\u0003\u0000\u0000"+
		"\u0316\u0355\u0003\u0082A\u0016\u0317\u0318\n\u0014\u0000\u0000\u0318"+
		"\u0319\u0007\u0004\u0000\u0000\u0319\u0355\u0003\u0082A\u0015\u031a\u031b"+
		"\n\u0012\u0000\u0000\u031b\u031c\u0005Y\u0000\u0000\u031c\u0355\u0003"+
		"\u0082A\u0013\u031d\u031e\n\u0011\u0000\u0000\u031e\u031f\u0007\u0005"+
		"\u0000\u0000\u031f\u0355\u0003\u0082A\u0012\u0320\u0321\n\u0010\u0000"+
		"\u0000\u0321\u0322\u0005(\u0000\u0000\u0322\u0355\u0003\u0082A\u0011\u0323"+
		"\u0324\n\u000f\u0000\u0000\u0324\u0325\u0005)\u0000\u0000\u0325\u0355"+
		"\u0003\u0082A\u0010\u0326\u0327\n\u000e\u0000\u0000\u0327\u0328\u0005"+
		"*\u0000\u0000\u0328\u0355\u0003\u0082A\u000f\u0329\u032a\n\r\u0000\u0000"+
		"\u032a\u032b\u0007\u0006\u0000\u0000\u032b\u0355\u0003\u0082A\u000e\u032c"+
		"\u032d\n\f\u0000\u0000\u032d\u032e\u0007\u0007\u0000\u0000\u032e\u0355"+
		"\u0003\u0082A\r\u032f\u0330\n\u000b\u0000\u0000\u0330\u0331\u0005\u000e"+
		"\u0000\u0000\u0331\u0332\u0003\u0082A\u0000\u0332\u0333\u0005\u0010\u0000"+
		"\u0000\u0333\u0334\u0003\u0082A\f\u0334\u0355\u0001\u0000\u0000\u0000"+
		"\u0335\u0337\n\'\u0000\u0000\u0336\u0338\u0005\u000f\u0000\u0000\u0337"+
		"\u0336\u0001\u0000\u0000\u0000\u0337\u0338\u0001\u0000\u0000\u0000\u0338"+
		"\u0339\u0001\u0000\u0000\u0000\u0339\u033a\u0005\u0004\u0000\u0000\u033a"+
		"\u033b\u0003\u0082A\u0000\u033b\u033c\u0005\u0005\u0000\u0000\u033c\u0355"+
		"\u0001\u0000\u0000\u0000\u033d\u033e\n&\u0000\u0000\u033e\u033f\u0007"+
		"\b\u0000\u0000\u033f\u0355\u0003\u00deo\u0000\u0340\u0341\n%\u0000\u0000"+
		"\u0341\u0342\u0004A\u0016\u0000\u0342\u0355\u0005\u0019\u0000\u0000\u0343"+
		"\u0345\n#\u0000\u0000\u0344\u0346\u0005\u000f\u0000\u0000\u0345\u0344"+
		"\u0001\u0000\u0000\u0000\u0345\u0346\u0001\u0000\u0000\u0000\u0346\u0347"+
		"\u0001\u0000\u0000\u0000\u0347\u0355\u0003v;\u0000\u0348\u0349\n\"\u0000"+
		"\u0000\u0349\u034a\u0004A\u0019\u0000\u034a\u0355\u0005\u0014\u0000\u0000"+
		"\u034b\u034c\n!\u0000\u0000\u034c\u034d\u0004A\u001b\u0000\u034d\u0355"+
		"\u0005\u0015\u0000\u0000\u034e\u034f\n\u0013\u0000\u0000\u034f\u0350\u0005"+
		"C\u0000\u0000\u0350\u0355\u0003\u00a4R\u0000\u0351\u0352\n\u0001\u0000"+
		"\u0000\u0352\u0353\u0005[\u0000\u0000\u0353\u0355\u0003\u00a4R\u0000\u0354"+
		"\u030b\u0001\u0000\u0000\u0000\u0354\u030e\u0001\u0000\u0000\u0000\u0354"+
		"\u0311\u0001\u0000\u0000\u0000\u0354\u0314\u0001\u0000\u0000\u0000\u0354"+
		"\u0317\u0001\u0000\u0000\u0000\u0354\u031a\u0001\u0000\u0000\u0000\u0354"+
		"\u031d\u0001\u0000\u0000\u0000\u0354\u0320\u0001\u0000\u0000\u0000\u0354"+
		"\u0323\u0001\u0000\u0000\u0000\u0354\u0326\u0001\u0000\u0000\u0000\u0354"+
		"\u0329\u0001\u0000\u0000\u0000\u0354\u032c\u0001\u0000\u0000\u0000\u0354"+
		"\u032f\u0001\u0000\u0000\u0000\u0354\u0335\u0001\u0000\u0000\u0000\u0354"+
		"\u033d\u0001\u0000\u0000\u0000\u0354\u0340\u0001\u0000\u0000\u0000\u0354"+
		"\u0343\u0001\u0000\u0000\u0000\u0354\u0348\u0001\u0000\u0000\u0000\u0354"+
		"\u034b\u0001\u0000\u0000\u0000\u0354\u034e\u0001\u0000\u0000\u0000\u0354"+
		"\u0351\u0001\u0000\u0000\u0000\u0355\u0358\u0001\u0000\u0000\u0000\u0356"+
		"\u0354\u0001\u0000\u0000\u0000\u0356\u0357\u0001\u0000\u0000\u0000\u0357"+
		"\u0083\u0001\u0000\u0000\u0000\u0358\u0356\u0001\u0000\u0000\u0000\u0359"+
		"\u035a\u0005y\u0000\u0000\u035a\u0085\u0001\u0000\u0000\u0000\u035b\u035d"+
		"\u0005\u0006\u0000\u0000\u035c\u035e\u0003P(\u0000\u035d\u035c\u0001\u0000"+
		"\u0000\u0000\u035d\u035e\u0001\u0000\u0000\u0000\u035e\u035f\u0001\u0000"+
		"\u0000\u0000\u035f\u0361\u0005\u0007\u0000\u0000\u0360\u0362\u0003\u00b2"+
		"Y\u0000\u0361\u0360\u0001\u0000\u0000\u0000\u0361\u0362\u0001\u0000\u0000"+
		"\u0000\u0362\u0363\u0001\u0000\u0000\u0000\u0363\u0364\u00058\u0000\u0000"+
		"\u0364\u036a\u0003\u008aE\u0000\u0365\u0366\u0003\u0088D\u0000\u0366\u0367"+
		"\u00058\u0000\u0000\u0367\u0368\u0003\u008aE\u0000\u0368\u036a\u0001\u0000"+
		"\u0000\u0000\u0369\u035b\u0001\u0000\u0000\u0000\u0369\u0365\u0001\u0000"+
		"\u0000\u0000\u036a\u0087\u0001\u0000\u0000\u0000\u036b\u036c\u0003\u00e0"+
		"p\u0000\u036c\u0089\u0001\u0000\u0000\u0000\u036d\u0370\u0003\u0082A\u0000"+
		"\u036e\u0370\u0003\u001c\u000e\u0000\u036f\u036d\u0001\u0000\u0000\u0000"+
		"\u036f\u036e\u0001\u0000\u0000\u0000\u0370\u008b\u0001\u0000\u0000\u0000"+
		"\u0371\u0372\u0003\u0082A\u0000\u0372\u0373\u0005\u0004\u0000\u0000\u0373"+
		"\u0374\u0003\u0082A\u0000\u0374\u0375\u0005\u0005\u0000\u0000\u0375\u037b"+
		"\u0001\u0000\u0000\u0000\u0376\u0377\u0003\u0082A\u0000\u0377\u0378\u0005"+
		"\u0013\u0000\u0000\u0378\u0379\u0003\u00deo\u0000\u0379\u037b\u0001\u0000"+
		"\u0000\u0000\u037a\u0371\u0001\u0000\u0000\u0000\u037a\u0376\u0001\u0000"+
		"\u0000\u0000\u037b\u008d\u0001\u0000\u0000\u0000\u037c\u037d\u0007\t\u0000"+
		"\u0000\u037d\u008f\u0001\u0000\u0000\u0000\u037e\u0383\u0005\n\u0000\u0000"+
		"\u037f\u0383\u0005\u0000\u0000\u0001\u0380\u0383\u0004H\u001e\u0000\u0381"+
		"\u0383\u0004H\u001f\u0000\u0382\u037e\u0001\u0000\u0000\u0000\u0382\u037f"+
		"\u0001\u0000\u0000\u0000\u0382\u0380\u0001\u0000\u0000\u0000\u0382\u0381"+
		"\u0001\u0000\u0000\u0000\u0383\u0091\u0001\u0000\u0000\u0000\u0384\u038d"+
		"\u0005 \u0000\u0000\u0385\u038a\u0003\u0094J\u0000\u0386\u0387\u0005\u000b"+
		"\u0000\u0000\u0387\u0389\u0003\u0094J\u0000\u0388\u0386\u0001\u0000\u0000"+
		"\u0000\u0389\u038c\u0001\u0000\u0000\u0000\u038a\u0388\u0001\u0000\u0000"+
		"\u0000\u038a\u038b\u0001\u0000\u0000\u0000\u038b\u038e\u0001\u0000\u0000"+
		"\u0000\u038c\u038a\u0001\u0000\u0000\u0000\u038d\u0385\u0001\u0000\u0000"+
		"\u0000\u038d\u038e\u0001\u0000\u0000\u0000\u038e\u038f\u0001\u0000\u0000"+
		"\u0000\u038f\u0390\u0005!\u0000\u0000\u0390\u0093\u0001\u0000\u0000\u0000"+
		"\u0391\u0396\u0003\u00e0p\u0000\u0392\u0393\u0005b\u0000\u0000\u0393\u0397"+
		"\u0003\u00a4R\u0000\u0394\u0395\u0005c\u0000\u0000\u0395\u0397\u0003\u00a4"+
		"R\u0000\u0396\u0392\u0001\u0000\u0000\u0000\u0396\u0394\u0001\u0000\u0000"+
		"\u0000\u0396\u0397\u0001\u0000\u0000\u0000\u0397\u0095\u0001\u0000\u0000"+
		"\u0000\u0398\u03a1\u0005 \u0000\u0000\u0399\u039e\u0003\u00a4R\u0000\u039a"+
		"\u039b\u0005\u000b\u0000\u0000\u039b\u039d\u0003\u00a4R\u0000\u039c\u039a"+
		"\u0001\u0000\u0000\u0000\u039d\u03a0\u0001\u0000\u0000\u0000\u039e\u039c"+
		"\u0001\u0000\u0000\u0000\u039e\u039f\u0001\u0000\u0000\u0000\u039f\u03a2"+
		"\u0001\u0000\u0000\u0000\u03a0\u039e\u0001\u0000\u0000\u0000\u03a1\u0399"+
		"\u0001\u0000\u0000\u0000\u03a1\u03a2\u0001\u0000\u0000\u0000\u03a2\u03a3"+
		"\u0001\u0000\u0000\u0000\u03a3\u03a4\u0005!\u0000\u0000\u03a4\u0097\u0001"+
		"\u0000\u0000\u0000\u03a5\u03ab\u0003\u009aM\u0000\u03a6\u03ab\u0003\u00aa"+
		"U\u0000\u03a7\u03ab\u0003\u00a0P\u0000\u03a8\u03ab\u0003\u00a4R\u0000"+
		"\u03a9\u03ab\u0003\u00aeW\u0000\u03aa\u03a5\u0001\u0000\u0000\u0000\u03aa"+
		"\u03a6\u0001\u0000\u0000\u0000\u03aa\u03a7\u0001\u0000\u0000\u0000\u03aa"+
		"\u03a8\u0001\u0000\u0000\u0000\u03aa\u03a9\u0001\u0000\u0000\u0000\u03ab"+
		"\u0099\u0001\u0000\u0000\u0000\u03ac\u03af\u0003\u009cN\u0000\u03ad\u03af"+
		"\u0003\u009eO\u0000\u03ae\u03ac\u0001\u0000\u0000\u0000\u03ae\u03ad\u0001"+
		"\u0000\u0000\u0000\u03af\u009b\u0001\u0000\u0000\u0000\u03b0\u03b3\u0003"+
		"\u00a4R\u0000\u03b1\u03b2\u0005(\u0000\u0000\u03b2\u03b4\u0003\u00a4R"+
		"\u0000\u03b3\u03b1\u0001\u0000\u0000\u0000\u03b4\u03b5\u0001\u0000\u0000"+
		"\u0000\u03b5\u03b3\u0001\u0000\u0000\u0000\u03b5\u03b6\u0001\u0000\u0000"+
		"\u0000\u03b6\u009d\u0001\u0000\u0000\u0000\u03b7\u03ba\u0003\u00a4R\u0000"+
		"\u03b8\u03b9\u0005*\u0000\u0000\u03b9\u03bb\u0003\u00a4R\u0000\u03ba\u03b8"+
		"\u0001\u0000\u0000\u0000\u03bb\u03bc\u0001\u0000\u0000\u0000\u03bc\u03ba"+
		"\u0001\u0000\u0000\u0000\u03bc\u03bd\u0001\u0000\u0000\u0000\u03bd\u009f"+
		"\u0001\u0000\u0000\u0000\u03be\u03bf\u0005\u0004\u0000\u0000\u03bf\u03c0"+
		"\u0003\u00a2Q\u0000\u03c0\u03c1\u0005\u0005\u0000\u0000\u03c1\u00a1\u0001"+
		"\u0000\u0000\u0000\u03c2\u03c7\u0003\u0098L\u0000\u03c3\u03c4\u0005\u000b"+
		"\u0000\u0000\u03c4\u03c6\u0003\u0098L\u0000\u03c5\u03c3\u0001\u0000\u0000"+
		"\u0000\u03c6\u03c9\u0001\u0000\u0000\u0000\u03c7\u03c5\u0001\u0000\u0000"+
		"\u0000\u03c7\u03c8\u0001\u0000\u0000\u0000\u03c8\u00a3\u0001\u0000\u0000"+
		"\u0000\u03c9\u03c7\u0001\u0000\u0000\u0000\u03ca\u03cb\u0006R\uffff\uffff"+
		"\u0000\u03cb\u03cf\u0003\u00a6S\u0000\u03cc\u03cf\u0003\u00d8l\u0000\u03cd"+
		"\u03cf\u0003\u00a8T\u0000\u03ce\u03ca\u0001\u0000\u0000\u0000\u03ce\u03cc"+
		"\u0001\u0000\u0000\u0000\u03ce\u03cd\u0001\u0000\u0000\u0000\u03cf\u03d6"+
		"\u0001\u0000\u0000\u0000\u03d0\u03d1\n\u0001\u0000\u0000\u03d1\u03d2\u0004"+
		"R!\u0000\u03d2\u03d3\u0005\u0004\u0000\u0000\u03d3\u03d5\u0005\u0005\u0000"+
		"\u0000\u03d4\u03d0\u0001\u0000\u0000\u0000\u03d5\u03d8\u0001\u0000\u0000"+
		"\u0000\u03d6\u03d4\u0001\u0000\u0000\u0000\u03d6\u03d7\u0001\u0000\u0000"+
		"\u0000\u03d7\u00a5\u0001\u0000\u0000\u0000\u03d8\u03d6\u0001\u0000\u0000"+
		"\u0000\u03d9\u03da\u0007\n\u0000\u0000\u03da\u00a7\u0001\u0000\u0000\u0000"+
		"\u03db\u03dd\u0003\u00d8l\u0000\u03dc\u03de\u0003\u0096K\u0000\u03dd\u03dc"+
		"\u0001\u0000\u0000\u0000\u03dd\u03de\u0001\u0000\u0000\u0000\u03de\u00a9"+
		"\u0001\u0000\u0000\u0000\u03df\u03e0\u0005\b\u0000\u0000\u03e0\u03e2\u0003"+
		"\u00acV\u0000\u03e1\u03e3\u0005\u000b\u0000\u0000\u03e2\u03e1\u0001\u0000"+
		"\u0000\u0000\u03e2\u03e3\u0001\u0000\u0000\u0000\u03e3\u03e4\u0001\u0000"+
		"\u0000\u0000\u03e4\u03e5\u0005\t\u0000\u0000\u03e5\u00ab\u0001\u0000\u0000"+
		"\u0000\u03e6\u03eb\u0003\u00b0X\u0000\u03e7\u03e8\u0005\u000b\u0000\u0000"+
		"\u03e8\u03ea\u0003\u00b0X\u0000\u03e9\u03e7\u0001\u0000\u0000\u0000\u03ea"+
		"\u03ed\u0001\u0000\u0000\u0000\u03eb\u03e9\u0001\u0000\u0000\u0000\u03eb"+
		"\u03ec\u0001\u0000\u0000\u0000\u03ec\u00ad\u0001\u0000\u0000\u0000\u03ed"+
		"\u03eb\u0001\u0000\u0000\u0000\u03ee\u03f0\u0003\u0092I\u0000\u03ef\u03ee"+
		"\u0001\u0000\u0000\u0000\u03ef\u03f0\u0001\u0000\u0000\u0000\u03f0\u03f1"+
		"\u0001\u0000\u0000\u0000\u03f1\u03f3\u0005\b\u0000\u0000\u03f2\u03f4\u0003"+
		"\u00b6[\u0000\u03f3\u03f2\u0001\u0000\u0000\u0000\u03f3\u03f4\u0001\u0000"+
		"\u0000\u0000\u03f4\u03f5\u0001\u0000\u0000\u0000\u03f5\u03f6\u0005\t\u0000"+
		"\u0000\u03f6\u03f7\u00058\u0000\u0000\u03f7\u03f8\u0003\u00a4R\u0000\u03f8"+
		"\u00af\u0001\u0000\u0000\u0000\u03f9\u03fb\u0003\u00c2a\u0000\u03fa\u03f9"+
		"\u0001\u0000\u0000\u0000\u03fa\u03fb\u0001\u0000\u0000\u0000\u03fb\u03fd"+
		"\u0001\u0000\u0000\u0000\u03fc\u03fe\u0005]\u0000\u0000\u03fd\u03fc\u0001"+
		"\u0000\u0000\u0000\u03fd\u03fe\u0001\u0000\u0000\u0000\u03fe\u03ff\u0001"+
		"\u0000\u0000\u0000\u03ff\u0401\u0003\u00dam\u0000\u0400\u0402\u0005\u000e"+
		"\u0000\u0000\u0401\u0400\u0001\u0000\u0000\u0000\u0401\u0402\u0001\u0000"+
		"\u0000\u0000\u0402\u0404\u0001\u0000\u0000\u0000\u0403\u0405\u0003\u00b4"+
		"Z\u0000\u0404\u0403\u0001\u0000\u0000\u0000\u0404\u0405\u0001\u0000\u0000"+
		"\u0000\u0405\u00b1\u0001\u0000\u0000\u0000\u0406\u0407\u0005\u0010\u0000"+
		"\u0000\u0407\u0408\u0003\u00a4R\u0000\u0408\u00b3\u0001\u0000\u0000\u0000"+
		"\u0409\u040a\u0005\u0010\u0000\u0000\u040a\u040b\u0003\u0098L\u0000\u040b"+
		"\u00b5\u0001\u0000\u0000\u0000\u040c\u0411\u0003\u00b8\\\u0000\u040d\u040e"+
		"\u0005\u000b\u0000\u0000\u040e\u0410\u0003\u00b8\\\u0000\u040f\u040d\u0001"+
		"\u0000\u0000\u0000\u0410\u0413\u0001\u0000\u0000\u0000\u0411\u040f\u0001"+
		"\u0000\u0000\u0000\u0411\u0412\u0001\u0000\u0000\u0000\u0412\u00b7\u0001"+
		"\u0000\u0000\u0000\u0413\u0411\u0001\u0000\u0000\u0000\u0414\u0416\u0003"+
		"\u00e0p\u0000\u0415\u0417\u0005\u000e\u0000\u0000\u0416\u0415\u0001\u0000"+
		"\u0000\u0000\u0416\u0417\u0001\u0000\u0000\u0000\u0417\u0419\u0001\u0000"+
		"\u0000\u0000\u0418\u041a\u0003\u00b2Y\u0000\u0419\u0418\u0001\u0000\u0000"+
		"\u0000\u0419\u041a\u0001\u0000\u0000\u0000\u041a\u00b9\u0001\u0000\u0000"+
		"\u0000\u041b\u041d\u0003\u00c2a\u0000\u041c\u041b\u0001\u0000\u0000\u0000"+
		"\u041c\u041d\u0001\u0000\u0000\u0000\u041d\u041e\u0001\u0000\u0000\u0000"+
		"\u041e\u041f\u0005t\u0000\u0000\u041f\u0421\u0003\u00e0p\u0000\u0420\u0422"+
		"\u0003\u0092I\u0000\u0421\u0420\u0001\u0000\u0000\u0000\u0421\u0422\u0001"+
		"\u0000\u0000\u0000\u0422\u0423\u0001\u0000\u0000\u0000\u0423\u0424\u0005"+
		"\f\u0000\u0000\u0424\u0426\u0003\u0098L\u0000\u0425\u0427\u0005\n\u0000"+
		"\u0000\u0426\u0425\u0001\u0000\u0000\u0000\u0426\u0427\u0001\u0000\u0000"+
		"\u0000\u0427\u00bb\u0001\u0000\u0000\u0000\u0428\u0429\u0005a\u0000\u0000"+
		"\u0429\u042a\u0003\u00e0p\u0000\u042a\u042b\u0005\b\u0000\u0000\u042b"+
		"\u042d\u0003\u00be_\u0000\u042c\u042e\u0005\u000b\u0000\u0000\u042d\u042c"+
		"\u0001\u0000\u0000\u0000\u042d\u042e\u0001\u0000\u0000\u0000\u042e\u042f"+
		"\u0001\u0000\u0000\u0000\u042f\u0430\u0005\t\u0000\u0000\u0430\u00bd\u0001"+
		"\u0000\u0000\u0000\u0431\u0436\u0003\u00c0`\u0000\u0432\u0433\u0005\u000b"+
		"\u0000\u0000\u0433\u0435\u0003\u00c0`\u0000\u0434\u0432\u0001\u0000\u0000"+
		"\u0000\u0435\u0438\u0001\u0000\u0000\u0000\u0436\u0434\u0001\u0000\u0000"+
		"\u0000\u0436\u0437\u0001\u0000\u0000\u0000\u0437\u00bf\u0001\u0000\u0000"+
		"\u0000\u0438\u0436\u0001\u0000\u0000\u0000\u0439\u043c\u0003\u00e0p\u0000"+
		"\u043a\u043b\u0005\f\u0000\u0000\u043b\u043d\u0003\u00e8t\u0000\u043c"+
		"\u043a\u0001\u0000\u0000\u0000\u043c\u043d\u0001\u0000\u0000\u0000\u043d"+
		"\u00c1\u0001\u0000\u0000\u0000\u043e\u043f\u0003\u00c4b\u0000\u043f\u00c3"+
		"\u0001\u0000\u0000\u0000\u0440\u0442\u0003\u00c6c\u0000\u0441\u0440\u0001"+
		"\u0000\u0000\u0000\u0442\u0443\u0001\u0000\u0000\u0000\u0443\u0441\u0001"+
		"\u0000\u0000\u0000\u0443\u0444\u0001\u0000\u0000\u0000\u0444\u00c5\u0001"+
		"\u0000\u0000\u0000\u0445\u0446\u0005w\u0000\u0000\u0446\u044b\u0003\u00d4"+
		"j\u0000\u0447\u0448\u0005\b\u0000\u0000\u0448\u0449\u0003\u00c8d\u0000"+
		"\u0449\u044a\u0005\t\u0000\u0000\u044a\u044c\u0001\u0000\u0000\u0000\u044b"+
		"\u0447\u0001\u0000\u0000\u0000\u044b\u044c\u0001\u0000\u0000\u0000\u044c"+
		"\u00c7\u0001\u0000\u0000\u0000\u044d\u0452\u0005\b\u0000\u0000\u044e\u0450"+
		"\u0003\u00cae\u0000\u044f\u0451\u0005\u000b\u0000\u0000\u0450\u044f\u0001"+
		"\u0000\u0000\u0000\u0450\u0451\u0001\u0000\u0000\u0000\u0451\u0453\u0001"+
		"\u0000\u0000\u0000\u0452\u044e\u0001\u0000\u0000\u0000\u0452\u0453\u0001"+
		"\u0000\u0000\u0000\u0453\u0454\u0001\u0000\u0000\u0000\u0454\u0455\u0005"+
		"\t\u0000\u0000\u0455\u00c9\u0001\u0000\u0000\u0000\u0456\u045b\u0003\u00cc"+
		"f\u0000\u0457\u0458\u0005\u000b\u0000\u0000\u0458\u045a\u0003\u00ccf\u0000"+
		"\u0459\u0457\u0001\u0000\u0000\u0000\u045a\u045d\u0001\u0000\u0000\u0000"+
		"\u045b\u0459\u0001\u0000\u0000\u0000\u045b\u045c\u0001\u0000\u0000\u0000"+
		"\u045c\u00cb\u0001\u0000\u0000\u0000\u045d\u045b\u0001\u0000\u0000\u0000"+
		"\u045e\u045f\u0003\u00e0p\u0000\u045f\u0460\u0005\u0010\u0000\u0000\u0460"+
		"\u0461\u0003\u00d2i\u0000\u0461\u00cd\u0001\u0000\u0000\u0000\u0462\u0464"+
		"\u0005\u0004\u0000\u0000\u0463\u0465\u0003\u00d0h\u0000\u0464\u0463\u0001"+
		"\u0000\u0000\u0000\u0464\u0465\u0001\u0000\u0000\u0000\u0465\u0467\u0001"+
		"\u0000\u0000\u0000\u0466\u0468\u0005\u000b\u0000\u0000\u0467\u0466\u0001"+
		"\u0000\u0000\u0000\u0467\u0468\u0001\u0000\u0000\u0000\u0468\u0469\u0001"+
		"\u0000\u0000\u0000\u0469\u046a\u0005\u0005\u0000\u0000\u046a\u00cf\u0001"+
		"\u0000\u0000\u0000\u046b\u0470\u0003\u00d2i\u0000\u046c\u046d\u0005\u000b"+
		"\u0000\u0000\u046d\u046f\u0003\u00d2i\u0000\u046e\u046c\u0001\u0000\u0000"+
		"\u0000\u046f\u0472\u0001\u0000\u0000\u0000\u0470\u046e\u0001\u0000\u0000"+
		"\u0000\u0470\u0471\u0001\u0000\u0000\u0000\u0471\u00d1\u0001\u0000\u0000"+
		"\u0000\u0472\u0470\u0001\u0000\u0000\u0000\u0473\u0478\u0003\u00e8t\u0000"+
		"\u0474\u0478\u0003\u00c8d\u0000\u0475\u0478\u0003\u00ceg\u0000\u0476\u0478"+
		"\u0003\u00d4j\u0000\u0477\u0473\u0001\u0000\u0000\u0000\u0477\u0474\u0001"+
		"\u0000\u0000\u0000\u0477\u0475\u0001\u0000\u0000\u0000\u0477\u0476\u0001"+
		"\u0000\u0000\u0000\u0478\u00d3\u0001\u0000\u0000\u0000\u0479\u047c\u0003"+
		"\u00d6k\u0000\u047a\u047b\u0005\u0013\u0000\u0000\u047b\u047d\u0003\u00d4"+
		"j\u0000\u047c\u047a\u0001\u0000\u0000\u0000\u047c\u047d\u0001\u0000\u0000"+
		"\u0000\u047d\u00d5\u0001\u0000\u0000\u0000\u047e\u047f\u0003\u00e0p\u0000"+
		"\u047f\u00d7\u0001\u0000\u0000\u0000\u0480\u0481\u0003\u00d4j\u0000\u0481"+
		"\u00d9\u0001\u0000\u0000\u0000\u0482\u0485\u0003\u00e2q\u0000\u0483\u0485"+
		"\u0005x\u0000\u0000\u0484\u0482\u0001\u0000\u0000\u0000\u0484\u0483\u0001"+
		"\u0000\u0000\u0000\u0485\u00db\u0001\u0000\u0000\u0000\u0486\u0489\u0003"+
		"\u00deo\u0000\u0487\u0489\u0003\u00ecv\u0000\u0488\u0486\u0001\u0000\u0000"+
		"\u0000\u0488\u0487\u0001\u0000\u0000\u0000\u0489\u00dd\u0001\u0000\u0000"+
		"\u0000\u048a\u048b\u0003\u00e2q\u0000\u048b\u00df\u0001\u0000\u0000\u0000"+
		"\u048c\u048d\u0007\u000b\u0000\u0000\u048d\u00e1\u0001\u0000\u0000\u0000"+
		"\u048e\u0491\u0003\u00e0p\u0000\u048f\u0491\u0003\u00e4r\u0000\u0490\u048e"+
		"\u0001\u0000\u0000\u0000\u0490\u048f\u0001\u0000\u0000\u0000\u0491\u00e3"+
		"\u0001\u0000\u0000\u0000\u0492\u0498\u0003\u00e6s\u0000\u0493\u0498\u0005"+
		"9\u0000\u0000\u0494\u0498\u0005:\u0000\u0000\u0495\u0498\u0005;\u0000"+
		"\u0000\u0496\u0498\u0005<\u0000\u0000\u0497\u0492\u0001\u0000\u0000\u0000"+
		"\u0497\u0493\u0001\u0000\u0000\u0000\u0497\u0494\u0001\u0000\u0000\u0000"+
		"\u0497\u0495\u0001\u0000\u0000\u0000\u0497\u0496\u0001\u0000\u0000\u0000"+
		"\u0498\u00e5\u0001\u0000\u0000\u0000\u0499\u049a\u0007\f\u0000\u0000\u049a"+
		"\u00e7\u0001\u0000\u0000\u0000\u049b\u04a2\u00059\u0000\u0000\u049c\u04a2"+
		"\u0005:\u0000\u0000\u049d\u04a2\u0003\u00ecv\u0000\u049e\u04a2\u0005y"+
		"\u0000\u0000\u049f\u04a2\u0005\u0003\u0000\u0000\u04a0\u04a2\u0003\u00ea"+
		"u\u0000\u04a1\u049b\u0001\u0000\u0000\u0000\u04a1\u049c\u0001\u0000\u0000"+
		"\u0000\u04a1\u049d\u0001\u0000\u0000\u0000\u04a1\u049e\u0001\u0000\u0000"+
		"\u0000\u04a1\u049f\u0001\u0000\u0000\u0000\u04a1\u04a0\u0001\u0000\u0000"+
		"\u0000\u04a2\u00e9\u0001\u0000\u0000\u0000\u04a3\u04a4\u0007\r\u0000\u0000"+
		"\u04a4\u00eb\u0001\u0000\u0000\u0000\u04a5\u04a6\u0005x\u0000\u0000\u04a6"+
		"\u00ed\u0001\u0000\u0000\u0000{\u00f4\u0100\u0112\u0116\u0125\u012b\u0131"+
		"\u0137\u0148\u014e\u0157\u015d\u016f\u0174\u0177\u0186\u0199\u019d\u01a1"+
		"\u01b6\u01bc\u01c7\u01d2\u01dd\u01e4\u01ea\u01f4\u01f7\u01fe\u0207\u020d"+
		"\u0211\u021a\u021e\u0222\u0225\u022a\u0234\u023b\u023f\u0246\u024b\u024f"+
		"\u0254\u025a\u025e\u0265\u026c\u0270\u0272\u0277\u027b\u0284\u0288\u028c"+
		"\u0293\u0296\u029f\u02a4\u02b1\u02b9\u02bd\u02bf\u02ca\u02d5\u02dc\u02e4"+
		"\u0309\u0337\u0345\u0354\u0356\u035d\u0361\u0369\u036f\u037a\u0382\u038a"+
		"\u038d\u0396\u039e\u03a1\u03aa\u03ae\u03b5\u03bc\u03c7\u03ce\u03d6\u03dd"+
		"\u03e2\u03eb\u03ef\u03f3\u03fa\u03fd\u0401\u0404\u0411\u0416\u0419\u041c"+
		"\u0421\u0426\u042d\u0436\u043c\u0443\u044b\u0450\u0452\u045b\u0464\u0467"+
		"\u0470\u0477\u047c\u0484\u0488\u0490\u0497\u04a1";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
// resume CPD analysis - CPD-ON