// Nop Generated from Eql.g4 by ANTLR 4.13.0
package io.nop.orm.eql.parse.antlr;
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
public class EqlParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.13.0", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		AND_=1, CONCAT_=2, NOT_=3, TILDE_=4, VERTICAL_BAR_=5, AMPERSAND_=6, SIGNED_LEFT_SHIFT_=7, 
		SIGNED_RIGHT_SHIFT_=8, CARET_=9, MOD_=10, COLON_=11, PLUS_=12, MINUS_=13, 
		ASTERISK_=14, SLASH_=15, BACKSLASH_=16, DOT_=17, DOT_ASTERISK_=18, SAFE_EQ_=19, 
		DEQ_=20, EQ_=21, NEQ_=22, GT_=23, GTE_=24, LT_=25, LTE_=26, POUND_=27, 
		LP_=28, RP_=29, LBE_=30, RBE_=31, LBT_=32, RBT_=33, COMMA_=34, DQ_=35, 
		SQ_=36, QUESTION_=37, AT_=38, SEMI_=39, BLOCK_COMMENT=40, INLINE_COMMENT=41, 
		WS=42, SELECT=43, INSERT=44, UPDATE=45, DELETE=46, CREATE=47, ALTER=48, 
		DROP=49, TRUNCATE=50, SCHEMA=51, GRANT=52, REVOKE=53, ADD=54, SET=55, 
		TABLE=56, COLUMN=57, INDEX=58, CONSTRAINT=59, PRIMARY=60, UNIQUE=61, FOREIGN=62, 
		KEY=63, POSITION=64, PRECISION=65, FUNCTION=66, TRIGGER=67, PROCEDURE=68, 
		VIEW=69, INTO=70, VALUES=71, WITH=72, UNION=73, DISTINCT=74, CASE=75, 
		WHEN=76, CAST=77, TRIM=78, SUBSTRING=79, FROM=80, JOIN=81, FULL=82, INNER=83, 
		OUTER=84, LEFT=85, LATERAL=86, RIGHT=87, CROSS=88, WHERE=89, AS=90, ON=91, 
		IF=92, ELSE=93, THEN=94, FOR=95, TO=96, AND=97, OR=98, IS=99, NOT=100, 
		NULL=101, TRUE=102, FALSE=103, EXISTS=104, BETWEEN=105, IN=106, ALL=107, 
		ANY=108, LIKE=109, ILIKE=110, ORDER=111, GROUP=112, BY=113, ASC=114, DESC=115, 
		HAVING=116, LIMIT=117, OFFSET=118, BEGIN=119, COMMIT=120, ROLLBACK=121, 
		SAVEPOINT=122, BOOLEAN=123, DOUBLE=124, CHAR=125, CHARACTER=126, ARRAY=127, 
		INTERVAL=128, DATE=129, TIME=130, TIMESTAMP=131, LOCALTIME=132, LOCALTIMESTAMP=133, 
		YEAR=134, QUARTER=135, MONTH=136, WEEK=137, DAY=138, HOUR=139, MINUTE=140, 
		SECOND=141, MICROSECOND=142, MAX=143, MIN=144, SUM=145, COUNT=146, AVG=147, 
		DEFAULT=148, CURRENT=149, ENABLE=150, DISABLE=151, CALL=152, INSTANCE=153, 
		PRESERVE=154, DO=155, DEFINER=156, CURRENT_USER=157, SQL=158, CASCADED=159, 
		LOCAL=160, CLOSE=161, OPEN=162, NEXT=163, NAME=164, COLLATION=165, NAMES=166, 
		INTEGER=167, REAL=168, DECIMAL=169, TYPE=170, VARCHAR=171, FLOAT=172, 
		RECURSIVE=173, OVER=174, PARTITION=175, RETURNING=176, UPSERT=177, CONFLICT=178, 
		FOR_GENERATOR=179, CATALOG_NAME=180, CHARACTER_SET_CATALOG=181, CHARACTER_SET_NAME=182, 
		CHARACTER_SET_SCHEMA=183, CLASS_ORIGIN=184, COBOL=185, COLLATION_CATALOG=186, 
		COLLATION_NAME=187, COLLATION_SCHEMA=188, COLUMN_NAME=189, COMMAND_FUNCTION=190, 
		COMMITTED=191, CONDITION_NUMBER=192, CONNECTION_NAME=193, CONSTRAINT_CATALOG=194, 
		CONSTRAINT_NAME=195, CONSTRAINT_SCHEMA=196, CURSOR_NAME=197, DATA=198, 
		DATETIME_INTERVAL_CODE=199, DATETIME_INTERVAL_PRECISION=200, DYNAMIC_FUNCTION=201, 
		FORTRAN=202, LENGTH=203, MESSAGE_LENGTH=204, MESSAGE_OCTET_LENGTH=205, 
		MESSAGE_TEXT=206, MORE92=207, MUMPS=208, NULLABLE=209, NUMBER=210, PASCAL=211, 
		PLI=212, REPEATABLE=213, RETURNED_LENGTH=214, RETURNED_OCTET_LENGTH=215, 
		RETURNED_SQLSTATE=216, ROW_COUNT=217, SCALE=218, SCHEMA_NAME=219, SERIALIZABLE=220, 
		SERVER_NAME=221, SUBCLASS_ORIGIN=222, TABLE_NAME=223, UNCOMMITTED=224, 
		UNNAMED=225, ABSOLUTE=226, ACTION=227, ALLOCATE=228, ARE=229, ASSERTION=230, 
		AT=231, AUTHORIZATION=232, BIT=233, BIT_LENGTH=234, BOTH=235, CASCADE=236, 
		CATALOG=237, CHAR_LENGTH=238, CHARACTER_LENGTH=239, CHECK=240, COALESCE=241, 
		COLLATE=242, CONNECT=243, CONNECTION=244, CONSTRAINTS=245, CONTINUE=246, 
		CONVERT=247, CORRESPONDING=248, CURRENT_DATE=249, CURRENT_TIME=250, CURRENT_TIMESTAMP=251, 
		CURSOR=252, DEALLOCATE=253, DEC=254, DECLARE=255, DEFERRABLE=256, DEFERRED=257, 
		DESCRIBE=258, DESCRIPTOR=259, DIAGNOSTICS=260, DISCONNECT=261, DOMAIN=262, 
		END=263, END_EXEC=264, ESCAPE=265, EXCEPT=266, EXCEPTION=267, EXEC=268, 
		EXECUTE=269, EXTERNAL=270, EXTRACT=271, FETCH=272, FIRST=273, FOLLOWING=274, 
		FOUND=275, GET=276, GLOBAL=277, GO=278, GOTO=279, GROUPS=280, IDENTITY=281, 
		IMMEDIATE=282, INDICATOR=283, INITIALLY=284, INPUT=285, INSENSITIVE=286, 
		INTERSECT=287, ISOLATION=288, LANGUAGE=289, LAST=290, NULLS=291, LEADING=292, 
		LEVEL=293, LOWER=294, MATCH=295, MODULE=296, NATIONAL=297, NCHAR=298, 
		NO=299, NULLIF=300, NUMERIC=301, OCTET_LENGTH=302, OF=303, ONLY=304, OPTION=305, 
		OUTPUT=306, OVERLAPS=307, PAD=308, PARTIAL=309, PRECEDING=310, PREPARE=311, 
		PRIOR=312, PRIVILEGES=313, PUBLIC=314, RANGE=315, READ=316, REFERENCES=317, 
		RELATIVE=318, RESTRICT=319, ROW=320, ROWS=321, SCROLL=322, SECTION=323, 
		SESSION=324, SESSION_USER=325, SIZE=326, SMALLINT=327, SOME=328, SPACE=329, 
		SQLCODE=330, SQLERROR=331, SQLSTATE=332, SYSTEM_USER=333, TEMPORARY=334, 
		TUMBLE=335, HOP=336, TIMEZONE_HOUR=337, TIMEZONE_MINUTE=338, TRAILING=339, 
		TRANSACTION=340, TRANSLATE=341, TRANSLATION=342, UNBOUNDED=343, UNKNOWN=344, 
		UPPER=345, USAGE=346, USER=347, VALUE=348, VARYING=349, WHENEVER=350, 
		WINDOW=351, WORK=352, WRITE=353, ZONE=354, ROLLUP=355, CUBE=356, NOWAIT=357, 
		SKIP_=358, LOCKED=359, IDENTIFIER_=360, STRING_=361, NUMBER_=362, HEX_DIGIT_=363, 
		BIT_NUM_=364, STRING=365;
	public static final int
		RULE_sqlProgram = 0, RULE_sqlStatements_ = 1, RULE_sqlStatement = 2, RULE_sqlDmlStatement = 3, 
		RULE_sqlTransactionStatement = 4, RULE_sqlCommit = 5, RULE_sqlRollback = 6, 
		RULE_sqlInsert = 7, RULE_sqlUpsert = 8, RULE_sqlUpdate = 9, RULE_sqlAssignments_ = 10, 
		RULE_sqlAssignment = 11, RULE_sqlValues = 12, RULE_sqlValues_ = 13, RULE_sqlDelete = 14, 
		RULE_sqlSelectWithCte = 15, RULE_sqlCteStatement = 16, RULE_sqlCteStatements_ = 17, 
		RULE_sqlSelect = 18, RULE_unionType_ = 19, RULE_sqlQuerySelect = 20, RULE_sqlProjections_ = 21, 
		RULE_sqlProjection = 22, RULE_sqlExprProjection = 23, RULE_sqlAllProjection = 24, 
		RULE_sqlAlias = 25, RULE_sqlAlias_ = 26, RULE_sqlFrom = 27, RULE_tableSources_ = 28, 
		RULE_sqlTableSource = 29, RULE_sqlSingleTableSource = 30, RULE_sqlTumbleTableSource = 31, 
		RULE_sqlHopTableSource = 32, RULE_sqlSessionTableSource = 33, RULE_sqlSubqueryTableSource = 34, 
		RULE_sqlValuesTableSource = 35, RULE_sqlRowList_ = 36, RULE_sqlColumnAliases__ = 37, 
		RULE_joinType_ = 38, RULE_sqlTableSource_joinRight = 39, RULE_innerJoin_ = 40, 
		RULE_fullJoin_ = 41, RULE_leftJoin_ = 42, RULE_rightJoin_ = 43, RULE_crossJoin_ = 44, 
		RULE_sqlWhere = 45, RULE_sqlGroupBy = 46, RULE_sqlGroupByItems_ = 47, 
		RULE_sqlHaving = 48, RULE_sqlLimit = 49, RULE_sqlExpr_limitRowCount = 50, 
		RULE_sqlExpr_limitOffset = 51, RULE_sqlSubQueryExpr = 52, RULE_forUpdate_ = 53, 
		RULE_skipLocked_ = 54, RULE_sqlParameterMarker = 55, RULE_sqlLiteral = 56, 
		RULE_sqlStringLiteral = 57, RULE_sqlNumberLiteral = 58, RULE_sqlDateTimeLiteral = 59, 
		RULE_sqlHexadecimalLiteral = 60, RULE_sqlBitValueLiteral = 61, RULE_sqlBooleanLiteral = 62, 
		RULE_sqlNullLiteral = 63, RULE_sqlIdentifier_ = 64, RULE_unreservedWord_ = 65, 
		RULE_sqlTableName = 66, RULE_sqlColumnName = 67, RULE_sqlQualifiedName = 68, 
		RULE_columnNames_ = 69, RULE_sqlExpr = 70, RULE_sqlExpr_primary = 71, 
		RULE_comparisonOperator_ = 72, RULE_sqlExpr_predicate = 73, RULE_sqlInValues_ = 74, 
		RULE_sqlExpr_bit = 75, RULE_sqlExpr_simple = 76, RULE_sqlUnaryExpr = 77, 
		RULE_sqlExpr_brace = 78, RULE_sqlMultiValueExpr = 79, RULE_sqlExistsExpr = 80, 
		RULE_sqlExpr_functionCall = 81, RULE_sqlAggregateFunction = 82, RULE_sqlWindowExpr = 83, 
		RULE_sqlWindowFunction_ = 84, RULE_sqlPartitionBy = 85, RULE_sqlPartitionByItems_ = 86, 
		RULE_sqlWindowFrame = 87, RULE_sqlWindowFrameUnit_ = 88, RULE_sqlWindowFrameBound = 89, 
		RULE_sqlWindowFrameBoundType_ = 90, RULE_sqlWindowClause = 91, RULE_sqlWindowDeclItems_ = 92, 
		RULE_sqlWindowDecl = 93, RULE_sqlIdentifier_agg_ = 94, RULE_distinct_ = 95, 
		RULE_functionArgs_ = 96, RULE_sqlExpr_special = 97, RULE_trimDirection__ = 98, 
		RULE_extractField__ = 99, RULE_sqlCastExpr = 100, RULE_sqlRegularFunction = 101, 
		RULE_sqlIdentifier_func_ = 102, RULE_sqlDecorators_ = 103, RULE_sqlDecorator = 104, 
		RULE_decoratorArgs_ = 105, RULE_sqlCaseExpr = 106, RULE_caseWhens_ = 107, 
		RULE_sqlCaseWhenItem = 108, RULE_sqlIntervalExpr = 109, RULE_intervalUnit_ = 110, 
		RULE_sqlOrderBy = 111, RULE_sqlOrderByItems_ = 112, RULE_sqlOrderByItem = 113, 
		RULE_sqlGroupByItem = 114, RULE_sqlTypeExpr = 115, RULE_dataTypeName_ = 116, 
		RULE_characterSet_ = 117, RULE_collateClause_ = 118;
	private static String[] makeRuleNames() {
		return new String[] {
			"sqlProgram", "sqlStatements_", "sqlStatement", "sqlDmlStatement", "sqlTransactionStatement", 
			"sqlCommit", "sqlRollback", "sqlInsert", "sqlUpsert", "sqlUpdate", "sqlAssignments_", 
			"sqlAssignment", "sqlValues", "sqlValues_", "sqlDelete", "sqlSelectWithCte", 
			"sqlCteStatement", "sqlCteStatements_", "sqlSelect", "unionType_", "sqlQuerySelect", 
			"sqlProjections_", "sqlProjection", "sqlExprProjection", "sqlAllProjection", 
			"sqlAlias", "sqlAlias_", "sqlFrom", "tableSources_", "sqlTableSource", 
			"sqlSingleTableSource", "sqlTumbleTableSource", "sqlHopTableSource", 
			"sqlSessionTableSource", "sqlSubqueryTableSource", "sqlValuesTableSource", 
			"sqlRowList_", "sqlColumnAliases__", "joinType_", "sqlTableSource_joinRight", 
			"innerJoin_", "fullJoin_", "leftJoin_", "rightJoin_", "crossJoin_", "sqlWhere", 
			"sqlGroupBy", "sqlGroupByItems_", "sqlHaving", "sqlLimit", "sqlExpr_limitRowCount", 
			"sqlExpr_limitOffset", "sqlSubQueryExpr", "forUpdate_", "skipLocked_", 
			"sqlParameterMarker", "sqlLiteral", "sqlStringLiteral", "sqlNumberLiteral", 
			"sqlDateTimeLiteral", "sqlHexadecimalLiteral", "sqlBitValueLiteral", 
			"sqlBooleanLiteral", "sqlNullLiteral", "sqlIdentifier_", "unreservedWord_", 
			"sqlTableName", "sqlColumnName", "sqlQualifiedName", "columnNames_", 
			"sqlExpr", "sqlExpr_primary", "comparisonOperator_", "sqlExpr_predicate", 
			"sqlInValues_", "sqlExpr_bit", "sqlExpr_simple", "sqlUnaryExpr", "sqlExpr_brace", 
			"sqlMultiValueExpr", "sqlExistsExpr", "sqlExpr_functionCall", "sqlAggregateFunction", 
			"sqlWindowExpr", "sqlWindowFunction_", "sqlPartitionBy", "sqlPartitionByItems_", 
			"sqlWindowFrame", "sqlWindowFrameUnit_", "sqlWindowFrameBound", "sqlWindowFrameBoundType_", 
			"sqlWindowClause", "sqlWindowDeclItems_", "sqlWindowDecl", "sqlIdentifier_agg_", 
			"distinct_", "functionArgs_", "sqlExpr_special", "trimDirection__", "extractField__", 
			"sqlCastExpr", "sqlRegularFunction", "sqlIdentifier_func_", "sqlDecorators_", 
			"sqlDecorator", "decoratorArgs_", "sqlCaseExpr", "caseWhens_", "sqlCaseWhenItem", 
			"sqlIntervalExpr", "intervalUnit_", "sqlOrderBy", "sqlOrderByItems_", 
			"sqlOrderByItem", "sqlGroupByItem", "sqlTypeExpr", "dataTypeName_", "characterSet_", 
			"collateClause_"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'&&'", "'||'", "'!'", "'~'", "'|'", "'&'", "'<<'", "'>>'", "'^'", 
			"'%'", "':'", "'+'", "'-'", "'*'", "'/'", "'\\'", "'.'", "'.*'", "'<=>'", 
			"'=='", "'='", null, "'>'", "'>='", "'<'", "'<='", "'#'", "'('", "')'", 
			"'{'", "'}'", "'['", "']'", "','", "'\"'", "'''", "'?'", "'@'", "';'", 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, "'DO NOT MATCH ANY THING, JUST FOR GENERATOR'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "AND_", "CONCAT_", "NOT_", "TILDE_", "VERTICAL_BAR_", "AMPERSAND_", 
			"SIGNED_LEFT_SHIFT_", "SIGNED_RIGHT_SHIFT_", "CARET_", "MOD_", "COLON_", 
			"PLUS_", "MINUS_", "ASTERISK_", "SLASH_", "BACKSLASH_", "DOT_", "DOT_ASTERISK_", 
			"SAFE_EQ_", "DEQ_", "EQ_", "NEQ_", "GT_", "GTE_", "LT_", "LTE_", "POUND_", 
			"LP_", "RP_", "LBE_", "RBE_", "LBT_", "RBT_", "COMMA_", "DQ_", "SQ_", 
			"QUESTION_", "AT_", "SEMI_", "BLOCK_COMMENT", "INLINE_COMMENT", "WS", 
			"SELECT", "INSERT", "UPDATE", "DELETE", "CREATE", "ALTER", "DROP", "TRUNCATE", 
			"SCHEMA", "GRANT", "REVOKE", "ADD", "SET", "TABLE", "COLUMN", "INDEX", 
			"CONSTRAINT", "PRIMARY", "UNIQUE", "FOREIGN", "KEY", "POSITION", "PRECISION", 
			"FUNCTION", "TRIGGER", "PROCEDURE", "VIEW", "INTO", "VALUES", "WITH", 
			"UNION", "DISTINCT", "CASE", "WHEN", "CAST", "TRIM", "SUBSTRING", "FROM", 
			"JOIN", "FULL", "INNER", "OUTER", "LEFT", "LATERAL", "RIGHT", "CROSS", 
			"WHERE", "AS", "ON", "IF", "ELSE", "THEN", "FOR", "TO", "AND", "OR", 
			"IS", "NOT", "NULL", "TRUE", "FALSE", "EXISTS", "BETWEEN", "IN", "ALL", 
			"ANY", "LIKE", "ILIKE", "ORDER", "GROUP", "BY", "ASC", "DESC", "HAVING", 
			"LIMIT", "OFFSET", "BEGIN", "COMMIT", "ROLLBACK", "SAVEPOINT", "BOOLEAN", 
			"DOUBLE", "CHAR", "CHARACTER", "ARRAY", "INTERVAL", "DATE", "TIME", "TIMESTAMP", 
			"LOCALTIME", "LOCALTIMESTAMP", "YEAR", "QUARTER", "MONTH", "WEEK", "DAY", 
			"HOUR", "MINUTE", "SECOND", "MICROSECOND", "MAX", "MIN", "SUM", "COUNT", 
			"AVG", "DEFAULT", "CURRENT", "ENABLE", "DISABLE", "CALL", "INSTANCE", 
			"PRESERVE", "DO", "DEFINER", "CURRENT_USER", "SQL", "CASCADED", "LOCAL", 
			"CLOSE", "OPEN", "NEXT", "NAME", "COLLATION", "NAMES", "INTEGER", "REAL", 
			"DECIMAL", "TYPE", "VARCHAR", "FLOAT", "RECURSIVE", "OVER", "PARTITION", 
			"RETURNING", "UPSERT", "CONFLICT", "FOR_GENERATOR", "CATALOG_NAME", "CHARACTER_SET_CATALOG", 
			"CHARACTER_SET_NAME", "CHARACTER_SET_SCHEMA", "CLASS_ORIGIN", "COBOL", 
			"COLLATION_CATALOG", "COLLATION_NAME", "COLLATION_SCHEMA", "COLUMN_NAME", 
			"COMMAND_FUNCTION", "COMMITTED", "CONDITION_NUMBER", "CONNECTION_NAME", 
			"CONSTRAINT_CATALOG", "CONSTRAINT_NAME", "CONSTRAINT_SCHEMA", "CURSOR_NAME", 
			"DATA", "DATETIME_INTERVAL_CODE", "DATETIME_INTERVAL_PRECISION", "DYNAMIC_FUNCTION", 
			"FORTRAN", "LENGTH", "MESSAGE_LENGTH", "MESSAGE_OCTET_LENGTH", "MESSAGE_TEXT", 
			"MORE92", "MUMPS", "NULLABLE", "NUMBER", "PASCAL", "PLI", "REPEATABLE", 
			"RETURNED_LENGTH", "RETURNED_OCTET_LENGTH", "RETURNED_SQLSTATE", "ROW_COUNT", 
			"SCALE", "SCHEMA_NAME", "SERIALIZABLE", "SERVER_NAME", "SUBCLASS_ORIGIN", 
			"TABLE_NAME", "UNCOMMITTED", "UNNAMED", "ABSOLUTE", "ACTION", "ALLOCATE", 
			"ARE", "ASSERTION", "AT", "AUTHORIZATION", "BIT", "BIT_LENGTH", "BOTH", 
			"CASCADE", "CATALOG", "CHAR_LENGTH", "CHARACTER_LENGTH", "CHECK", "COALESCE", 
			"COLLATE", "CONNECT", "CONNECTION", "CONSTRAINTS", "CONTINUE", "CONVERT", 
			"CORRESPONDING", "CURRENT_DATE", "CURRENT_TIME", "CURRENT_TIMESTAMP", 
			"CURSOR", "DEALLOCATE", "DEC", "DECLARE", "DEFERRABLE", "DEFERRED", "DESCRIBE", 
			"DESCRIPTOR", "DIAGNOSTICS", "DISCONNECT", "DOMAIN", "END", "END_EXEC", 
			"ESCAPE", "EXCEPT", "EXCEPTION", "EXEC", "EXECUTE", "EXTERNAL", "EXTRACT", 
			"FETCH", "FIRST", "FOLLOWING", "FOUND", "GET", "GLOBAL", "GO", "GOTO", 
			"GROUPS", "IDENTITY", "IMMEDIATE", "INDICATOR", "INITIALLY", "INPUT", 
			"INSENSITIVE", "INTERSECT", "ISOLATION", "LANGUAGE", "LAST", "NULLS", 
			"LEADING", "LEVEL", "LOWER", "MATCH", "MODULE", "NATIONAL", "NCHAR", 
			"NO", "NULLIF", "NUMERIC", "OCTET_LENGTH", "OF", "ONLY", "OPTION", "OUTPUT", 
			"OVERLAPS", "PAD", "PARTIAL", "PRECEDING", "PREPARE", "PRIOR", "PRIVILEGES", 
			"PUBLIC", "RANGE", "READ", "REFERENCES", "RELATIVE", "RESTRICT", "ROW", 
			"ROWS", "SCROLL", "SECTION", "SESSION", "SESSION_USER", "SIZE", "SMALLINT", 
			"SOME", "SPACE", "SQLCODE", "SQLERROR", "SQLSTATE", "SYSTEM_USER", "TEMPORARY", 
			"TUMBLE", "HOP", "TIMEZONE_HOUR", "TIMEZONE_MINUTE", "TRAILING", "TRANSACTION", 
			"TRANSLATE", "TRANSLATION", "UNBOUNDED", "UNKNOWN", "UPPER", "USAGE", 
			"USER", "VALUE", "VARYING", "WHENEVER", "WINDOW", "WORK", "WRITE", "ZONE", 
			"ROLLUP", "CUBE", "NOWAIT", "SKIP_", "LOCKED", "IDENTIFIER_", "STRING_", 
			"NUMBER_", "HEX_DIGIT_", "BIT_NUM_", "STRING"
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
	public String getGrammarFileName() { return "Eql.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public EqlParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class SqlProgramContext extends ParserRuleContext {
		public SqlStatements_Context statements;
		public SqlStatements_Context sqlStatements_() {
			return getRuleContext(SqlStatements_Context.class,0);
		}
		public SqlProgramContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlProgram; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlProgram(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlProgram(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlProgram(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlProgramContext sqlProgram() throws RecognitionException {
		SqlProgramContext _localctx = new SqlProgramContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_sqlProgram);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(238);
			((SqlProgramContext)_localctx).statements = sqlStatements_();
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
	public static class SqlStatements_Context extends ParserRuleContext {
		public SqlStatementContext e;
		public List<SqlStatementContext> sqlStatement() {
			return getRuleContexts(SqlStatementContext.class);
		}
		public SqlStatementContext sqlStatement(int i) {
			return getRuleContext(SqlStatementContext.class,i);
		}
		public List<TerminalNode> SEMI_() { return getTokens(EqlParser.SEMI_); }
		public TerminalNode SEMI_(int i) {
			return getToken(EqlParser.SEMI_, i);
		}
		public SqlStatements_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlStatements_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlStatements_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlStatements_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlStatements_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlStatements_Context sqlStatements_() throws RecognitionException {
		SqlStatements_Context _localctx = new SqlStatements_Context(_ctx, getState());
		enterRule(_localctx, 2, RULE_sqlStatements_);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(240);
			((SqlStatements_Context)_localctx).e = sqlStatement();
			setState(245);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,0,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(241);
					match(SEMI_);
					setState(242);
					((SqlStatements_Context)_localctx).e = sqlStatement();
					}
					} 
				}
				setState(247);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,0,_ctx);
			}
			setState(249);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==SEMI_) {
				{
				setState(248);
				match(SEMI_);
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
	public static class SqlStatementContext extends ParserRuleContext {
		public SqlDmlStatementContext sqlDmlStatement() {
			return getRuleContext(SqlDmlStatementContext.class,0);
		}
		public SqlTransactionStatementContext sqlTransactionStatement() {
			return getRuleContext(SqlTransactionStatementContext.class,0);
		}
		public SqlStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlStatementContext sqlStatement() throws RecognitionException {
		SqlStatementContext _localctx = new SqlStatementContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_sqlStatement);
		try {
			setState(253);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case LP_:
			case AT_:
			case SELECT:
			case INSERT:
			case UPDATE:
			case DELETE:
			case WITH:
			case UPSERT:
				enterOuterAlt(_localctx, 1);
				{
				setState(251);
				sqlDmlStatement();
				}
				break;
			case COMMIT:
			case ROLLBACK:
				enterOuterAlt(_localctx, 2);
				{
				setState(252);
				sqlTransactionStatement();
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
	public static class SqlDmlStatementContext extends ParserRuleContext {
		public SqlSelectWithCteContext sqlSelectWithCte() {
			return getRuleContext(SqlSelectWithCteContext.class,0);
		}
		public SqlSelectContext sqlSelect() {
			return getRuleContext(SqlSelectContext.class,0);
		}
		public SqlInsertContext sqlInsert() {
			return getRuleContext(SqlInsertContext.class,0);
		}
		public SqlUpsertContext sqlUpsert() {
			return getRuleContext(SqlUpsertContext.class,0);
		}
		public SqlUpdateContext sqlUpdate() {
			return getRuleContext(SqlUpdateContext.class,0);
		}
		public SqlDeleteContext sqlDelete() {
			return getRuleContext(SqlDeleteContext.class,0);
		}
		public SqlDmlStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlDmlStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlDmlStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlDmlStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlDmlStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlDmlStatementContext sqlDmlStatement() throws RecognitionException {
		SqlDmlStatementContext _localctx = new SqlDmlStatementContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_sqlDmlStatement);
		try {
			setState(261);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,3,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(255);
				sqlSelectWithCte();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(256);
				sqlSelect(0);
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(257);
				sqlInsert();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(258);
				sqlUpsert();
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(259);
				sqlUpdate();
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(260);
				sqlDelete();
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
	public static class SqlTransactionStatementContext extends ParserRuleContext {
		public SqlCommitContext sqlCommit() {
			return getRuleContext(SqlCommitContext.class,0);
		}
		public SqlRollbackContext sqlRollback() {
			return getRuleContext(SqlRollbackContext.class,0);
		}
		public SqlTransactionStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlTransactionStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlTransactionStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlTransactionStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlTransactionStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlTransactionStatementContext sqlTransactionStatement() throws RecognitionException {
		SqlTransactionStatementContext _localctx = new SqlTransactionStatementContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_sqlTransactionStatement);
		try {
			setState(265);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case COMMIT:
				enterOuterAlt(_localctx, 1);
				{
				setState(263);
				sqlCommit();
				}
				break;
			case ROLLBACK:
				enterOuterAlt(_localctx, 2);
				{
				setState(264);
				sqlRollback();
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
	public static class SqlCommitContext extends ParserRuleContext {
		public TerminalNode COMMIT() { return getToken(EqlParser.COMMIT, 0); }
		public SqlCommitContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlCommit; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlCommit(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlCommit(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlCommit(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlCommitContext sqlCommit() throws RecognitionException {
		SqlCommitContext _localctx = new SqlCommitContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_sqlCommit);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(267);
			match(COMMIT);
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
	public static class SqlRollbackContext extends ParserRuleContext {
		public TerminalNode ROLLBACK() { return getToken(EqlParser.ROLLBACK, 0); }
		public SqlRollbackContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlRollback; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlRollback(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlRollback(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlRollback(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlRollbackContext sqlRollback() throws RecognitionException {
		SqlRollbackContext _localctx = new SqlRollbackContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_sqlRollback);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(269);
			match(ROLLBACK);
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
	public static class SqlInsertContext extends ParserRuleContext {
		public SqlDecorators_Context decorators;
		public SqlTableNameContext tableName;
		public ColumnNames_Context columns;
		public SqlValuesContext values;
		public SqlSelectContext select;
		public TerminalNode INSERT() { return getToken(EqlParser.INSERT, 0); }
		public TerminalNode INTO() { return getToken(EqlParser.INTO, 0); }
		public TerminalNode LP_() { return getToken(EqlParser.LP_, 0); }
		public TerminalNode RP_() { return getToken(EqlParser.RP_, 0); }
		public SqlTableNameContext sqlTableName() {
			return getRuleContext(SqlTableNameContext.class,0);
		}
		public ColumnNames_Context columnNames_() {
			return getRuleContext(ColumnNames_Context.class,0);
		}
		public SqlValuesContext sqlValues() {
			return getRuleContext(SqlValuesContext.class,0);
		}
		public SqlSelectContext sqlSelect() {
			return getRuleContext(SqlSelectContext.class,0);
		}
		public SqlDecorators_Context sqlDecorators_() {
			return getRuleContext(SqlDecorators_Context.class,0);
		}
		public SqlInsertContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlInsert; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlInsert(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlInsert(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlInsert(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlInsertContext sqlInsert() throws RecognitionException {
		SqlInsertContext _localctx = new SqlInsertContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_sqlInsert);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(272);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==AT_) {
				{
				setState(271);
				((SqlInsertContext)_localctx).decorators = sqlDecorators_();
				}
			}

			setState(274);
			match(INSERT);
			setState(275);
			match(INTO);
			setState(276);
			((SqlInsertContext)_localctx).tableName = sqlTableName();
			setState(277);
			match(LP_);
			setState(278);
			((SqlInsertContext)_localctx).columns = columnNames_();
			setState(279);
			match(RP_);
			setState(282);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case VALUES:
				{
				setState(280);
				((SqlInsertContext)_localctx).values = sqlValues();
				}
				break;
			case LP_:
			case AT_:
			case SELECT:
				{
				setState(281);
				((SqlInsertContext)_localctx).select = sqlSelect(0);
				}
				break;
			default:
				throw new NoViableAltException(this);
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
	public static class SqlUpsertContext extends ParserRuleContext {
		public SqlDecorators_Context decorators;
		public SqlTableNameContext tableName;
		public ColumnNames_Context columns;
		public SqlValuesContext values;
		public ColumnNames_Context conflictColumns;
		public SqlAssignments_Context assignments;
		public TerminalNode UPSERT() { return getToken(EqlParser.UPSERT, 0); }
		public TerminalNode INTO() { return getToken(EqlParser.INTO, 0); }
		public List<TerminalNode> LP_() { return getTokens(EqlParser.LP_); }
		public TerminalNode LP_(int i) {
			return getToken(EqlParser.LP_, i);
		}
		public List<TerminalNode> RP_() { return getTokens(EqlParser.RP_); }
		public TerminalNode RP_(int i) {
			return getToken(EqlParser.RP_, i);
		}
		public TerminalNode ON() { return getToken(EqlParser.ON, 0); }
		public TerminalNode CONFLICT() { return getToken(EqlParser.CONFLICT, 0); }
		public TerminalNode DO() { return getToken(EqlParser.DO, 0); }
		public TerminalNode UPDATE() { return getToken(EqlParser.UPDATE, 0); }
		public SqlTableNameContext sqlTableName() {
			return getRuleContext(SqlTableNameContext.class,0);
		}
		public List<ColumnNames_Context> columnNames_() {
			return getRuleContexts(ColumnNames_Context.class);
		}
		public ColumnNames_Context columnNames_(int i) {
			return getRuleContext(ColumnNames_Context.class,i);
		}
		public SqlValuesContext sqlValues() {
			return getRuleContext(SqlValuesContext.class,0);
		}
		public SqlAssignments_Context sqlAssignments_() {
			return getRuleContext(SqlAssignments_Context.class,0);
		}
		public SqlDecorators_Context sqlDecorators_() {
			return getRuleContext(SqlDecorators_Context.class,0);
		}
		public SqlUpsertContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlUpsert; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlUpsert(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlUpsert(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlUpsert(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlUpsertContext sqlUpsert() throws RecognitionException {
		SqlUpsertContext _localctx = new SqlUpsertContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_sqlUpsert);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(285);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==AT_) {
				{
				setState(284);
				((SqlUpsertContext)_localctx).decorators = sqlDecorators_();
				}
			}

			setState(287);
			match(UPSERT);
			setState(288);
			match(INTO);
			setState(289);
			((SqlUpsertContext)_localctx).tableName = sqlTableName();
			setState(290);
			match(LP_);
			setState(291);
			((SqlUpsertContext)_localctx).columns = columnNames_();
			setState(292);
			match(RP_);
			setState(293);
			((SqlUpsertContext)_localctx).values = sqlValues();
			setState(294);
			match(ON);
			setState(295);
			match(CONFLICT);
			setState(296);
			match(LP_);
			setState(297);
			((SqlUpsertContext)_localctx).conflictColumns = columnNames_();
			setState(298);
			match(RP_);
			setState(299);
			match(DO);
			setState(300);
			match(UPDATE);
			setState(301);
			((SqlUpsertContext)_localctx).assignments = sqlAssignments_();
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
	public static class SqlUpdateContext extends ParserRuleContext {
		public SqlDecorators_Context decorators;
		public SqlTableNameContext tableName;
		public SqlAliasContext alias;
		public SqlAssignments_Context assignments;
		public SqlWhereContext where;
		public Token returnAll;
		public SqlProjections_Context returnProjections;
		public TerminalNode UPDATE() { return getToken(EqlParser.UPDATE, 0); }
		public SqlTableNameContext sqlTableName() {
			return getRuleContext(SqlTableNameContext.class,0);
		}
		public SqlAssignments_Context sqlAssignments_() {
			return getRuleContext(SqlAssignments_Context.class,0);
		}
		public TerminalNode RETURNING() { return getToken(EqlParser.RETURNING, 0); }
		public SqlDecorators_Context sqlDecorators_() {
			return getRuleContext(SqlDecorators_Context.class,0);
		}
		public SqlWhereContext sqlWhere() {
			return getRuleContext(SqlWhereContext.class,0);
		}
		public TerminalNode AS() { return getToken(EqlParser.AS, 0); }
		public SqlAliasContext sqlAlias() {
			return getRuleContext(SqlAliasContext.class,0);
		}
		public TerminalNode ASTERISK_() { return getToken(EqlParser.ASTERISK_, 0); }
		public SqlProjections_Context sqlProjections_() {
			return getRuleContext(SqlProjections_Context.class,0);
		}
		public SqlUpdateContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlUpdate; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlUpdate(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlUpdate(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlUpdate(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlUpdateContext sqlUpdate() throws RecognitionException {
		SqlUpdateContext _localctx = new SqlUpdateContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_sqlUpdate);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(304);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==AT_) {
				{
				setState(303);
				((SqlUpdateContext)_localctx).decorators = sqlDecorators_();
				}
			}

			setState(306);
			match(UPDATE);
			setState(307);
			((SqlUpdateContext)_localctx).tableName = sqlTableName();
			{
			setState(309);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==AS) {
				{
				setState(308);
				match(AS);
				}
			}

			setState(312);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & 36451009505001473L) != 0) || ((((_la - 129)) & ~0x3f) == 0 && ((1L << (_la - 129)) & -1387549045940507L) != 0) || ((((_la - 193)) & ~0x3f) == 0 && ((1L << (_la - 193)) & 72341276627828735L) != 0) || ((((_la - 263)) & ~0x3f) == 0 && ((1L << (_la - 263)) & 3607524592334276609L) != 0) || ((((_la - 335)) & ~0x3f) == 0 && ((1L << (_la - 335)) & 133903619L) != 0)) {
				{
				setState(311);
				((SqlUpdateContext)_localctx).alias = sqlAlias();
				}
			}

			}
			{
			setState(314);
			((SqlUpdateContext)_localctx).assignments = sqlAssignments_();
			}
			setState(316);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WHERE) {
				{
				setState(315);
				((SqlUpdateContext)_localctx).where = sqlWhere();
				}
			}

			setState(323);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==RETURNING) {
				{
				setState(318);
				match(RETURNING);
				setState(321);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case ASTERISK_:
					{
					setState(319);
					((SqlUpdateContext)_localctx).returnAll = match(ASTERISK_);
					}
					break;
				case NOT_:
				case TILDE_:
				case PLUS_:
				case MINUS_:
				case LP_:
				case QUESTION_:
				case POSITION:
				case CASE:
				case CAST:
				case TRIM:
				case SUBSTRING:
				case LATERAL:
				case CROSS:
				case IF:
				case NOT:
				case NULL:
				case TRUE:
				case FALSE:
				case EXISTS:
				case ORDER:
				case GROUP:
				case BEGIN:
				case INTERVAL:
				case DATE:
				case TIME:
				case TIMESTAMP:
				case LOCALTIME:
				case LOCALTIMESTAMP:
				case YEAR:
				case QUARTER:
				case MONTH:
				case DAY:
				case HOUR:
				case MINUTE:
				case SECOND:
				case MAX:
				case MIN:
				case SUM:
				case COUNT:
				case AVG:
				case CURRENT_USER:
				case NAME:
				case TYPE:
				case RECURSIVE:
				case UPSERT:
				case CONFLICT:
				case CATALOG_NAME:
				case CHARACTER_SET_CATALOG:
				case CHARACTER_SET_NAME:
				case CHARACTER_SET_SCHEMA:
				case CLASS_ORIGIN:
				case COBOL:
				case COLLATION_CATALOG:
				case COLLATION_NAME:
				case COLLATION_SCHEMA:
				case COLUMN_NAME:
				case COMMAND_FUNCTION:
				case COMMITTED:
				case CONDITION_NUMBER:
				case CONNECTION_NAME:
				case CONSTRAINT_CATALOG:
				case CONSTRAINT_NAME:
				case CONSTRAINT_SCHEMA:
				case CURSOR_NAME:
				case DATA:
				case DATETIME_INTERVAL_CODE:
				case DATETIME_INTERVAL_PRECISION:
				case DYNAMIC_FUNCTION:
				case FORTRAN:
				case LENGTH:
				case MESSAGE_LENGTH:
				case MESSAGE_OCTET_LENGTH:
				case MESSAGE_TEXT:
				case MORE92:
				case MUMPS:
				case NULLABLE:
				case NUMBER:
				case PASCAL:
				case PLI:
				case REPEATABLE:
				case RETURNED_LENGTH:
				case RETURNED_OCTET_LENGTH:
				case RETURNED_SQLSTATE:
				case ROW_COUNT:
				case SCALE:
				case SCHEMA_NAME:
				case SERIALIZABLE:
				case SERVER_NAME:
				case SUBCLASS_ORIGIN:
				case TABLE_NAME:
				case UNCOMMITTED:
				case UNNAMED:
				case BIT_LENGTH:
				case COALESCE:
				case CURRENT_DATE:
				case CURRENT_TIMESTAMP:
				case END:
				case EXTRACT:
				case FOLLOWING:
				case GROUPS:
				case INDICATOR:
				case INSENSITIVE:
				case LANGUAGE:
				case NULLS:
				case LEVEL:
				case LOWER:
				case NULLIF:
				case OCTET_LENGTH:
				case PRECEDING:
				case RANGE:
				case ROW:
				case SECTION:
				case SESSION:
				case TUMBLE:
				case HOP:
				case UNBOUNDED:
				case UPPER:
				case USER:
				case VALUE:
				case WINDOW:
				case WORK:
				case ZONE:
				case ROLLUP:
				case CUBE:
				case NOWAIT:
				case SKIP_:
				case LOCKED:
				case IDENTIFIER_:
				case STRING_:
				case NUMBER_:
				case HEX_DIGIT_:
				case BIT_NUM_:
					{
					setState(320);
					((SqlUpdateContext)_localctx).returnProjections = sqlProjections_();
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
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
	public static class SqlAssignments_Context extends ParserRuleContext {
		public SqlAssignmentContext e;
		public TerminalNode SET() { return getToken(EqlParser.SET, 0); }
		public List<SqlAssignmentContext> sqlAssignment() {
			return getRuleContexts(SqlAssignmentContext.class);
		}
		public SqlAssignmentContext sqlAssignment(int i) {
			return getRuleContext(SqlAssignmentContext.class,i);
		}
		public List<TerminalNode> COMMA_() { return getTokens(EqlParser.COMMA_); }
		public TerminalNode COMMA_(int i) {
			return getToken(EqlParser.COMMA_, i);
		}
		public SqlAssignments_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlAssignments_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlAssignments_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlAssignments_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlAssignments_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlAssignments_Context sqlAssignments_() throws RecognitionException {
		SqlAssignments_Context _localctx = new SqlAssignments_Context(_ctx, getState());
		enterRule(_localctx, 20, RULE_sqlAssignments_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(325);
			match(SET);
			setState(326);
			((SqlAssignments_Context)_localctx).e = sqlAssignment();
			setState(331);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA_) {
				{
				{
				setState(327);
				match(COMMA_);
				setState(328);
				((SqlAssignments_Context)_localctx).e = sqlAssignment();
				}
				}
				setState(333);
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
	public static class SqlAssignmentContext extends ParserRuleContext {
		public SqlColumnNameContext columnName;
		public SqlExprContext expr;
		public TerminalNode EQ_() { return getToken(EqlParser.EQ_, 0); }
		public SqlColumnNameContext sqlColumnName() {
			return getRuleContext(SqlColumnNameContext.class,0);
		}
		public SqlExprContext sqlExpr() {
			return getRuleContext(SqlExprContext.class,0);
		}
		public SqlAssignmentContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlAssignment; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlAssignment(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlAssignment(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlAssignment(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlAssignmentContext sqlAssignment() throws RecognitionException {
		SqlAssignmentContext _localctx = new SqlAssignmentContext(_ctx, getState());
		enterRule(_localctx, 22, RULE_sqlAssignment);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(334);
			((SqlAssignmentContext)_localctx).columnName = sqlColumnName();
			setState(335);
			match(EQ_);
			setState(336);
			((SqlAssignmentContext)_localctx).expr = sqlExpr(0);
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
	public static class SqlValuesContext extends ParserRuleContext {
		public SqlValues_Context values;
		public SqlValues_Context sqlValues_() {
			return getRuleContext(SqlValues_Context.class,0);
		}
		public SqlValuesContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlValues; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlValues(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlValues(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlValues(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlValuesContext sqlValues() throws RecognitionException {
		SqlValuesContext _localctx = new SqlValuesContext(_ctx, getState());
		enterRule(_localctx, 24, RULE_sqlValues);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(338);
			((SqlValuesContext)_localctx).values = sqlValues_();
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
	public static class SqlValues_Context extends ParserRuleContext {
		public SqlExprContext e;
		public TerminalNode VALUES() { return getToken(EqlParser.VALUES, 0); }
		public TerminalNode LP_() { return getToken(EqlParser.LP_, 0); }
		public List<SqlExprContext> sqlExpr() {
			return getRuleContexts(SqlExprContext.class);
		}
		public SqlExprContext sqlExpr(int i) {
			return getRuleContext(SqlExprContext.class,i);
		}
		public TerminalNode RP_() { return getToken(EqlParser.RP_, 0); }
		public List<TerminalNode> COMMA_() { return getTokens(EqlParser.COMMA_); }
		public TerminalNode COMMA_(int i) {
			return getToken(EqlParser.COMMA_, i);
		}
		public SqlValues_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlValues_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlValues_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlValues_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlValues_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlValues_Context sqlValues_() throws RecognitionException {
		SqlValues_Context _localctx = new SqlValues_Context(_ctx, getState());
		enterRule(_localctx, 26, RULE_sqlValues_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(340);
			match(VALUES);
			setState(341);
			match(LP_);
			setState(342);
			((SqlValues_Context)_localctx).e = sqlExpr(0);
			setState(347);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA_) {
				{
				{
				setState(343);
				match(COMMA_);
				setState(344);
				((SqlValues_Context)_localctx).e = sqlExpr(0);
				}
				}
				setState(349);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			{
			setState(350);
			match(RP_);
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
	public static class SqlDeleteContext extends ParserRuleContext {
		public SqlDecorators_Context decorators;
		public SqlTableNameContext tableName;
		public SqlAliasContext alias;
		public SqlWhereContext where;
		public TerminalNode DELETE() { return getToken(EqlParser.DELETE, 0); }
		public TerminalNode FROM() { return getToken(EqlParser.FROM, 0); }
		public SqlTableNameContext sqlTableName() {
			return getRuleContext(SqlTableNameContext.class,0);
		}
		public TerminalNode AS() { return getToken(EqlParser.AS, 0); }
		public SqlDecorators_Context sqlDecorators_() {
			return getRuleContext(SqlDecorators_Context.class,0);
		}
		public SqlAliasContext sqlAlias() {
			return getRuleContext(SqlAliasContext.class,0);
		}
		public SqlWhereContext sqlWhere() {
			return getRuleContext(SqlWhereContext.class,0);
		}
		public SqlDeleteContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlDelete; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlDelete(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlDelete(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlDelete(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlDeleteContext sqlDelete() throws RecognitionException {
		SqlDeleteContext _localctx = new SqlDeleteContext(_ctx, getState());
		enterRule(_localctx, 28, RULE_sqlDelete);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(353);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==AT_) {
				{
				setState(352);
				((SqlDeleteContext)_localctx).decorators = sqlDecorators_();
				}
			}

			setState(355);
			match(DELETE);
			setState(356);
			match(FROM);
			setState(357);
			((SqlDeleteContext)_localctx).tableName = sqlTableName();
			setState(359);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==AS) {
				{
				setState(358);
				match(AS);
				}
			}

			setState(362);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & 36451009505001473L) != 0) || ((((_la - 129)) & ~0x3f) == 0 && ((1L << (_la - 129)) & -1387549045940507L) != 0) || ((((_la - 193)) & ~0x3f) == 0 && ((1L << (_la - 193)) & 72341276627828735L) != 0) || ((((_la - 263)) & ~0x3f) == 0 && ((1L << (_la - 263)) & 3607524592334276609L) != 0) || ((((_la - 335)) & ~0x3f) == 0 && ((1L << (_la - 335)) & 133903619L) != 0)) {
				{
				setState(361);
				((SqlDeleteContext)_localctx).alias = sqlAlias();
				}
			}

			setState(365);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WHERE) {
				{
				setState(364);
				((SqlDeleteContext)_localctx).where = sqlWhere();
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
	public static class SqlSelectWithCteContext extends ParserRuleContext {
		public SqlDecorators_Context decorators;
		public SqlCteStatements_Context withCtes;
		public SqlSelectContext select;
		public SqlCteStatements_Context sqlCteStatements_() {
			return getRuleContext(SqlCteStatements_Context.class,0);
		}
		public SqlSelectContext sqlSelect() {
			return getRuleContext(SqlSelectContext.class,0);
		}
		public SqlDecorators_Context sqlDecorators_() {
			return getRuleContext(SqlDecorators_Context.class,0);
		}
		public SqlSelectWithCteContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlSelectWithCte; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlSelectWithCte(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlSelectWithCte(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlSelectWithCte(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlSelectWithCteContext sqlSelectWithCte() throws RecognitionException {
		SqlSelectWithCteContext _localctx = new SqlSelectWithCteContext(_ctx, getState());
		enterRule(_localctx, 30, RULE_sqlSelectWithCte);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(368);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==AT_) {
				{
				setState(367);
				((SqlSelectWithCteContext)_localctx).decorators = sqlDecorators_();
				}
			}

			setState(370);
			((SqlSelectWithCteContext)_localctx).withCtes = sqlCteStatements_();
			setState(371);
			((SqlSelectWithCteContext)_localctx).select = sqlSelect(0);
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
	public static class SqlCteStatementContext extends ParserRuleContext {
		public Token recursive;
		public SqlIdentifier_Context name;
		public SqlSelectContext statement;
		public TerminalNode AS() { return getToken(EqlParser.AS, 0); }
		public TerminalNode LP_() { return getToken(EqlParser.LP_, 0); }
		public TerminalNode RP_() { return getToken(EqlParser.RP_, 0); }
		public SqlIdentifier_Context sqlIdentifier_() {
			return getRuleContext(SqlIdentifier_Context.class,0);
		}
		public SqlSelectContext sqlSelect() {
			return getRuleContext(SqlSelectContext.class,0);
		}
		public TerminalNode RECURSIVE() { return getToken(EqlParser.RECURSIVE, 0); }
		public SqlCteStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlCteStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlCteStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlCteStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlCteStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlCteStatementContext sqlCteStatement() throws RecognitionException {
		SqlCteStatementContext _localctx = new SqlCteStatementContext(_ctx, getState());
		enterRule(_localctx, 32, RULE_sqlCteStatement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(374);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,21,_ctx) ) {
			case 1:
				{
				setState(373);
				((SqlCteStatementContext)_localctx).recursive = match(RECURSIVE);
				}
				break;
			}
			setState(376);
			((SqlCteStatementContext)_localctx).name = sqlIdentifier_();
			setState(377);
			match(AS);
			setState(378);
			match(LP_);
			setState(379);
			((SqlCteStatementContext)_localctx).statement = sqlSelect(0);
			setState(380);
			match(RP_);
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
	public static class SqlCteStatements_Context extends ParserRuleContext {
		public SqlCteStatementContext e;
		public TerminalNode WITH() { return getToken(EqlParser.WITH, 0); }
		public List<SqlCteStatementContext> sqlCteStatement() {
			return getRuleContexts(SqlCteStatementContext.class);
		}
		public SqlCteStatementContext sqlCteStatement(int i) {
			return getRuleContext(SqlCteStatementContext.class,i);
		}
		public List<TerminalNode> COMMA_() { return getTokens(EqlParser.COMMA_); }
		public TerminalNode COMMA_(int i) {
			return getToken(EqlParser.COMMA_, i);
		}
		public SqlCteStatements_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlCteStatements_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlCteStatements_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlCteStatements_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlCteStatements_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlCteStatements_Context sqlCteStatements_() throws RecognitionException {
		SqlCteStatements_Context _localctx = new SqlCteStatements_Context(_ctx, getState());
		enterRule(_localctx, 34, RULE_sqlCteStatements_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(382);
			match(WITH);
			setState(383);
			((SqlCteStatements_Context)_localctx).e = sqlCteStatement();
			setState(388);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA_) {
				{
				{
				setState(384);
				match(COMMA_);
				setState(385);
				((SqlCteStatements_Context)_localctx).e = sqlCteStatement();
				}
				}
				setState(390);
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
	public static class SqlSelectContext extends ParserRuleContext {
		public SqlSelectContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlSelect; }
	 
		public SqlSelectContext() { }
		public void copyFrom(SqlSelectContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlUnionSelect_exContext extends SqlSelectContext {
		public SqlSelectContext left;
		public SqlDecorators_Context decorators;
		public UnionType_Context unionType;
		public SqlSelectContext right;
		public List<SqlSelectContext> sqlSelect() {
			return getRuleContexts(SqlSelectContext.class);
		}
		public SqlSelectContext sqlSelect(int i) {
			return getRuleContext(SqlSelectContext.class,i);
		}
		public UnionType_Context unionType_() {
			return getRuleContext(UnionType_Context.class,0);
		}
		public SqlDecorators_Context sqlDecorators_() {
			return getRuleContext(SqlDecorators_Context.class,0);
		}
		public SqlUnionSelect_exContext(SqlSelectContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlUnionSelect_ex(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlUnionSelect_ex(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlUnionSelect_ex(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlQuerySelect_exContext extends SqlSelectContext {
		public SqlQuerySelectContext sqlQuerySelect() {
			return getRuleContext(SqlQuerySelectContext.class,0);
		}
		public SqlQuerySelect_exContext(SqlSelectContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlQuerySelect_ex(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlQuerySelect_ex(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlQuerySelect_ex(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlSelect_exContext extends SqlSelectContext {
		public TerminalNode LP_() { return getToken(EqlParser.LP_, 0); }
		public SqlSelectContext sqlSelect() {
			return getRuleContext(SqlSelectContext.class,0);
		}
		public TerminalNode RP_() { return getToken(EqlParser.RP_, 0); }
		public SqlSelect_exContext(SqlSelectContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlSelect_ex(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlSelect_ex(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlSelect_ex(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlSelectContext sqlSelect() throws RecognitionException {
		return sqlSelect(0);
	}

	private SqlSelectContext sqlSelect(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		SqlSelectContext _localctx = new SqlSelectContext(_ctx, _parentState);
		SqlSelectContext _prevctx = _localctx;
		int _startState = 36;
		enterRecursionRule(_localctx, 36, RULE_sqlSelect, _p);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(397);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case AT_:
			case SELECT:
				{
				_localctx = new SqlQuerySelect_exContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;

				setState(392);
				sqlQuerySelect();
				}
				break;
			case LP_:
				{
				_localctx = new SqlSelect_exContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(393);
				match(LP_);
				setState(394);
				sqlSelect(0);
				setState(395);
				match(RP_);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			_ctx.stop = _input.LT(-1);
			setState(408);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,25,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					{
					_localctx = new SqlUnionSelect_exContext(new SqlSelectContext(_parentctx, _parentState));
					((SqlUnionSelect_exContext)_localctx).left = _prevctx;
					pushNewRecursionContext(_localctx, _startState, RULE_sqlSelect);
					setState(399);
					if (!(precpred(_ctx, 1))) throw new FailedPredicateException(this, "precpred(_ctx, 1)");
					setState(401);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la==AT_) {
						{
						setState(400);
						((SqlUnionSelect_exContext)_localctx).decorators = sqlDecorators_();
						}
					}

					setState(403);
					((SqlUnionSelect_exContext)_localctx).unionType = unionType_();
					setState(404);
					((SqlUnionSelect_exContext)_localctx).right = sqlSelect(2);
					}
					} 
				}
				setState(410);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,25,_ctx);
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
	public static class UnionType_Context extends ParserRuleContext {
		public TerminalNode UNION() { return getToken(EqlParser.UNION, 0); }
		public TerminalNode INTERSECT() { return getToken(EqlParser.INTERSECT, 0); }
		public TerminalNode EXCEPT() { return getToken(EqlParser.EXCEPT, 0); }
		public TerminalNode ALL() { return getToken(EqlParser.ALL, 0); }
		public UnionType_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_unionType_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterUnionType_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitUnionType_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitUnionType_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final UnionType_Context unionType_() throws RecognitionException {
		UnionType_Context _localctx = new UnionType_Context(_ctx, getState());
		enterRule(_localctx, 38, RULE_unionType_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(411);
			_la = _input.LA(1);
			if ( !(_la==UNION || _la==EXCEPT || _la==INTERSECT) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(413);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ALL) {
				{
				setState(412);
				match(ALL);
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
	public static class SqlQuerySelectContext extends ParserRuleContext {
		public SqlDecorators_Context decorators;
		public Distinct_Context distinct;
		public Token selectAll;
		public SqlProjections_Context projections;
		public SqlFromContext from;
		public SqlWhereContext where;
		public SqlGroupByContext groupBy;
		public SqlHavingContext having;
		public SqlWindowClauseContext windowClause;
		public SqlOrderByContext orderBy;
		public SqlLimitContext limit;
		public ForUpdate_Context forUpdate;
		public Token nowait;
		public SkipLocked_Context skipLocked;
		public TerminalNode SELECT() { return getToken(EqlParser.SELECT, 0); }
		public TerminalNode ASTERISK_() { return getToken(EqlParser.ASTERISK_, 0); }
		public SqlProjections_Context sqlProjections_() {
			return getRuleContext(SqlProjections_Context.class,0);
		}
		public SqlDecorators_Context sqlDecorators_() {
			return getRuleContext(SqlDecorators_Context.class,0);
		}
		public Distinct_Context distinct_() {
			return getRuleContext(Distinct_Context.class,0);
		}
		public SqlFromContext sqlFrom() {
			return getRuleContext(SqlFromContext.class,0);
		}
		public SqlWhereContext sqlWhere() {
			return getRuleContext(SqlWhereContext.class,0);
		}
		public SqlGroupByContext sqlGroupBy() {
			return getRuleContext(SqlGroupByContext.class,0);
		}
		public SqlHavingContext sqlHaving() {
			return getRuleContext(SqlHavingContext.class,0);
		}
		public SqlWindowClauseContext sqlWindowClause() {
			return getRuleContext(SqlWindowClauseContext.class,0);
		}
		public SqlOrderByContext sqlOrderBy() {
			return getRuleContext(SqlOrderByContext.class,0);
		}
		public SqlLimitContext sqlLimit() {
			return getRuleContext(SqlLimitContext.class,0);
		}
		public ForUpdate_Context forUpdate_() {
			return getRuleContext(ForUpdate_Context.class,0);
		}
		public TerminalNode NOWAIT() { return getToken(EqlParser.NOWAIT, 0); }
		public SkipLocked_Context skipLocked_() {
			return getRuleContext(SkipLocked_Context.class,0);
		}
		public SqlQuerySelectContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlQuerySelect; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlQuerySelect(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlQuerySelect(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlQuerySelect(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlQuerySelectContext sqlQuerySelect() throws RecognitionException {
		SqlQuerySelectContext _localctx = new SqlQuerySelectContext(_ctx, getState());
		enterRule(_localctx, 40, RULE_sqlQuerySelect);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(416);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==AT_) {
				{
				setState(415);
				((SqlQuerySelectContext)_localctx).decorators = sqlDecorators_();
				}
			}

			setState(418);
			match(SELECT);
			setState(420);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==DISTINCT) {
				{
				setState(419);
				((SqlQuerySelectContext)_localctx).distinct = distinct_();
				}
			}

			setState(424);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case ASTERISK_:
				{
				setState(422);
				((SqlQuerySelectContext)_localctx).selectAll = match(ASTERISK_);
				}
				break;
			case NOT_:
			case TILDE_:
			case PLUS_:
			case MINUS_:
			case LP_:
			case QUESTION_:
			case POSITION:
			case CASE:
			case CAST:
			case TRIM:
			case SUBSTRING:
			case LATERAL:
			case CROSS:
			case IF:
			case NOT:
			case NULL:
			case TRUE:
			case FALSE:
			case EXISTS:
			case ORDER:
			case GROUP:
			case BEGIN:
			case INTERVAL:
			case DATE:
			case TIME:
			case TIMESTAMP:
			case LOCALTIME:
			case LOCALTIMESTAMP:
			case YEAR:
			case QUARTER:
			case MONTH:
			case DAY:
			case HOUR:
			case MINUTE:
			case SECOND:
			case MAX:
			case MIN:
			case SUM:
			case COUNT:
			case AVG:
			case CURRENT_USER:
			case NAME:
			case TYPE:
			case RECURSIVE:
			case UPSERT:
			case CONFLICT:
			case CATALOG_NAME:
			case CHARACTER_SET_CATALOG:
			case CHARACTER_SET_NAME:
			case CHARACTER_SET_SCHEMA:
			case CLASS_ORIGIN:
			case COBOL:
			case COLLATION_CATALOG:
			case COLLATION_NAME:
			case COLLATION_SCHEMA:
			case COLUMN_NAME:
			case COMMAND_FUNCTION:
			case COMMITTED:
			case CONDITION_NUMBER:
			case CONNECTION_NAME:
			case CONSTRAINT_CATALOG:
			case CONSTRAINT_NAME:
			case CONSTRAINT_SCHEMA:
			case CURSOR_NAME:
			case DATA:
			case DATETIME_INTERVAL_CODE:
			case DATETIME_INTERVAL_PRECISION:
			case DYNAMIC_FUNCTION:
			case FORTRAN:
			case LENGTH:
			case MESSAGE_LENGTH:
			case MESSAGE_OCTET_LENGTH:
			case MESSAGE_TEXT:
			case MORE92:
			case MUMPS:
			case NULLABLE:
			case NUMBER:
			case PASCAL:
			case PLI:
			case REPEATABLE:
			case RETURNED_LENGTH:
			case RETURNED_OCTET_LENGTH:
			case RETURNED_SQLSTATE:
			case ROW_COUNT:
			case SCALE:
			case SCHEMA_NAME:
			case SERIALIZABLE:
			case SERVER_NAME:
			case SUBCLASS_ORIGIN:
			case TABLE_NAME:
			case UNCOMMITTED:
			case UNNAMED:
			case BIT_LENGTH:
			case COALESCE:
			case CURRENT_DATE:
			case CURRENT_TIMESTAMP:
			case END:
			case EXTRACT:
			case FOLLOWING:
			case GROUPS:
			case INDICATOR:
			case INSENSITIVE:
			case LANGUAGE:
			case NULLS:
			case LEVEL:
			case LOWER:
			case NULLIF:
			case OCTET_LENGTH:
			case PRECEDING:
			case RANGE:
			case ROW:
			case SECTION:
			case SESSION:
			case TUMBLE:
			case HOP:
			case UNBOUNDED:
			case UPPER:
			case USER:
			case VALUE:
			case WINDOW:
			case WORK:
			case ZONE:
			case ROLLUP:
			case CUBE:
			case NOWAIT:
			case SKIP_:
			case LOCKED:
			case IDENTIFIER_:
			case STRING_:
			case NUMBER_:
			case HEX_DIGIT_:
			case BIT_NUM_:
				{
				setState(423);
				((SqlQuerySelectContext)_localctx).projections = sqlProjections_();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(427);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,30,_ctx) ) {
			case 1:
				{
				setState(426);
				((SqlQuerySelectContext)_localctx).from = sqlFrom();
				}
				break;
			}
			setState(430);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,31,_ctx) ) {
			case 1:
				{
				setState(429);
				((SqlQuerySelectContext)_localctx).where = sqlWhere();
				}
				break;
			}
			setState(433);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,32,_ctx) ) {
			case 1:
				{
				setState(432);
				((SqlQuerySelectContext)_localctx).groupBy = sqlGroupBy();
				}
				break;
			}
			setState(436);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,33,_ctx) ) {
			case 1:
				{
				setState(435);
				((SqlQuerySelectContext)_localctx).having = sqlHaving();
				}
				break;
			}
			setState(439);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,34,_ctx) ) {
			case 1:
				{
				setState(438);
				((SqlQuerySelectContext)_localctx).windowClause = sqlWindowClause();
				}
				break;
			}
			setState(442);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,35,_ctx) ) {
			case 1:
				{
				setState(441);
				((SqlQuerySelectContext)_localctx).orderBy = sqlOrderBy();
				}
				break;
			}
			setState(445);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,36,_ctx) ) {
			case 1:
				{
				setState(444);
				((SqlQuerySelectContext)_localctx).limit = sqlLimit();
				}
				break;
			}
			setState(452);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,38,_ctx) ) {
			case 1:
				{
				setState(447);
				((SqlQuerySelectContext)_localctx).forUpdate = forUpdate_();
				setState(450);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,37,_ctx) ) {
				case 1:
					{
					setState(448);
					((SqlQuerySelectContext)_localctx).nowait = match(NOWAIT);
					}
					break;
				case 2:
					{
					setState(449);
					((SqlQuerySelectContext)_localctx).skipLocked = skipLocked_();
					}
					break;
				}
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
	public static class SqlProjections_Context extends ParserRuleContext {
		public SqlProjectionContext e;
		public List<SqlProjectionContext> sqlProjection() {
			return getRuleContexts(SqlProjectionContext.class);
		}
		public SqlProjectionContext sqlProjection(int i) {
			return getRuleContext(SqlProjectionContext.class,i);
		}
		public List<TerminalNode> COMMA_() { return getTokens(EqlParser.COMMA_); }
		public TerminalNode COMMA_(int i) {
			return getToken(EqlParser.COMMA_, i);
		}
		public SqlProjections_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlProjections_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlProjections_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlProjections_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlProjections_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlProjections_Context sqlProjections_() throws RecognitionException {
		SqlProjections_Context _localctx = new SqlProjections_Context(_ctx, getState());
		enterRule(_localctx, 42, RULE_sqlProjections_);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(454);
			((SqlProjections_Context)_localctx).e = sqlProjection();
			setState(459);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,39,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(455);
					match(COMMA_);
					setState(456);
					((SqlProjections_Context)_localctx).e = sqlProjection();
					}
					} 
				}
				setState(461);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,39,_ctx);
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
	public static class SqlProjectionContext extends ParserRuleContext {
		public SqlExprProjectionContext sqlExprProjection() {
			return getRuleContext(SqlExprProjectionContext.class,0);
		}
		public SqlAllProjectionContext sqlAllProjection() {
			return getRuleContext(SqlAllProjectionContext.class,0);
		}
		public SqlProjectionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlProjection; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlProjection(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlProjection(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlProjection(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlProjectionContext sqlProjection() throws RecognitionException {
		SqlProjectionContext _localctx = new SqlProjectionContext(_ctx, getState());
		enterRule(_localctx, 44, RULE_sqlProjection);
		try {
			setState(464);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,40,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(462);
				sqlExprProjection();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(463);
				sqlAllProjection();
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
	public static class SqlExprProjectionContext extends ParserRuleContext {
		public SqlExprContext expr;
		public SqlAliasContext alias;
		public SqlDecorators_Context decorators;
		public SqlExprContext sqlExpr() {
			return getRuleContext(SqlExprContext.class,0);
		}
		public TerminalNode AS() { return getToken(EqlParser.AS, 0); }
		public SqlAliasContext sqlAlias() {
			return getRuleContext(SqlAliasContext.class,0);
		}
		public SqlDecorators_Context sqlDecorators_() {
			return getRuleContext(SqlDecorators_Context.class,0);
		}
		public SqlExprProjectionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlExprProjection; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlExprProjection(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlExprProjection(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlExprProjection(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlExprProjectionContext sqlExprProjection() throws RecognitionException {
		SqlExprProjectionContext _localctx = new SqlExprProjectionContext(_ctx, getState());
		enterRule(_localctx, 46, RULE_sqlExprProjection);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(466);
			((SqlExprProjectionContext)_localctx).expr = sqlExpr(0);
			setState(468);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,41,_ctx) ) {
			case 1:
				{
				setState(467);
				match(AS);
				}
				break;
			}
			setState(471);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,42,_ctx) ) {
			case 1:
				{
				setState(470);
				((SqlExprProjectionContext)_localctx).alias = sqlAlias();
				}
				break;
			}
			setState(474);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,43,_ctx) ) {
			case 1:
				{
				setState(473);
				((SqlExprProjectionContext)_localctx).decorators = sqlDecorators_();
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
	public static class SqlAllProjectionContext extends ParserRuleContext {
		public SqlQualifiedNameContext owner;
		public TerminalNode DOT_ASTERISK_() { return getToken(EqlParser.DOT_ASTERISK_, 0); }
		public SqlQualifiedNameContext sqlQualifiedName() {
			return getRuleContext(SqlQualifiedNameContext.class,0);
		}
		public SqlAllProjectionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlAllProjection; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlAllProjection(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlAllProjection(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlAllProjection(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlAllProjectionContext sqlAllProjection() throws RecognitionException {
		SqlAllProjectionContext _localctx = new SqlAllProjectionContext(_ctx, getState());
		enterRule(_localctx, 48, RULE_sqlAllProjection);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(476);
			((SqlAllProjectionContext)_localctx).owner = sqlQualifiedName();
			setState(477);
			match(DOT_ASTERISK_);
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
	public static class SqlAliasContext extends ParserRuleContext {
		public SqlAlias_Context alias;
		public SqlAlias_Context sqlAlias_() {
			return getRuleContext(SqlAlias_Context.class,0);
		}
		public SqlAliasContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlAlias; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlAlias(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlAlias(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlAlias(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlAliasContext sqlAlias() throws RecognitionException {
		SqlAliasContext _localctx = new SqlAliasContext(_ctx, getState());
		enterRule(_localctx, 50, RULE_sqlAlias);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(479);
			((SqlAliasContext)_localctx).alias = sqlAlias_();
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
	public static class SqlAlias_Context extends ParserRuleContext {
		public SqlIdentifier_Context sqlIdentifier_() {
			return getRuleContext(SqlIdentifier_Context.class,0);
		}
		public TerminalNode STRING_() { return getToken(EqlParser.STRING_, 0); }
		public SqlAlias_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlAlias_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlAlias_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlAlias_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlAlias_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlAlias_Context sqlAlias_() throws RecognitionException {
		SqlAlias_Context _localctx = new SqlAlias_Context(_ctx, getState());
		enterRule(_localctx, 52, RULE_sqlAlias_);
		try {
			setState(483);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case POSITION:
			case LATERAL:
			case CROSS:
			case ORDER:
			case GROUP:
			case BEGIN:
			case DATE:
			case TIMESTAMP:
			case YEAR:
			case QUARTER:
			case MONTH:
			case DAY:
			case HOUR:
			case MINUTE:
			case SECOND:
			case COUNT:
			case CURRENT_USER:
			case NAME:
			case TYPE:
			case RECURSIVE:
			case UPSERT:
			case CONFLICT:
			case CATALOG_NAME:
			case CHARACTER_SET_CATALOG:
			case CHARACTER_SET_NAME:
			case CHARACTER_SET_SCHEMA:
			case CLASS_ORIGIN:
			case COBOL:
			case COLLATION_CATALOG:
			case COLLATION_NAME:
			case COLLATION_SCHEMA:
			case COLUMN_NAME:
			case COMMAND_FUNCTION:
			case COMMITTED:
			case CONDITION_NUMBER:
			case CONNECTION_NAME:
			case CONSTRAINT_CATALOG:
			case CONSTRAINT_NAME:
			case CONSTRAINT_SCHEMA:
			case CURSOR_NAME:
			case DATA:
			case DATETIME_INTERVAL_CODE:
			case DATETIME_INTERVAL_PRECISION:
			case DYNAMIC_FUNCTION:
			case FORTRAN:
			case LENGTH:
			case MESSAGE_LENGTH:
			case MESSAGE_OCTET_LENGTH:
			case MESSAGE_TEXT:
			case MORE92:
			case MUMPS:
			case NULLABLE:
			case NUMBER:
			case PASCAL:
			case PLI:
			case REPEATABLE:
			case RETURNED_LENGTH:
			case RETURNED_OCTET_LENGTH:
			case RETURNED_SQLSTATE:
			case ROW_COUNT:
			case SCALE:
			case SCHEMA_NAME:
			case SERIALIZABLE:
			case SERVER_NAME:
			case SUBCLASS_ORIGIN:
			case TABLE_NAME:
			case UNCOMMITTED:
			case UNNAMED:
			case BIT_LENGTH:
			case COALESCE:
			case CURRENT_DATE:
			case END:
			case FOLLOWING:
			case GROUPS:
			case INDICATOR:
			case INSENSITIVE:
			case LANGUAGE:
			case NULLS:
			case LEVEL:
			case LOWER:
			case OCTET_LENGTH:
			case PRECEDING:
			case RANGE:
			case ROW:
			case SECTION:
			case SESSION:
			case TUMBLE:
			case HOP:
			case UNBOUNDED:
			case UPPER:
			case USER:
			case VALUE:
			case WINDOW:
			case WORK:
			case ZONE:
			case ROLLUP:
			case CUBE:
			case NOWAIT:
			case SKIP_:
			case LOCKED:
			case IDENTIFIER_:
				enterOuterAlt(_localctx, 1);
				{
				setState(481);
				sqlIdentifier_();
				}
				break;
			case STRING_:
				enterOuterAlt(_localctx, 2);
				{
				setState(482);
				match(STRING_);
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
	public static class SqlFromContext extends ParserRuleContext {
		public SqlDecorators_Context decorators;
		public TableSources_Context tableSources;
		public TerminalNode FROM() { return getToken(EqlParser.FROM, 0); }
		public TableSources_Context tableSources_() {
			return getRuleContext(TableSources_Context.class,0);
		}
		public SqlDecorators_Context sqlDecorators_() {
			return getRuleContext(SqlDecorators_Context.class,0);
		}
		public SqlFromContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlFrom; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlFrom(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlFrom(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlFrom(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlFromContext sqlFrom() throws RecognitionException {
		SqlFromContext _localctx = new SqlFromContext(_ctx, getState());
		enterRule(_localctx, 54, RULE_sqlFrom);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(485);
			match(FROM);
			setState(487);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==AT_) {
				{
				setState(486);
				((SqlFromContext)_localctx).decorators = sqlDecorators_();
				}
			}

			setState(489);
			((SqlFromContext)_localctx).tableSources = tableSources_();
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
	public static class TableSources_Context extends ParserRuleContext {
		public SqlTableSourceContext e;
		public List<SqlTableSourceContext> sqlTableSource() {
			return getRuleContexts(SqlTableSourceContext.class);
		}
		public SqlTableSourceContext sqlTableSource(int i) {
			return getRuleContext(SqlTableSourceContext.class,i);
		}
		public List<TerminalNode> COMMA_() { return getTokens(EqlParser.COMMA_); }
		public TerminalNode COMMA_(int i) {
			return getToken(EqlParser.COMMA_, i);
		}
		public TableSources_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_tableSources_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterTableSources_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitTableSources_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitTableSources_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TableSources_Context tableSources_() throws RecognitionException {
		TableSources_Context _localctx = new TableSources_Context(_ctx, getState());
		enterRule(_localctx, 56, RULE_tableSources_);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(491);
			((TableSources_Context)_localctx).e = sqlTableSource(0);
			setState(496);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,46,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(492);
					match(COMMA_);
					setState(493);
					((TableSources_Context)_localctx).e = sqlTableSource(0);
					}
					} 
				}
				setState(498);
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
	public static class SqlTableSourceContext extends ParserRuleContext {
		public SqlTableSourceContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlTableSource; }
	 
		public SqlTableSourceContext() { }
		public void copyFrom(SqlTableSourceContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlHopTableSource_exContext extends SqlTableSourceContext {
		public SqlHopTableSourceContext sqlHopTableSource() {
			return getRuleContext(SqlHopTableSourceContext.class,0);
		}
		public SqlHopTableSource_exContext(SqlTableSourceContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlHopTableSource_ex(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlHopTableSource_ex(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlHopTableSource_ex(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlJoinTableSourceContext extends SqlTableSourceContext {
		public SqlTableSourceContext left;
		public JoinType_Context joinType;
		public SqlTableSource_joinRightContext right;
		public SqlExprContext condition;
		public SqlTableSourceContext sqlTableSource() {
			return getRuleContext(SqlTableSourceContext.class,0);
		}
		public JoinType_Context joinType_() {
			return getRuleContext(JoinType_Context.class,0);
		}
		public SqlTableSource_joinRightContext sqlTableSource_joinRight() {
			return getRuleContext(SqlTableSource_joinRightContext.class,0);
		}
		public TerminalNode ON() { return getToken(EqlParser.ON, 0); }
		public SqlExprContext sqlExpr() {
			return getRuleContext(SqlExprContext.class,0);
		}
		public SqlJoinTableSourceContext(SqlTableSourceContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlJoinTableSource(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlJoinTableSource(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlJoinTableSource(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlTumbleTableSource_exContext extends SqlTableSourceContext {
		public SqlTumbleTableSourceContext sqlTumbleTableSource() {
			return getRuleContext(SqlTumbleTableSourceContext.class,0);
		}
		public SqlTumbleTableSource_exContext(SqlTableSourceContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlTumbleTableSource_ex(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlTumbleTableSource_ex(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlTumbleTableSource_ex(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlSingleTableSource_exContext extends SqlTableSourceContext {
		public SqlSingleTableSourceContext sqlSingleTableSource() {
			return getRuleContext(SqlSingleTableSourceContext.class,0);
		}
		public SqlSingleTableSource_exContext(SqlTableSourceContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlSingleTableSource_ex(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlSingleTableSource_ex(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlSingleTableSource_ex(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlSubqueryTableSource_exContext extends SqlTableSourceContext {
		public SqlSubqueryTableSourceContext sqlSubqueryTableSource() {
			return getRuleContext(SqlSubqueryTableSourceContext.class,0);
		}
		public SqlSubqueryTableSource_exContext(SqlTableSourceContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlSubqueryTableSource_ex(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlSubqueryTableSource_ex(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlSubqueryTableSource_ex(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlValuesTableSource_exContext extends SqlTableSourceContext {
		public SqlValuesTableSourceContext sqlValuesTableSource() {
			return getRuleContext(SqlValuesTableSourceContext.class,0);
		}
		public SqlValuesTableSource_exContext(SqlTableSourceContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlValuesTableSource_ex(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlValuesTableSource_ex(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlValuesTableSource_ex(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlSessionTableSource_exContext extends SqlTableSourceContext {
		public SqlSessionTableSourceContext sqlSessionTableSource() {
			return getRuleContext(SqlSessionTableSourceContext.class,0);
		}
		public SqlSessionTableSource_exContext(SqlTableSourceContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlSessionTableSource_ex(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlSessionTableSource_ex(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlSessionTableSource_ex(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlTableSourceContext sqlTableSource() throws RecognitionException {
		return sqlTableSource(0);
	}

	private SqlTableSourceContext sqlTableSource(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		SqlTableSourceContext _localctx = new SqlTableSourceContext(_ctx, _parentState);
		SqlTableSourceContext _prevctx = _localctx;
		int _startState = 58;
		enterRecursionRule(_localctx, 58, RULE_sqlTableSource, _p);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(506);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,47,_ctx) ) {
			case 1:
				{
				_localctx = new SqlSingleTableSource_exContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;

				setState(500);
				sqlSingleTableSource();
				}
				break;
			case 2:
				{
				_localctx = new SqlSubqueryTableSource_exContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(501);
				sqlSubqueryTableSource();
				}
				break;
			case 3:
				{
				_localctx = new SqlTumbleTableSource_exContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(502);
				sqlTumbleTableSource();
				}
				break;
			case 4:
				{
				_localctx = new SqlHopTableSource_exContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(503);
				sqlHopTableSource();
				}
				break;
			case 5:
				{
				_localctx = new SqlSessionTableSource_exContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(504);
				sqlSessionTableSource();
				}
				break;
			case 6:
				{
				_localctx = new SqlValuesTableSource_exContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(505);
				sqlValuesTableSource();
				}
				break;
			}
			_ctx.stop = _input.LT(-1);
			setState(517);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,49,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					{
					_localctx = new SqlJoinTableSourceContext(new SqlTableSourceContext(_parentctx, _parentState));
					((SqlJoinTableSourceContext)_localctx).left = _prevctx;
					pushNewRecursionContext(_localctx, _startState, RULE_sqlTableSource);
					setState(508);
					if (!(precpred(_ctx, 1))) throw new FailedPredicateException(this, "precpred(_ctx, 1)");
					setState(509);
					((SqlJoinTableSourceContext)_localctx).joinType = joinType_();
					setState(510);
					((SqlJoinTableSourceContext)_localctx).right = sqlTableSource_joinRight();
					setState(513);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,48,_ctx) ) {
					case 1:
						{
						setState(511);
						match(ON);
						setState(512);
						((SqlJoinTableSourceContext)_localctx).condition = sqlExpr(0);
						}
						break;
					}
					}
					} 
				}
				setState(519);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,49,_ctx);
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
	public static class SqlSingleTableSourceContext extends ParserRuleContext {
		public SqlTableNameContext tableName;
		public SqlAliasContext alias;
		public SqlDecorators_Context decorators;
		public SqlTableNameContext sqlTableName() {
			return getRuleContext(SqlTableNameContext.class,0);
		}
		public TerminalNode AS() { return getToken(EqlParser.AS, 0); }
		public SqlAliasContext sqlAlias() {
			return getRuleContext(SqlAliasContext.class,0);
		}
		public SqlDecorators_Context sqlDecorators_() {
			return getRuleContext(SqlDecorators_Context.class,0);
		}
		public SqlSingleTableSourceContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlSingleTableSource; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlSingleTableSource(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlSingleTableSource(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlSingleTableSource(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlSingleTableSourceContext sqlSingleTableSource() throws RecognitionException {
		SqlSingleTableSourceContext _localctx = new SqlSingleTableSourceContext(_ctx, getState());
		enterRule(_localctx, 60, RULE_sqlSingleTableSource);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(520);
			((SqlSingleTableSourceContext)_localctx).tableName = sqlTableName();
			setState(522);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,50,_ctx) ) {
			case 1:
				{
				setState(521);
				match(AS);
				}
				break;
			}
			setState(525);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,51,_ctx) ) {
			case 1:
				{
				setState(524);
				((SqlSingleTableSourceContext)_localctx).alias = sqlAlias();
				}
				break;
			}
			setState(528);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,52,_ctx) ) {
			case 1:
				{
				setState(527);
				((SqlSingleTableSourceContext)_localctx).decorators = sqlDecorators_();
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
	public static class SqlTumbleTableSourceContext extends ParserRuleContext {
		public SqlIdentifier_Context tableName;
		public SqlIdentifier_Context timeColumn;
		public SqlIntervalExprContext interval;
		public SqlAliasContext alias;
		public SqlDecorators_Context decorators;
		public TerminalNode TUMBLE() { return getToken(EqlParser.TUMBLE, 0); }
		public TerminalNode LP_() { return getToken(EqlParser.LP_, 0); }
		public TerminalNode DOT_() { return getToken(EqlParser.DOT_, 0); }
		public TerminalNode COMMA_() { return getToken(EqlParser.COMMA_, 0); }
		public TerminalNode RP_() { return getToken(EqlParser.RP_, 0); }
		public List<SqlIdentifier_Context> sqlIdentifier_() {
			return getRuleContexts(SqlIdentifier_Context.class);
		}
		public SqlIdentifier_Context sqlIdentifier_(int i) {
			return getRuleContext(SqlIdentifier_Context.class,i);
		}
		public SqlIntervalExprContext sqlIntervalExpr() {
			return getRuleContext(SqlIntervalExprContext.class,0);
		}
		public TerminalNode AS() { return getToken(EqlParser.AS, 0); }
		public SqlAliasContext sqlAlias() {
			return getRuleContext(SqlAliasContext.class,0);
		}
		public SqlDecorators_Context sqlDecorators_() {
			return getRuleContext(SqlDecorators_Context.class,0);
		}
		public SqlTumbleTableSourceContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlTumbleTableSource; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlTumbleTableSource(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlTumbleTableSource(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlTumbleTableSource(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlTumbleTableSourceContext sqlTumbleTableSource() throws RecognitionException {
		SqlTumbleTableSourceContext _localctx = new SqlTumbleTableSourceContext(_ctx, getState());
		enterRule(_localctx, 62, RULE_sqlTumbleTableSource);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(530);
			match(TUMBLE);
			setState(531);
			match(LP_);
			setState(532);
			((SqlTumbleTableSourceContext)_localctx).tableName = sqlIdentifier_();
			setState(533);
			match(DOT_);
			setState(534);
			((SqlTumbleTableSourceContext)_localctx).timeColumn = sqlIdentifier_();
			setState(535);
			match(COMMA_);
			setState(536);
			((SqlTumbleTableSourceContext)_localctx).interval = sqlIntervalExpr();
			setState(537);
			match(RP_);
			setState(539);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,53,_ctx) ) {
			case 1:
				{
				setState(538);
				match(AS);
				}
				break;
			}
			setState(542);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,54,_ctx) ) {
			case 1:
				{
				setState(541);
				((SqlTumbleTableSourceContext)_localctx).alias = sqlAlias();
				}
				break;
			}
			setState(545);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,55,_ctx) ) {
			case 1:
				{
				setState(544);
				((SqlTumbleTableSourceContext)_localctx).decorators = sqlDecorators_();
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
	public static class SqlHopTableSourceContext extends ParserRuleContext {
		public SqlIdentifier_Context tableName;
		public SqlIdentifier_Context timeColumn;
		public SqlIntervalExprContext slideInterval;
		public SqlIntervalExprContext sizeInterval;
		public SqlAliasContext alias;
		public SqlDecorators_Context decorators;
		public TerminalNode HOP() { return getToken(EqlParser.HOP, 0); }
		public TerminalNode LP_() { return getToken(EqlParser.LP_, 0); }
		public TerminalNode DOT_() { return getToken(EqlParser.DOT_, 0); }
		public List<TerminalNode> COMMA_() { return getTokens(EqlParser.COMMA_); }
		public TerminalNode COMMA_(int i) {
			return getToken(EqlParser.COMMA_, i);
		}
		public TerminalNode RP_() { return getToken(EqlParser.RP_, 0); }
		public List<SqlIdentifier_Context> sqlIdentifier_() {
			return getRuleContexts(SqlIdentifier_Context.class);
		}
		public SqlIdentifier_Context sqlIdentifier_(int i) {
			return getRuleContext(SqlIdentifier_Context.class,i);
		}
		public List<SqlIntervalExprContext> sqlIntervalExpr() {
			return getRuleContexts(SqlIntervalExprContext.class);
		}
		public SqlIntervalExprContext sqlIntervalExpr(int i) {
			return getRuleContext(SqlIntervalExprContext.class,i);
		}
		public TerminalNode AS() { return getToken(EqlParser.AS, 0); }
		public SqlAliasContext sqlAlias() {
			return getRuleContext(SqlAliasContext.class,0);
		}
		public SqlDecorators_Context sqlDecorators_() {
			return getRuleContext(SqlDecorators_Context.class,0);
		}
		public SqlHopTableSourceContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlHopTableSource; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlHopTableSource(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlHopTableSource(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlHopTableSource(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlHopTableSourceContext sqlHopTableSource() throws RecognitionException {
		SqlHopTableSourceContext _localctx = new SqlHopTableSourceContext(_ctx, getState());
		enterRule(_localctx, 64, RULE_sqlHopTableSource);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(547);
			match(HOP);
			setState(548);
			match(LP_);
			setState(549);
			((SqlHopTableSourceContext)_localctx).tableName = sqlIdentifier_();
			setState(550);
			match(DOT_);
			setState(551);
			((SqlHopTableSourceContext)_localctx).timeColumn = sqlIdentifier_();
			setState(552);
			match(COMMA_);
			setState(553);
			((SqlHopTableSourceContext)_localctx).slideInterval = sqlIntervalExpr();
			setState(554);
			match(COMMA_);
			setState(555);
			((SqlHopTableSourceContext)_localctx).sizeInterval = sqlIntervalExpr();
			setState(556);
			match(RP_);
			setState(558);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,56,_ctx) ) {
			case 1:
				{
				setState(557);
				match(AS);
				}
				break;
			}
			setState(561);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,57,_ctx) ) {
			case 1:
				{
				setState(560);
				((SqlHopTableSourceContext)_localctx).alias = sqlAlias();
				}
				break;
			}
			setState(564);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,58,_ctx) ) {
			case 1:
				{
				setState(563);
				((SqlHopTableSourceContext)_localctx).decorators = sqlDecorators_();
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
	public static class SqlSessionTableSourceContext extends ParserRuleContext {
		public SqlIdentifier_Context tableName;
		public SqlIdentifier_Context timeColumn;
		public SqlIntervalExprContext gapInterval;
		public SqlAliasContext alias;
		public SqlDecorators_Context decorators;
		public TerminalNode SESSION() { return getToken(EqlParser.SESSION, 0); }
		public TerminalNode LP_() { return getToken(EqlParser.LP_, 0); }
		public TerminalNode DOT_() { return getToken(EqlParser.DOT_, 0); }
		public TerminalNode COMMA_() { return getToken(EqlParser.COMMA_, 0); }
		public TerminalNode RP_() { return getToken(EqlParser.RP_, 0); }
		public List<SqlIdentifier_Context> sqlIdentifier_() {
			return getRuleContexts(SqlIdentifier_Context.class);
		}
		public SqlIdentifier_Context sqlIdentifier_(int i) {
			return getRuleContext(SqlIdentifier_Context.class,i);
		}
		public SqlIntervalExprContext sqlIntervalExpr() {
			return getRuleContext(SqlIntervalExprContext.class,0);
		}
		public TerminalNode AS() { return getToken(EqlParser.AS, 0); }
		public SqlAliasContext sqlAlias() {
			return getRuleContext(SqlAliasContext.class,0);
		}
		public SqlDecorators_Context sqlDecorators_() {
			return getRuleContext(SqlDecorators_Context.class,0);
		}
		public SqlSessionTableSourceContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlSessionTableSource; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlSessionTableSource(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlSessionTableSource(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlSessionTableSource(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlSessionTableSourceContext sqlSessionTableSource() throws RecognitionException {
		SqlSessionTableSourceContext _localctx = new SqlSessionTableSourceContext(_ctx, getState());
		enterRule(_localctx, 66, RULE_sqlSessionTableSource);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(566);
			match(SESSION);
			setState(567);
			match(LP_);
			setState(568);
			((SqlSessionTableSourceContext)_localctx).tableName = sqlIdentifier_();
			setState(569);
			match(DOT_);
			setState(570);
			((SqlSessionTableSourceContext)_localctx).timeColumn = sqlIdentifier_();
			setState(571);
			match(COMMA_);
			setState(572);
			((SqlSessionTableSourceContext)_localctx).gapInterval = sqlIntervalExpr();
			setState(573);
			match(RP_);
			setState(575);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,59,_ctx) ) {
			case 1:
				{
				setState(574);
				match(AS);
				}
				break;
			}
			setState(578);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,60,_ctx) ) {
			case 1:
				{
				setState(577);
				((SqlSessionTableSourceContext)_localctx).alias = sqlAlias();
				}
				break;
			}
			setState(581);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,61,_ctx) ) {
			case 1:
				{
				setState(580);
				((SqlSessionTableSourceContext)_localctx).decorators = sqlDecorators_();
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
	public static class SqlSubqueryTableSourceContext extends ParserRuleContext {
		public Token lateral;
		public SqlSelectContext query;
		public SqlAliasContext alias;
		public TerminalNode LP_() { return getToken(EqlParser.LP_, 0); }
		public TerminalNode RP_() { return getToken(EqlParser.RP_, 0); }
		public SqlSelectContext sqlSelect() {
			return getRuleContext(SqlSelectContext.class,0);
		}
		public SqlAliasContext sqlAlias() {
			return getRuleContext(SqlAliasContext.class,0);
		}
		public TerminalNode AS() { return getToken(EqlParser.AS, 0); }
		public TerminalNode LATERAL() { return getToken(EqlParser.LATERAL, 0); }
		public SqlSubqueryTableSourceContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlSubqueryTableSource; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlSubqueryTableSource(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlSubqueryTableSource(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlSubqueryTableSource(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlSubqueryTableSourceContext sqlSubqueryTableSource() throws RecognitionException {
		SqlSubqueryTableSourceContext _localctx = new SqlSubqueryTableSourceContext(_ctx, getState());
		enterRule(_localctx, 68, RULE_sqlSubqueryTableSource);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(584);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LATERAL) {
				{
				setState(583);
				((SqlSubqueryTableSourceContext)_localctx).lateral = match(LATERAL);
				}
			}

			setState(586);
			match(LP_);
			setState(587);
			((SqlSubqueryTableSourceContext)_localctx).query = sqlSelect(0);
			setState(588);
			match(RP_);
			setState(590);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==AS) {
				{
				setState(589);
				match(AS);
				}
			}

			setState(592);
			((SqlSubqueryTableSourceContext)_localctx).alias = sqlAlias();
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
	public static class SqlValuesTableSourceContext extends ParserRuleContext {
		public SqlRowList_Context rows;
		public SqlAliasContext alias;
		public TerminalNode LP_() { return getToken(EqlParser.LP_, 0); }
		public TerminalNode VALUES() { return getToken(EqlParser.VALUES, 0); }
		public TerminalNode RP_() { return getToken(EqlParser.RP_, 0); }
		public SqlRowList_Context sqlRowList_() {
			return getRuleContext(SqlRowList_Context.class,0);
		}
		public SqlAliasContext sqlAlias() {
			return getRuleContext(SqlAliasContext.class,0);
		}
		public TerminalNode AS() { return getToken(EqlParser.AS, 0); }
		public SqlColumnAliases__Context sqlColumnAliases__() {
			return getRuleContext(SqlColumnAliases__Context.class,0);
		}
		public SqlValuesTableSourceContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlValuesTableSource; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlValuesTableSource(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlValuesTableSource(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlValuesTableSource(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlValuesTableSourceContext sqlValuesTableSource() throws RecognitionException {
		SqlValuesTableSourceContext _localctx = new SqlValuesTableSourceContext(_ctx, getState());
		enterRule(_localctx, 70, RULE_sqlValuesTableSource);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(594);
			match(LP_);
			setState(595);
			match(VALUES);
			setState(596);
			((SqlValuesTableSourceContext)_localctx).rows = sqlRowList_();
			setState(597);
			match(RP_);
			setState(599);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==AS) {
				{
				setState(598);
				match(AS);
				}
			}

			setState(601);
			((SqlValuesTableSourceContext)_localctx).alias = sqlAlias();
			setState(603);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,65,_ctx) ) {
			case 1:
				{
				setState(602);
				sqlColumnAliases__();
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
	public static class SqlRowList_Context extends ParserRuleContext {
		public SqlMultiValueExprContext e;
		public List<SqlMultiValueExprContext> sqlMultiValueExpr() {
			return getRuleContexts(SqlMultiValueExprContext.class);
		}
		public SqlMultiValueExprContext sqlMultiValueExpr(int i) {
			return getRuleContext(SqlMultiValueExprContext.class,i);
		}
		public List<TerminalNode> COMMA_() { return getTokens(EqlParser.COMMA_); }
		public TerminalNode COMMA_(int i) {
			return getToken(EqlParser.COMMA_, i);
		}
		public SqlRowList_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlRowList_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlRowList_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlRowList_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlRowList_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlRowList_Context sqlRowList_() throws RecognitionException {
		SqlRowList_Context _localctx = new SqlRowList_Context(_ctx, getState());
		enterRule(_localctx, 72, RULE_sqlRowList_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(605);
			((SqlRowList_Context)_localctx).e = sqlMultiValueExpr();
			setState(610);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA_) {
				{
				{
				setState(606);
				match(COMMA_);
				setState(607);
				((SqlRowList_Context)_localctx).e = sqlMultiValueExpr();
				}
				}
				setState(612);
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
	public static class SqlColumnAliases__Context extends ParserRuleContext {
		public TerminalNode LP_() { return getToken(EqlParser.LP_, 0); }
		public List<SqlIdentifier_Context> sqlIdentifier_() {
			return getRuleContexts(SqlIdentifier_Context.class);
		}
		public SqlIdentifier_Context sqlIdentifier_(int i) {
			return getRuleContext(SqlIdentifier_Context.class,i);
		}
		public TerminalNode RP_() { return getToken(EqlParser.RP_, 0); }
		public List<TerminalNode> COMMA_() { return getTokens(EqlParser.COMMA_); }
		public TerminalNode COMMA_(int i) {
			return getToken(EqlParser.COMMA_, i);
		}
		public SqlColumnAliases__Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlColumnAliases__; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlColumnAliases__(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlColumnAliases__(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlColumnAliases__(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlColumnAliases__Context sqlColumnAliases__() throws RecognitionException {
		SqlColumnAliases__Context _localctx = new SqlColumnAliases__Context(_ctx, getState());
		enterRule(_localctx, 74, RULE_sqlColumnAliases__);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(613);
			match(LP_);
			setState(614);
			sqlIdentifier_();
			setState(619);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA_) {
				{
				{
				setState(615);
				match(COMMA_);
				setState(616);
				sqlIdentifier_();
				}
				}
				setState(621);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(622);
			match(RP_);
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
	public static class JoinType_Context extends ParserRuleContext {
		public InnerJoin_Context innerJoin_() {
			return getRuleContext(InnerJoin_Context.class,0);
		}
		public LeftJoin_Context leftJoin_() {
			return getRuleContext(LeftJoin_Context.class,0);
		}
		public RightJoin_Context rightJoin_() {
			return getRuleContext(RightJoin_Context.class,0);
		}
		public FullJoin_Context fullJoin_() {
			return getRuleContext(FullJoin_Context.class,0);
		}
		public CrossJoin_Context crossJoin_() {
			return getRuleContext(CrossJoin_Context.class,0);
		}
		public JoinType_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_joinType_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterJoinType_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitJoinType_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitJoinType_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final JoinType_Context joinType_() throws RecognitionException {
		JoinType_Context _localctx = new JoinType_Context(_ctx, getState());
		enterRule(_localctx, 76, RULE_joinType_);
		try {
			setState(629);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case JOIN:
			case INNER:
				enterOuterAlt(_localctx, 1);
				{
				setState(624);
				innerJoin_();
				}
				break;
			case LEFT:
				enterOuterAlt(_localctx, 2);
				{
				setState(625);
				leftJoin_();
				}
				break;
			case RIGHT:
				enterOuterAlt(_localctx, 3);
				{
				setState(626);
				rightJoin_();
				}
				break;
			case FULL:
				enterOuterAlt(_localctx, 4);
				{
				setState(627);
				fullJoin_();
				}
				break;
			case CROSS:
				enterOuterAlt(_localctx, 5);
				{
				setState(628);
				crossJoin_();
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
	public static class SqlTableSource_joinRightContext extends ParserRuleContext {
		public SqlSingleTableSourceContext sqlSingleTableSource() {
			return getRuleContext(SqlSingleTableSourceContext.class,0);
		}
		public SqlSubqueryTableSourceContext sqlSubqueryTableSource() {
			return getRuleContext(SqlSubqueryTableSourceContext.class,0);
		}
		public SqlTableSource_joinRightContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlTableSource_joinRight; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlTableSource_joinRight(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlTableSource_joinRight(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlTableSource_joinRight(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlTableSource_joinRightContext sqlTableSource_joinRight() throws RecognitionException {
		SqlTableSource_joinRightContext _localctx = new SqlTableSource_joinRightContext(_ctx, getState());
		enterRule(_localctx, 78, RULE_sqlTableSource_joinRight);
		try {
			setState(633);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,69,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(631);
				sqlSingleTableSource();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(632);
				sqlSubqueryTableSource();
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
	public static class InnerJoin_Context extends ParserRuleContext {
		public TerminalNode JOIN() { return getToken(EqlParser.JOIN, 0); }
		public TerminalNode INNER() { return getToken(EqlParser.INNER, 0); }
		public InnerJoin_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_innerJoin_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterInnerJoin_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitInnerJoin_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitInnerJoin_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final InnerJoin_Context innerJoin_() throws RecognitionException {
		InnerJoin_Context _localctx = new InnerJoin_Context(_ctx, getState());
		enterRule(_localctx, 80, RULE_innerJoin_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(636);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==INNER) {
				{
				setState(635);
				match(INNER);
				}
			}

			setState(638);
			match(JOIN);
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
	public static class FullJoin_Context extends ParserRuleContext {
		public TerminalNode FULL() { return getToken(EqlParser.FULL, 0); }
		public TerminalNode JOIN() { return getToken(EqlParser.JOIN, 0); }
		public TerminalNode OUTER() { return getToken(EqlParser.OUTER, 0); }
		public FullJoin_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_fullJoin_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterFullJoin_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitFullJoin_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitFullJoin_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final FullJoin_Context fullJoin_() throws RecognitionException {
		FullJoin_Context _localctx = new FullJoin_Context(_ctx, getState());
		enterRule(_localctx, 82, RULE_fullJoin_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(640);
			match(FULL);
			setState(642);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==OUTER) {
				{
				setState(641);
				match(OUTER);
				}
			}

			setState(644);
			match(JOIN);
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
	public static class LeftJoin_Context extends ParserRuleContext {
		public TerminalNode LEFT() { return getToken(EqlParser.LEFT, 0); }
		public TerminalNode JOIN() { return getToken(EqlParser.JOIN, 0); }
		public TerminalNode OUTER() { return getToken(EqlParser.OUTER, 0); }
		public LeftJoin_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_leftJoin_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterLeftJoin_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitLeftJoin_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitLeftJoin_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final LeftJoin_Context leftJoin_() throws RecognitionException {
		LeftJoin_Context _localctx = new LeftJoin_Context(_ctx, getState());
		enterRule(_localctx, 84, RULE_leftJoin_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(646);
			match(LEFT);
			setState(648);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==OUTER) {
				{
				setState(647);
				match(OUTER);
				}
			}

			setState(650);
			match(JOIN);
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
	public static class RightJoin_Context extends ParserRuleContext {
		public TerminalNode RIGHT() { return getToken(EqlParser.RIGHT, 0); }
		public TerminalNode JOIN() { return getToken(EqlParser.JOIN, 0); }
		public TerminalNode OUTER() { return getToken(EqlParser.OUTER, 0); }
		public RightJoin_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_rightJoin_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterRightJoin_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitRightJoin_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitRightJoin_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final RightJoin_Context rightJoin_() throws RecognitionException {
		RightJoin_Context _localctx = new RightJoin_Context(_ctx, getState());
		enterRule(_localctx, 86, RULE_rightJoin_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(652);
			match(RIGHT);
			setState(654);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==OUTER) {
				{
				setState(653);
				match(OUTER);
				}
			}

			setState(656);
			match(JOIN);
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
	public static class CrossJoin_Context extends ParserRuleContext {
		public TerminalNode CROSS() { return getToken(EqlParser.CROSS, 0); }
		public TerminalNode JOIN() { return getToken(EqlParser.JOIN, 0); }
		public CrossJoin_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_crossJoin_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterCrossJoin_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitCrossJoin_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitCrossJoin_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final CrossJoin_Context crossJoin_() throws RecognitionException {
		CrossJoin_Context _localctx = new CrossJoin_Context(_ctx, getState());
		enterRule(_localctx, 88, RULE_crossJoin_);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(658);
			match(CROSS);
			setState(659);
			match(JOIN);
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
	public static class SqlWhereContext extends ParserRuleContext {
		public SqlExprContext expr;
		public TerminalNode WHERE() { return getToken(EqlParser.WHERE, 0); }
		public SqlExprContext sqlExpr() {
			return getRuleContext(SqlExprContext.class,0);
		}
		public SqlWhereContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlWhere; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlWhere(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlWhere(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlWhere(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlWhereContext sqlWhere() throws RecognitionException {
		SqlWhereContext _localctx = new SqlWhereContext(_ctx, getState());
		enterRule(_localctx, 90, RULE_sqlWhere);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(661);
			match(WHERE);
			setState(662);
			((SqlWhereContext)_localctx).expr = sqlExpr(0);
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
	public static class SqlGroupByContext extends ParserRuleContext {
		public SqlGroupByItems_Context items;
		public TerminalNode GROUP() { return getToken(EqlParser.GROUP, 0); }
		public TerminalNode BY() { return getToken(EqlParser.BY, 0); }
		public SqlGroupByItems_Context sqlGroupByItems_() {
			return getRuleContext(SqlGroupByItems_Context.class,0);
		}
		public SqlGroupByContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlGroupBy; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlGroupBy(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlGroupBy(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlGroupBy(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlGroupByContext sqlGroupBy() throws RecognitionException {
		SqlGroupByContext _localctx = new SqlGroupByContext(_ctx, getState());
		enterRule(_localctx, 92, RULE_sqlGroupBy);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(664);
			match(GROUP);
			setState(665);
			match(BY);
			setState(666);
			((SqlGroupByContext)_localctx).items = sqlGroupByItems_();
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
	public static class SqlGroupByItems_Context extends ParserRuleContext {
		public SqlGroupByItemContext e;
		public List<SqlGroupByItemContext> sqlGroupByItem() {
			return getRuleContexts(SqlGroupByItemContext.class);
		}
		public SqlGroupByItemContext sqlGroupByItem(int i) {
			return getRuleContext(SqlGroupByItemContext.class,i);
		}
		public List<TerminalNode> COMMA_() { return getTokens(EqlParser.COMMA_); }
		public TerminalNode COMMA_(int i) {
			return getToken(EqlParser.COMMA_, i);
		}
		public SqlGroupByItems_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlGroupByItems_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlGroupByItems_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlGroupByItems_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlGroupByItems_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlGroupByItems_Context sqlGroupByItems_() throws RecognitionException {
		SqlGroupByItems_Context _localctx = new SqlGroupByItems_Context(_ctx, getState());
		enterRule(_localctx, 94, RULE_sqlGroupByItems_);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(668);
			((SqlGroupByItems_Context)_localctx).e = sqlGroupByItem();
			setState(673);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,74,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(669);
					match(COMMA_);
					setState(670);
					((SqlGroupByItems_Context)_localctx).e = sqlGroupByItem();
					}
					} 
				}
				setState(675);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,74,_ctx);
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
	public static class SqlHavingContext extends ParserRuleContext {
		public SqlExprContext expr;
		public TerminalNode HAVING() { return getToken(EqlParser.HAVING, 0); }
		public SqlExprContext sqlExpr() {
			return getRuleContext(SqlExprContext.class,0);
		}
		public SqlHavingContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlHaving; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlHaving(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlHaving(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlHaving(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlHavingContext sqlHaving() throws RecognitionException {
		SqlHavingContext _localctx = new SqlHavingContext(_ctx, getState());
		enterRule(_localctx, 96, RULE_sqlHaving);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(676);
			match(HAVING);
			setState(677);
			((SqlHavingContext)_localctx).expr = sqlExpr(0);
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
	public static class SqlLimitContext extends ParserRuleContext {
		public SqlLimitContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlLimit; }
	 
		public SqlLimitContext() { }
		public void copyFrom(SqlLimitContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlLimit_fetchFirstContext extends SqlLimitContext {
		public SqlExpr_limitRowCountContext limit;
		public TerminalNode FETCH() { return getToken(EqlParser.FETCH, 0); }
		public TerminalNode FIRST() { return getToken(EqlParser.FIRST, 0); }
		public TerminalNode ROWS() { return getToken(EqlParser.ROWS, 0); }
		public TerminalNode ONLY() { return getToken(EqlParser.ONLY, 0); }
		public SqlExpr_limitRowCountContext sqlExpr_limitRowCount() {
			return getRuleContext(SqlExpr_limitRowCountContext.class,0);
		}
		public SqlLimit_fetchFirstContext(SqlLimitContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlLimit_fetchFirst(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlLimit_fetchFirst(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlLimit_fetchFirst(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlLimit_offsetFetchContext extends SqlLimitContext {
		public SqlExpr_limitOffsetContext offset;
		public SqlExpr_limitRowCountContext limit;
		public TerminalNode OFFSET() { return getToken(EqlParser.OFFSET, 0); }
		public List<TerminalNode> ROWS() { return getTokens(EqlParser.ROWS); }
		public TerminalNode ROWS(int i) {
			return getToken(EqlParser.ROWS, i);
		}
		public TerminalNode FETCH() { return getToken(EqlParser.FETCH, 0); }
		public TerminalNode NEXT() { return getToken(EqlParser.NEXT, 0); }
		public TerminalNode ONLY() { return getToken(EqlParser.ONLY, 0); }
		public SqlExpr_limitOffsetContext sqlExpr_limitOffset() {
			return getRuleContext(SqlExpr_limitOffsetContext.class,0);
		}
		public SqlExpr_limitRowCountContext sqlExpr_limitRowCount() {
			return getRuleContext(SqlExpr_limitRowCountContext.class,0);
		}
		public SqlLimit_offsetFetchContext(SqlLimitContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlLimit_offsetFetch(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlLimit_offsetFetch(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlLimit_offsetFetch(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlLimit_limitContext extends SqlLimitContext {
		public SqlExpr_limitRowCountContext limit;
		public SqlExpr_limitOffsetContext offset;
		public TerminalNode LIMIT() { return getToken(EqlParser.LIMIT, 0); }
		public SqlExpr_limitRowCountContext sqlExpr_limitRowCount() {
			return getRuleContext(SqlExpr_limitRowCountContext.class,0);
		}
		public TerminalNode OFFSET() { return getToken(EqlParser.OFFSET, 0); }
		public SqlExpr_limitOffsetContext sqlExpr_limitOffset() {
			return getRuleContext(SqlExpr_limitOffsetContext.class,0);
		}
		public SqlLimit_limitContext(SqlLimitContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlLimit_limit(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlLimit_limit(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlLimit_limit(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlLimitContext sqlLimit() throws RecognitionException {
		SqlLimitContext _localctx = new SqlLimitContext(_ctx, getState());
		enterRule(_localctx, 98, RULE_sqlLimit);
		try {
			setState(700);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case LIMIT:
				_localctx = new SqlLimit_limitContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(679);
				match(LIMIT);
				setState(680);
				((SqlLimit_limitContext)_localctx).limit = sqlExpr_limitRowCount();
				setState(683);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,75,_ctx) ) {
				case 1:
					{
					setState(681);
					match(OFFSET);
					setState(682);
					((SqlLimit_limitContext)_localctx).offset = sqlExpr_limitOffset();
					}
					break;
				}
				}
				break;
			case FETCH:
				_localctx = new SqlLimit_fetchFirstContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(685);
				match(FETCH);
				setState(686);
				match(FIRST);
				setState(687);
				((SqlLimit_fetchFirstContext)_localctx).limit = sqlExpr_limitRowCount();
				setState(688);
				match(ROWS);
				setState(689);
				match(ONLY);
				}
				break;
			case OFFSET:
				_localctx = new SqlLimit_offsetFetchContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(691);
				match(OFFSET);
				setState(692);
				((SqlLimit_offsetFetchContext)_localctx).offset = sqlExpr_limitOffset();
				setState(693);
				match(ROWS);
				setState(694);
				match(FETCH);
				setState(695);
				match(NEXT);
				setState(696);
				((SqlLimit_offsetFetchContext)_localctx).limit = sqlExpr_limitRowCount();
				setState(697);
				match(ROWS);
				setState(698);
				match(ONLY);
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
	public static class SqlExpr_limitRowCountContext extends ParserRuleContext {
		public SqlNumberLiteralContext sqlNumberLiteral() {
			return getRuleContext(SqlNumberLiteralContext.class,0);
		}
		public SqlParameterMarkerContext sqlParameterMarker() {
			return getRuleContext(SqlParameterMarkerContext.class,0);
		}
		public SqlExpr_limitRowCountContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlExpr_limitRowCount; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlExpr_limitRowCount(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlExpr_limitRowCount(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlExpr_limitRowCount(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlExpr_limitRowCountContext sqlExpr_limitRowCount() throws RecognitionException {
		SqlExpr_limitRowCountContext _localctx = new SqlExpr_limitRowCountContext(_ctx, getState());
		enterRule(_localctx, 100, RULE_sqlExpr_limitRowCount);
		try {
			setState(704);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case NUMBER_:
				enterOuterAlt(_localctx, 1);
				{
				setState(702);
				sqlNumberLiteral();
				}
				break;
			case QUESTION_:
				enterOuterAlt(_localctx, 2);
				{
				setState(703);
				sqlParameterMarker();
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
	public static class SqlExpr_limitOffsetContext extends ParserRuleContext {
		public SqlNumberLiteralContext sqlNumberLiteral() {
			return getRuleContext(SqlNumberLiteralContext.class,0);
		}
		public SqlParameterMarkerContext sqlParameterMarker() {
			return getRuleContext(SqlParameterMarkerContext.class,0);
		}
		public SqlExpr_limitOffsetContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlExpr_limitOffset; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlExpr_limitOffset(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlExpr_limitOffset(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlExpr_limitOffset(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlExpr_limitOffsetContext sqlExpr_limitOffset() throws RecognitionException {
		SqlExpr_limitOffsetContext _localctx = new SqlExpr_limitOffsetContext(_ctx, getState());
		enterRule(_localctx, 102, RULE_sqlExpr_limitOffset);
		try {
			setState(708);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case NUMBER_:
				enterOuterAlt(_localctx, 1);
				{
				setState(706);
				sqlNumberLiteral();
				}
				break;
			case QUESTION_:
				enterOuterAlt(_localctx, 2);
				{
				setState(707);
				sqlParameterMarker();
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
	public static class SqlSubQueryExprContext extends ParserRuleContext {
		public SqlSelectContext select;
		public TerminalNode LP_() { return getToken(EqlParser.LP_, 0); }
		public TerminalNode RP_() { return getToken(EqlParser.RP_, 0); }
		public SqlSelectContext sqlSelect() {
			return getRuleContext(SqlSelectContext.class,0);
		}
		public SqlSubQueryExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlSubQueryExpr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlSubQueryExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlSubQueryExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlSubQueryExpr(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlSubQueryExprContext sqlSubQueryExpr() throws RecognitionException {
		SqlSubQueryExprContext _localctx = new SqlSubQueryExprContext(_ctx, getState());
		enterRule(_localctx, 104, RULE_sqlSubQueryExpr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(710);
			match(LP_);
			setState(711);
			((SqlSubQueryExprContext)_localctx).select = sqlSelect(0);
			setState(712);
			match(RP_);
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
	public static class ForUpdate_Context extends ParserRuleContext {
		public TerminalNode FOR() { return getToken(EqlParser.FOR, 0); }
		public TerminalNode UPDATE() { return getToken(EqlParser.UPDATE, 0); }
		public ForUpdate_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_forUpdate_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterForUpdate_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitForUpdate_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitForUpdate_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ForUpdate_Context forUpdate_() throws RecognitionException {
		ForUpdate_Context _localctx = new ForUpdate_Context(_ctx, getState());
		enterRule(_localctx, 106, RULE_forUpdate_);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(714);
			match(FOR);
			setState(715);
			match(UPDATE);
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
	public static class SkipLocked_Context extends ParserRuleContext {
		public TerminalNode SKIP_() { return getToken(EqlParser.SKIP_, 0); }
		public TerminalNode LOCKED() { return getToken(EqlParser.LOCKED, 0); }
		public SkipLocked_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_skipLocked_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSkipLocked_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSkipLocked_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSkipLocked_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SkipLocked_Context skipLocked_() throws RecognitionException {
		SkipLocked_Context _localctx = new SkipLocked_Context(_ctx, getState());
		enterRule(_localctx, 108, RULE_skipLocked_);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(717);
			match(SKIP_);
			setState(718);
			match(LOCKED);
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
	public static class SqlParameterMarkerContext extends ParserRuleContext {
		public TerminalNode QUESTION_() { return getToken(EqlParser.QUESTION_, 0); }
		public SqlParameterMarkerContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlParameterMarker; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlParameterMarker(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlParameterMarker(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlParameterMarker(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlParameterMarkerContext sqlParameterMarker() throws RecognitionException {
		SqlParameterMarkerContext _localctx = new SqlParameterMarkerContext(_ctx, getState());
		enterRule(_localctx, 110, RULE_sqlParameterMarker);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(720);
			match(QUESTION_);
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
	public static class SqlLiteralContext extends ParserRuleContext {
		public SqlStringLiteralContext sqlStringLiteral() {
			return getRuleContext(SqlStringLiteralContext.class,0);
		}
		public SqlNumberLiteralContext sqlNumberLiteral() {
			return getRuleContext(SqlNumberLiteralContext.class,0);
		}
		public SqlDateTimeLiteralContext sqlDateTimeLiteral() {
			return getRuleContext(SqlDateTimeLiteralContext.class,0);
		}
		public SqlHexadecimalLiteralContext sqlHexadecimalLiteral() {
			return getRuleContext(SqlHexadecimalLiteralContext.class,0);
		}
		public SqlBitValueLiteralContext sqlBitValueLiteral() {
			return getRuleContext(SqlBitValueLiteralContext.class,0);
		}
		public SqlBooleanLiteralContext sqlBooleanLiteral() {
			return getRuleContext(SqlBooleanLiteralContext.class,0);
		}
		public SqlNullLiteralContext sqlNullLiteral() {
			return getRuleContext(SqlNullLiteralContext.class,0);
		}
		public SqlLiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlLiteral; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlLiteral(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlLiteral(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlLiteral(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlLiteralContext sqlLiteral() throws RecognitionException {
		SqlLiteralContext _localctx = new SqlLiteralContext(_ctx, getState());
		enterRule(_localctx, 112, RULE_sqlLiteral);
		try {
			setState(729);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case STRING_:
				enterOuterAlt(_localctx, 1);
				{
				setState(722);
				sqlStringLiteral();
				}
				break;
			case NUMBER_:
				enterOuterAlt(_localctx, 2);
				{
				setState(723);
				sqlNumberLiteral();
				}
				break;
			case DATE:
			case TIME:
			case TIMESTAMP:
				enterOuterAlt(_localctx, 3);
				{
				setState(724);
				sqlDateTimeLiteral();
				}
				break;
			case HEX_DIGIT_:
				enterOuterAlt(_localctx, 4);
				{
				setState(725);
				sqlHexadecimalLiteral();
				}
				break;
			case BIT_NUM_:
				enterOuterAlt(_localctx, 5);
				{
				setState(726);
				sqlBitValueLiteral();
				}
				break;
			case TRUE:
			case FALSE:
				enterOuterAlt(_localctx, 6);
				{
				setState(727);
				sqlBooleanLiteral();
				}
				break;
			case NULL:
				enterOuterAlt(_localctx, 7);
				{
				setState(728);
				sqlNullLiteral();
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
	public static class SqlStringLiteralContext extends ParserRuleContext {
		public Token value;
		public TerminalNode STRING_() { return getToken(EqlParser.STRING_, 0); }
		public SqlStringLiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlStringLiteral; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlStringLiteral(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlStringLiteral(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlStringLiteral(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlStringLiteralContext sqlStringLiteral() throws RecognitionException {
		SqlStringLiteralContext _localctx = new SqlStringLiteralContext(_ctx, getState());
		enterRule(_localctx, 114, RULE_sqlStringLiteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(731);
			((SqlStringLiteralContext)_localctx).value = match(STRING_);
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
	public static class SqlNumberLiteralContext extends ParserRuleContext {
		public Token value;
		public TerminalNode NUMBER_() { return getToken(EqlParser.NUMBER_, 0); }
		public SqlNumberLiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlNumberLiteral; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlNumberLiteral(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlNumberLiteral(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlNumberLiteral(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlNumberLiteralContext sqlNumberLiteral() throws RecognitionException {
		SqlNumberLiteralContext _localctx = new SqlNumberLiteralContext(_ctx, getState());
		enterRule(_localctx, 116, RULE_sqlNumberLiteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(733);
			((SqlNumberLiteralContext)_localctx).value = match(NUMBER_);
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
	public static class SqlDateTimeLiteralContext extends ParserRuleContext {
		public Token type;
		public Token value;
		public TerminalNode STRING_() { return getToken(EqlParser.STRING_, 0); }
		public TerminalNode DATE() { return getToken(EqlParser.DATE, 0); }
		public TerminalNode TIME() { return getToken(EqlParser.TIME, 0); }
		public TerminalNode TIMESTAMP() { return getToken(EqlParser.TIMESTAMP, 0); }
		public SqlDateTimeLiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlDateTimeLiteral; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlDateTimeLiteral(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlDateTimeLiteral(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlDateTimeLiteral(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlDateTimeLiteralContext sqlDateTimeLiteral() throws RecognitionException {
		SqlDateTimeLiteralContext _localctx = new SqlDateTimeLiteralContext(_ctx, getState());
		enterRule(_localctx, 118, RULE_sqlDateTimeLiteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(735);
			((SqlDateTimeLiteralContext)_localctx).type = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 129)) & ~0x3f) == 0 && ((1L << (_la - 129)) & 7L) != 0)) ) {
				((SqlDateTimeLiteralContext)_localctx).type = (Token)_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(736);
			((SqlDateTimeLiteralContext)_localctx).value = match(STRING_);
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
	public static class SqlHexadecimalLiteralContext extends ParserRuleContext {
		public Token value;
		public TerminalNode HEX_DIGIT_() { return getToken(EqlParser.HEX_DIGIT_, 0); }
		public SqlHexadecimalLiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlHexadecimalLiteral; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlHexadecimalLiteral(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlHexadecimalLiteral(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlHexadecimalLiteral(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlHexadecimalLiteralContext sqlHexadecimalLiteral() throws RecognitionException {
		SqlHexadecimalLiteralContext _localctx = new SqlHexadecimalLiteralContext(_ctx, getState());
		enterRule(_localctx, 120, RULE_sqlHexadecimalLiteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(738);
			((SqlHexadecimalLiteralContext)_localctx).value = match(HEX_DIGIT_);
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
	public static class SqlBitValueLiteralContext extends ParserRuleContext {
		public Token value;
		public TerminalNode BIT_NUM_() { return getToken(EqlParser.BIT_NUM_, 0); }
		public SqlBitValueLiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlBitValueLiteral; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlBitValueLiteral(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlBitValueLiteral(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlBitValueLiteral(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlBitValueLiteralContext sqlBitValueLiteral() throws RecognitionException {
		SqlBitValueLiteralContext _localctx = new SqlBitValueLiteralContext(_ctx, getState());
		enterRule(_localctx, 122, RULE_sqlBitValueLiteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(740);
			((SqlBitValueLiteralContext)_localctx).value = match(BIT_NUM_);
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
	public static class SqlBooleanLiteralContext extends ParserRuleContext {
		public Token value;
		public TerminalNode TRUE() { return getToken(EqlParser.TRUE, 0); }
		public TerminalNode FALSE() { return getToken(EqlParser.FALSE, 0); }
		public SqlBooleanLiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlBooleanLiteral; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlBooleanLiteral(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlBooleanLiteral(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlBooleanLiteral(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlBooleanLiteralContext sqlBooleanLiteral() throws RecognitionException {
		SqlBooleanLiteralContext _localctx = new SqlBooleanLiteralContext(_ctx, getState());
		enterRule(_localctx, 124, RULE_sqlBooleanLiteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(742);
			((SqlBooleanLiteralContext)_localctx).value = _input.LT(1);
			_la = _input.LA(1);
			if ( !(_la==TRUE || _la==FALSE) ) {
				((SqlBooleanLiteralContext)_localctx).value = (Token)_errHandler.recoverInline(this);
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
	public static class SqlNullLiteralContext extends ParserRuleContext {
		public TerminalNode NULL() { return getToken(EqlParser.NULL, 0); }
		public SqlNullLiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlNullLiteral; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlNullLiteral(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlNullLiteral(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlNullLiteral(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlNullLiteralContext sqlNullLiteral() throws RecognitionException {
		SqlNullLiteralContext _localctx = new SqlNullLiteralContext(_ctx, getState());
		enterRule(_localctx, 126, RULE_sqlNullLiteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(744);
			match(NULL);
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
	public static class SqlIdentifier_Context extends ParserRuleContext {
		public TerminalNode IDENTIFIER_() { return getToken(EqlParser.IDENTIFIER_, 0); }
		public UnreservedWord_Context unreservedWord_() {
			return getRuleContext(UnreservedWord_Context.class,0);
		}
		public SqlIdentifier_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlIdentifier_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlIdentifier_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlIdentifier_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlIdentifier_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlIdentifier_Context sqlIdentifier_() throws RecognitionException {
		SqlIdentifier_Context _localctx = new SqlIdentifier_Context(_ctx, getState());
		enterRule(_localctx, 128, RULE_sqlIdentifier_);
		try {
			setState(748);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case IDENTIFIER_:
				enterOuterAlt(_localctx, 1);
				{
				setState(746);
				match(IDENTIFIER_);
				}
				break;
			case POSITION:
			case LATERAL:
			case CROSS:
			case ORDER:
			case GROUP:
			case BEGIN:
			case DATE:
			case TIMESTAMP:
			case YEAR:
			case QUARTER:
			case MONTH:
			case DAY:
			case HOUR:
			case MINUTE:
			case SECOND:
			case COUNT:
			case CURRENT_USER:
			case NAME:
			case TYPE:
			case RECURSIVE:
			case UPSERT:
			case CONFLICT:
			case CATALOG_NAME:
			case CHARACTER_SET_CATALOG:
			case CHARACTER_SET_NAME:
			case CHARACTER_SET_SCHEMA:
			case CLASS_ORIGIN:
			case COBOL:
			case COLLATION_CATALOG:
			case COLLATION_NAME:
			case COLLATION_SCHEMA:
			case COLUMN_NAME:
			case COMMAND_FUNCTION:
			case COMMITTED:
			case CONDITION_NUMBER:
			case CONNECTION_NAME:
			case CONSTRAINT_CATALOG:
			case CONSTRAINT_NAME:
			case CONSTRAINT_SCHEMA:
			case CURSOR_NAME:
			case DATA:
			case DATETIME_INTERVAL_CODE:
			case DATETIME_INTERVAL_PRECISION:
			case DYNAMIC_FUNCTION:
			case FORTRAN:
			case LENGTH:
			case MESSAGE_LENGTH:
			case MESSAGE_OCTET_LENGTH:
			case MESSAGE_TEXT:
			case MORE92:
			case MUMPS:
			case NULLABLE:
			case NUMBER:
			case PASCAL:
			case PLI:
			case REPEATABLE:
			case RETURNED_LENGTH:
			case RETURNED_OCTET_LENGTH:
			case RETURNED_SQLSTATE:
			case ROW_COUNT:
			case SCALE:
			case SCHEMA_NAME:
			case SERIALIZABLE:
			case SERVER_NAME:
			case SUBCLASS_ORIGIN:
			case TABLE_NAME:
			case UNCOMMITTED:
			case UNNAMED:
			case BIT_LENGTH:
			case COALESCE:
			case CURRENT_DATE:
			case END:
			case FOLLOWING:
			case GROUPS:
			case INDICATOR:
			case INSENSITIVE:
			case LANGUAGE:
			case NULLS:
			case LEVEL:
			case LOWER:
			case OCTET_LENGTH:
			case PRECEDING:
			case RANGE:
			case ROW:
			case SECTION:
			case SESSION:
			case TUMBLE:
			case HOP:
			case UNBOUNDED:
			case UPPER:
			case USER:
			case VALUE:
			case WINDOW:
			case WORK:
			case ZONE:
			case ROLLUP:
			case CUBE:
			case NOWAIT:
			case SKIP_:
			case LOCKED:
				enterOuterAlt(_localctx, 2);
				{
				setState(747);
				unreservedWord_();
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
	public static class UnreservedWord_Context extends ParserRuleContext {
		public TerminalNode CATALOG_NAME() { return getToken(EqlParser.CATALOG_NAME, 0); }
		public TerminalNode CHARACTER_SET_CATALOG() { return getToken(EqlParser.CHARACTER_SET_CATALOG, 0); }
		public TerminalNode CHARACTER_SET_NAME() { return getToken(EqlParser.CHARACTER_SET_NAME, 0); }
		public TerminalNode CHARACTER_SET_SCHEMA() { return getToken(EqlParser.CHARACTER_SET_SCHEMA, 0); }
		public TerminalNode CLASS_ORIGIN() { return getToken(EqlParser.CLASS_ORIGIN, 0); }
		public TerminalNode COBOL() { return getToken(EqlParser.COBOL, 0); }
		public TerminalNode COLLATION_CATALOG() { return getToken(EqlParser.COLLATION_CATALOG, 0); }
		public TerminalNode COLLATION_NAME() { return getToken(EqlParser.COLLATION_NAME, 0); }
		public TerminalNode COLLATION_SCHEMA() { return getToken(EqlParser.COLLATION_SCHEMA, 0); }
		public TerminalNode COLUMN_NAME() { return getToken(EqlParser.COLUMN_NAME, 0); }
		public TerminalNode COMMAND_FUNCTION() { return getToken(EqlParser.COMMAND_FUNCTION, 0); }
		public TerminalNode COMMITTED() { return getToken(EqlParser.COMMITTED, 0); }
		public TerminalNode CONDITION_NUMBER() { return getToken(EqlParser.CONDITION_NUMBER, 0); }
		public TerminalNode CONNECTION_NAME() { return getToken(EqlParser.CONNECTION_NAME, 0); }
		public TerminalNode CONSTRAINT_CATALOG() { return getToken(EqlParser.CONSTRAINT_CATALOG, 0); }
		public TerminalNode CONSTRAINT_NAME() { return getToken(EqlParser.CONSTRAINT_NAME, 0); }
		public TerminalNode CONSTRAINT_SCHEMA() { return getToken(EqlParser.CONSTRAINT_SCHEMA, 0); }
		public TerminalNode CURSOR_NAME() { return getToken(EqlParser.CURSOR_NAME, 0); }
		public TerminalNode DATA() { return getToken(EqlParser.DATA, 0); }
		public TerminalNode DATETIME_INTERVAL_CODE() { return getToken(EqlParser.DATETIME_INTERVAL_CODE, 0); }
		public TerminalNode DATETIME_INTERVAL_PRECISION() { return getToken(EqlParser.DATETIME_INTERVAL_PRECISION, 0); }
		public TerminalNode DYNAMIC_FUNCTION() { return getToken(EqlParser.DYNAMIC_FUNCTION, 0); }
		public TerminalNode FORTRAN() { return getToken(EqlParser.FORTRAN, 0); }
		public TerminalNode SECTION() { return getToken(EqlParser.SECTION, 0); }
		public TerminalNode LANGUAGE() { return getToken(EqlParser.LANGUAGE, 0); }
		public TerminalNode INSENSITIVE() { return getToken(EqlParser.INSENSITIVE, 0); }
		public TerminalNode INDICATOR() { return getToken(EqlParser.INDICATOR, 0); }
		public TerminalNode LENGTH() { return getToken(EqlParser.LENGTH, 0); }
		public TerminalNode MESSAGE_LENGTH() { return getToken(EqlParser.MESSAGE_LENGTH, 0); }
		public TerminalNode MESSAGE_OCTET_LENGTH() { return getToken(EqlParser.MESSAGE_OCTET_LENGTH, 0); }
		public TerminalNode MESSAGE_TEXT() { return getToken(EqlParser.MESSAGE_TEXT, 0); }
		public TerminalNode MORE92() { return getToken(EqlParser.MORE92, 0); }
		public TerminalNode MUMPS() { return getToken(EqlParser.MUMPS, 0); }
		public TerminalNode NAME() { return getToken(EqlParser.NAME, 0); }
		public TerminalNode NULLABLE() { return getToken(EqlParser.NULLABLE, 0); }
		public TerminalNode NUMBER() { return getToken(EqlParser.NUMBER, 0); }
		public TerminalNode PASCAL() { return getToken(EqlParser.PASCAL, 0); }
		public TerminalNode PLI() { return getToken(EqlParser.PLI, 0); }
		public TerminalNode REPEATABLE() { return getToken(EqlParser.REPEATABLE, 0); }
		public TerminalNode RETURNED_LENGTH() { return getToken(EqlParser.RETURNED_LENGTH, 0); }
		public TerminalNode RETURNED_OCTET_LENGTH() { return getToken(EqlParser.RETURNED_OCTET_LENGTH, 0); }
		public TerminalNode RETURNED_SQLSTATE() { return getToken(EqlParser.RETURNED_SQLSTATE, 0); }
		public TerminalNode ROW_COUNT() { return getToken(EqlParser.ROW_COUNT, 0); }
		public TerminalNode SCALE() { return getToken(EqlParser.SCALE, 0); }
		public TerminalNode SCHEMA_NAME() { return getToken(EqlParser.SCHEMA_NAME, 0); }
		public TerminalNode SERIALIZABLE() { return getToken(EqlParser.SERIALIZABLE, 0); }
		public TerminalNode SERVER_NAME() { return getToken(EqlParser.SERVER_NAME, 0); }
		public TerminalNode SUBCLASS_ORIGIN() { return getToken(EqlParser.SUBCLASS_ORIGIN, 0); }
		public TerminalNode TABLE_NAME() { return getToken(EqlParser.TABLE_NAME, 0); }
		public TerminalNode TYPE() { return getToken(EqlParser.TYPE, 0); }
		public TerminalNode UNCOMMITTED() { return getToken(EqlParser.UNCOMMITTED, 0); }
		public TerminalNode UNNAMED() { return getToken(EqlParser.UNNAMED, 0); }
		public TerminalNode VALUE() { return getToken(EqlParser.VALUE, 0); }
		public TerminalNode POSITION() { return getToken(EqlParser.POSITION, 0); }
		public TerminalNode ORDER() { return getToken(EqlParser.ORDER, 0); }
		public TerminalNode LEVEL() { return getToken(EqlParser.LEVEL, 0); }
		public TerminalNode SESSION() { return getToken(EqlParser.SESSION, 0); }
		public TerminalNode COUNT() { return getToken(EqlParser.COUNT, 0); }
		public TerminalNode COALESCE() { return getToken(EqlParser.COALESCE, 0); }
		public TerminalNode YEAR() { return getToken(EqlParser.YEAR, 0); }
		public TerminalNode MONTH() { return getToken(EqlParser.MONTH, 0); }
		public TerminalNode DAY() { return getToken(EqlParser.DAY, 0); }
		public TerminalNode HOUR() { return getToken(EqlParser.HOUR, 0); }
		public TerminalNode MINUTE() { return getToken(EqlParser.MINUTE, 0); }
		public TerminalNode SECOND() { return getToken(EqlParser.SECOND, 0); }
		public TerminalNode QUARTER() { return getToken(EqlParser.QUARTER, 0); }
		public TerminalNode LOWER() { return getToken(EqlParser.LOWER, 0); }
		public TerminalNode UPPER() { return getToken(EqlParser.UPPER, 0); }
		public TerminalNode ZONE() { return getToken(EqlParser.ZONE, 0); }
		public TerminalNode WORK() { return getToken(EqlParser.WORK, 0); }
		public TerminalNode RECURSIVE() { return getToken(EqlParser.RECURSIVE, 0); }
		public TerminalNode CURRENT_USER() { return getToken(EqlParser.CURRENT_USER, 0); }
		public TerminalNode USER() { return getToken(EqlParser.USER, 0); }
		public TerminalNode DATE() { return getToken(EqlParser.DATE, 0); }
		public TerminalNode OCTET_LENGTH() { return getToken(EqlParser.OCTET_LENGTH, 0); }
		public TerminalNode CURRENT_DATE() { return getToken(EqlParser.CURRENT_DATE, 0); }
		public TerminalNode BIT_LENGTH() { return getToken(EqlParser.BIT_LENGTH, 0); }
		public TerminalNode GROUP() { return getToken(EqlParser.GROUP, 0); }
		public TerminalNode TIMESTAMP() { return getToken(EqlParser.TIMESTAMP, 0); }
		public TerminalNode BEGIN() { return getToken(EqlParser.BEGIN, 0); }
		public TerminalNode END() { return getToken(EqlParser.END, 0); }
		public TerminalNode CROSS() { return getToken(EqlParser.CROSS, 0); }
		public TerminalNode RANGE() { return getToken(EqlParser.RANGE, 0); }
		public TerminalNode WINDOW() { return getToken(EqlParser.WINDOW, 0); }
		public TerminalNode GROUPS() { return getToken(EqlParser.GROUPS, 0); }
		public TerminalNode ROW() { return getToken(EqlParser.ROW, 0); }
		public TerminalNode PRECEDING() { return getToken(EqlParser.PRECEDING, 0); }
		public TerminalNode FOLLOWING() { return getToken(EqlParser.FOLLOWING, 0); }
		public TerminalNode UNBOUNDED() { return getToken(EqlParser.UNBOUNDED, 0); }
		public TerminalNode TUMBLE() { return getToken(EqlParser.TUMBLE, 0); }
		public TerminalNode HOP() { return getToken(EqlParser.HOP, 0); }
		public TerminalNode NULLS() { return getToken(EqlParser.NULLS, 0); }
		public TerminalNode LATERAL() { return getToken(EqlParser.LATERAL, 0); }
		public TerminalNode ROLLUP() { return getToken(EqlParser.ROLLUP, 0); }
		public TerminalNode CUBE() { return getToken(EqlParser.CUBE, 0); }
		public TerminalNode NOWAIT() { return getToken(EqlParser.NOWAIT, 0); }
		public TerminalNode SKIP_() { return getToken(EqlParser.SKIP_, 0); }
		public TerminalNode LOCKED() { return getToken(EqlParser.LOCKED, 0); }
		public TerminalNode UPSERT() { return getToken(EqlParser.UPSERT, 0); }
		public TerminalNode CONFLICT() { return getToken(EqlParser.CONFLICT, 0); }
		public UnreservedWord_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_unreservedWord_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterUnreservedWord_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitUnreservedWord_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitUnreservedWord_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final UnreservedWord_Context unreservedWord_() throws RecognitionException {
		UnreservedWord_Context _localctx = new UnreservedWord_Context(_ctx, getState());
		enterRule(_localctx, 130, RULE_unreservedWord_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(750);
			_la = _input.LA(1);
			if ( !(((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & 36451009505001473L) != 0) || ((((_la - 129)) & ~0x3f) == 0 && ((1L << (_la - 129)) & -1387549045940507L) != 0) || ((((_la - 193)) & ~0x3f) == 0 && ((1L << (_la - 193)) & 72341276627828735L) != 0) || ((((_la - 263)) & ~0x3f) == 0 && ((1L << (_la - 263)) & 3607524592334276609L) != 0) || ((((_la - 335)) & ~0x3f) == 0 && ((1L << (_la - 335)) & 33240323L) != 0)) ) {
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
	public static class SqlTableNameContext extends ParserRuleContext {
		public SqlQualifiedNameContext owner;
		public SqlIdentifier_Context name;
		public SqlIdentifier_Context sqlIdentifier_() {
			return getRuleContext(SqlIdentifier_Context.class,0);
		}
		public TerminalNode DOT_() { return getToken(EqlParser.DOT_, 0); }
		public SqlQualifiedNameContext sqlQualifiedName() {
			return getRuleContext(SqlQualifiedNameContext.class,0);
		}
		public SqlTableNameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlTableName; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlTableName(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlTableName(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlTableName(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlTableNameContext sqlTableName() throws RecognitionException {
		SqlTableNameContext _localctx = new SqlTableNameContext(_ctx, getState());
		enterRule(_localctx, 132, RULE_sqlTableName);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(755);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,81,_ctx) ) {
			case 1:
				{
				setState(752);
				((SqlTableNameContext)_localctx).owner = sqlQualifiedName();
				setState(753);
				match(DOT_);
				}
				break;
			}
			setState(757);
			((SqlTableNameContext)_localctx).name = sqlIdentifier_();
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
	public static class SqlColumnNameContext extends ParserRuleContext {
		public SqlQualifiedNameContext owner;
		public SqlIdentifier_Context name;
		public SqlIdentifier_Context sqlIdentifier_() {
			return getRuleContext(SqlIdentifier_Context.class,0);
		}
		public TerminalNode DOT_() { return getToken(EqlParser.DOT_, 0); }
		public SqlQualifiedNameContext sqlQualifiedName() {
			return getRuleContext(SqlQualifiedNameContext.class,0);
		}
		public SqlColumnNameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlColumnName; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlColumnName(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlColumnName(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlColumnName(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlColumnNameContext sqlColumnName() throws RecognitionException {
		SqlColumnNameContext _localctx = new SqlColumnNameContext(_ctx, getState());
		enterRule(_localctx, 134, RULE_sqlColumnName);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(762);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,82,_ctx) ) {
			case 1:
				{
				setState(759);
				((SqlColumnNameContext)_localctx).owner = sqlQualifiedName();
				setState(760);
				match(DOT_);
				}
				break;
			}
			setState(764);
			((SqlColumnNameContext)_localctx).name = sqlIdentifier_();
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
	public static class SqlQualifiedNameContext extends ParserRuleContext {
		public SqlIdentifier_Context name;
		public SqlQualifiedNameContext next;
		public SqlIdentifier_Context sqlIdentifier_() {
			return getRuleContext(SqlIdentifier_Context.class,0);
		}
		public TerminalNode DOT_() { return getToken(EqlParser.DOT_, 0); }
		public SqlQualifiedNameContext sqlQualifiedName() {
			return getRuleContext(SqlQualifiedNameContext.class,0);
		}
		public SqlQualifiedNameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlQualifiedName; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlQualifiedName(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlQualifiedName(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlQualifiedName(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlQualifiedNameContext sqlQualifiedName() throws RecognitionException {
		SqlQualifiedNameContext _localctx = new SqlQualifiedNameContext(_ctx, getState());
		enterRule(_localctx, 136, RULE_sqlQualifiedName);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(766);
			((SqlQualifiedNameContext)_localctx).name = sqlIdentifier_();
			setState(769);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,83,_ctx) ) {
			case 1:
				{
				setState(767);
				match(DOT_);
				setState(768);
				((SqlQualifiedNameContext)_localctx).next = sqlQualifiedName();
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
	public static class ColumnNames_Context extends ParserRuleContext {
		public SqlColumnNameContext e;
		public List<SqlColumnNameContext> sqlColumnName() {
			return getRuleContexts(SqlColumnNameContext.class);
		}
		public SqlColumnNameContext sqlColumnName(int i) {
			return getRuleContext(SqlColumnNameContext.class,i);
		}
		public List<TerminalNode> COMMA_() { return getTokens(EqlParser.COMMA_); }
		public TerminalNode COMMA_(int i) {
			return getToken(EqlParser.COMMA_, i);
		}
		public ColumnNames_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_columnNames_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterColumnNames_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitColumnNames_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitColumnNames_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ColumnNames_Context columnNames_() throws RecognitionException {
		ColumnNames_Context _localctx = new ColumnNames_Context(_ctx, getState());
		enterRule(_localctx, 138, RULE_columnNames_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(771);
			((ColumnNames_Context)_localctx).e = sqlColumnName();
			setState(776);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA_) {
				{
				{
				setState(772);
				match(COMMA_);
				setState(773);
				((ColumnNames_Context)_localctx).e = sqlColumnName();
				}
				}
				setState(778);
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
	public static class SqlExprContext extends ParserRuleContext {
		public SqlExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlExpr; }
	 
		public SqlExprContext() { }
		public void copyFrom(SqlExprContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlExpr_primary2Context extends SqlExprContext {
		public SqlExpr_primaryContext sqlExpr_primary() {
			return getRuleContext(SqlExpr_primaryContext.class,0);
		}
		public SqlExpr_primary2Context(SqlExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlExpr_primary2(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlExpr_primary2(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlExpr_primary2(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlOrExprContext extends SqlExprContext {
		public SqlExprContext left;
		public SqlExprContext right;
		public TerminalNode OR() { return getToken(EqlParser.OR, 0); }
		public List<SqlExprContext> sqlExpr() {
			return getRuleContexts(SqlExprContext.class);
		}
		public SqlExprContext sqlExpr(int i) {
			return getRuleContext(SqlExprContext.class,i);
		}
		public SqlOrExprContext(SqlExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlOrExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlOrExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlOrExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlNotExprContext extends SqlExprContext {
		public SqlExprContext expr;
		public TerminalNode NOT() { return getToken(EqlParser.NOT, 0); }
		public TerminalNode NOT_() { return getToken(EqlParser.NOT_, 0); }
		public SqlExprContext sqlExpr() {
			return getRuleContext(SqlExprContext.class,0);
		}
		public SqlNotExprContext(SqlExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlNotExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlNotExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlNotExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlAndExprContext extends SqlExprContext {
		public SqlExprContext left;
		public SqlExprContext right;
		public List<SqlExprContext> sqlExpr() {
			return getRuleContexts(SqlExprContext.class);
		}
		public SqlExprContext sqlExpr(int i) {
			return getRuleContext(SqlExprContext.class,i);
		}
		public TerminalNode AND() { return getToken(EqlParser.AND, 0); }
		public TerminalNode AND_() { return getToken(EqlParser.AND_, 0); }
		public SqlAndExprContext(SqlExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlAndExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlAndExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlAndExpr(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlExprContext sqlExpr() throws RecognitionException {
		return sqlExpr(0);
	}

	private SqlExprContext sqlExpr(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		SqlExprContext _localctx = new SqlExprContext(_ctx, _parentState);
		SqlExprContext _prevctx = _localctx;
		int _startState = 140;
		enterRecursionRule(_localctx, 140, RULE_sqlExpr, _p);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(783);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,85,_ctx) ) {
			case 1:
				{
				_localctx = new SqlNotExprContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;

				setState(780);
				_la = _input.LA(1);
				if ( !(_la==NOT_ || _la==NOT) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(781);
				((SqlNotExprContext)_localctx).expr = sqlExpr(4);
				}
				break;
			case 2:
				{
				_localctx = new SqlExpr_primary2Context(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(782);
				sqlExpr_primary(0);
				}
				break;
			}
			_ctx.stop = _input.LT(-1);
			setState(793);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,87,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(791);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,86,_ctx) ) {
					case 1:
						{
						_localctx = new SqlAndExprContext(new SqlExprContext(_parentctx, _parentState));
						((SqlAndExprContext)_localctx).left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_sqlExpr);
						setState(785);
						if (!(precpred(_ctx, 3))) throw new FailedPredicateException(this, "precpred(_ctx, 3)");
						setState(786);
						_la = _input.LA(1);
						if ( !(_la==AND_ || _la==AND) ) {
						_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(787);
						((SqlAndExprContext)_localctx).right = sqlExpr(4);
						}
						break;
					case 2:
						{
						_localctx = new SqlOrExprContext(new SqlExprContext(_parentctx, _parentState));
						((SqlOrExprContext)_localctx).left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_sqlExpr);
						setState(788);
						if (!(precpred(_ctx, 2))) throw new FailedPredicateException(this, "precpred(_ctx, 2)");
						setState(789);
						match(OR);
						setState(790);
						((SqlOrExprContext)_localctx).right = sqlExpr(3);
						}
						break;
					}
					} 
				}
				setState(795);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,87,_ctx);
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
	public static class SqlExpr_primaryContext extends ParserRuleContext {
		public SqlExpr_primaryContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlExpr_primary; }
	 
		public SqlExpr_primaryContext() { }
		public void copyFrom(SqlExpr_primaryContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlAndExpr_isTrueContext extends SqlExpr_primaryContext {
		public SqlExpr_primaryContext sqlExpr_primary() {
			return getRuleContext(SqlExpr_primaryContext.class,0);
		}
		public TerminalNode IS() { return getToken(EqlParser.IS, 0); }
		public TerminalNode TRUE() { return getToken(EqlParser.TRUE, 0); }
		public SqlAndExpr_isTrueContext(SqlExpr_primaryContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlAndExpr_isTrue(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlAndExpr_isTrue(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlAndExpr_isTrue(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlBinaryExpr_compareContext extends SqlExpr_primaryContext {
		public SqlExpr_primaryContext left;
		public ComparisonOperator_Context operator;
		public SqlExpr_predicateContext right;
		public SqlExpr_primaryContext sqlExpr_primary() {
			return getRuleContext(SqlExpr_primaryContext.class,0);
		}
		public ComparisonOperator_Context comparisonOperator_() {
			return getRuleContext(ComparisonOperator_Context.class,0);
		}
		public SqlExpr_predicateContext sqlExpr_predicate() {
			return getRuleContext(SqlExpr_predicateContext.class,0);
		}
		public SqlBinaryExpr_compareContext(SqlExpr_primaryContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlBinaryExpr_compare(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlBinaryExpr_compare(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlBinaryExpr_compare(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlExpr_predicate2Context extends SqlExpr_primaryContext {
		public SqlExpr_predicateContext sqlExpr_predicate() {
			return getRuleContext(SqlExpr_predicateContext.class,0);
		}
		public SqlExpr_predicate2Context(SqlExpr_primaryContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlExpr_predicate2(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlExpr_predicate2(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlExpr_predicate2(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlIsNullExprContext extends SqlExpr_primaryContext {
		public SqlExpr_primaryContext expr;
		public Token not;
		public TerminalNode IS() { return getToken(EqlParser.IS, 0); }
		public TerminalNode NULL() { return getToken(EqlParser.NULL, 0); }
		public SqlExpr_primaryContext sqlExpr_primary() {
			return getRuleContext(SqlExpr_primaryContext.class,0);
		}
		public TerminalNode NOT() { return getToken(EqlParser.NOT, 0); }
		public SqlIsNullExprContext(SqlExpr_primaryContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlIsNullExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlIsNullExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlIsNullExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlNotExpr_isNotFalseContext extends SqlExpr_primaryContext {
		public SqlExpr_primaryContext sqlExpr_primary() {
			return getRuleContext(SqlExpr_primaryContext.class,0);
		}
		public TerminalNode IS() { return getToken(EqlParser.IS, 0); }
		public TerminalNode NOT() { return getToken(EqlParser.NOT, 0); }
		public TerminalNode FALSE() { return getToken(EqlParser.FALSE, 0); }
		public SqlNotExpr_isNotFalseContext(SqlExpr_primaryContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlNotExpr_isNotFalse(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlNotExpr_isNotFalse(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlNotExpr_isNotFalse(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlIsNullExpr_isUnknownContext extends SqlExpr_primaryContext {
		public SqlExpr_primaryContext expr;
		public Token not;
		public TerminalNode IS() { return getToken(EqlParser.IS, 0); }
		public TerminalNode UNKNOWN() { return getToken(EqlParser.UNKNOWN, 0); }
		public SqlExpr_primaryContext sqlExpr_primary() {
			return getRuleContext(SqlExpr_primaryContext.class,0);
		}
		public TerminalNode NOT() { return getToken(EqlParser.NOT, 0); }
		public SqlIsNullExpr_isUnknownContext(SqlExpr_primaryContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlIsNullExpr_isUnknown(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlIsNullExpr_isUnknown(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlIsNullExpr_isUnknown(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlCompareWithQueryExprContext extends SqlExpr_primaryContext {
		public SqlExpr_primaryContext expr;
		public ComparisonOperator_Context operator;
		public Token compareRange;
		public SqlSubQueryExprContext query;
		public SqlExpr_primaryContext sqlExpr_primary() {
			return getRuleContext(SqlExpr_primaryContext.class,0);
		}
		public ComparisonOperator_Context comparisonOperator_() {
			return getRuleContext(ComparisonOperator_Context.class,0);
		}
		public SqlSubQueryExprContext sqlSubQueryExpr() {
			return getRuleContext(SqlSubQueryExprContext.class,0);
		}
		public TerminalNode ALL() { return getToken(EqlParser.ALL, 0); }
		public TerminalNode ANY() { return getToken(EqlParser.ANY, 0); }
		public TerminalNode SOME() { return getToken(EqlParser.SOME, 0); }
		public SqlCompareWithQueryExprContext(SqlExpr_primaryContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlCompareWithQueryExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlCompareWithQueryExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlCompareWithQueryExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlAndExpr_isFalseContext extends SqlExpr_primaryContext {
		public SqlExpr_primaryContext sqlExpr_primary() {
			return getRuleContext(SqlExpr_primaryContext.class,0);
		}
		public TerminalNode IS() { return getToken(EqlParser.IS, 0); }
		public TerminalNode FALSE() { return getToken(EqlParser.FALSE, 0); }
		public SqlAndExpr_isFalseContext(SqlExpr_primaryContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlAndExpr_isFalse(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlAndExpr_isFalse(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlAndExpr_isFalse(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlBinaryExpr_isNotDistinctFromContext extends SqlExpr_primaryContext {
		public SqlExpr_primaryContext sqlExpr_primary() {
			return getRuleContext(SqlExpr_primaryContext.class,0);
		}
		public TerminalNode IS() { return getToken(EqlParser.IS, 0); }
		public TerminalNode NOT() { return getToken(EqlParser.NOT, 0); }
		public TerminalNode DISTINCT() { return getToken(EqlParser.DISTINCT, 0); }
		public TerminalNode FROM() { return getToken(EqlParser.FROM, 0); }
		public SqlExpr_predicateContext sqlExpr_predicate() {
			return getRuleContext(SqlExpr_predicateContext.class,0);
		}
		public SqlBinaryExpr_isNotDistinctFromContext(SqlExpr_primaryContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlBinaryExpr_isNotDistinctFrom(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlBinaryExpr_isNotDistinctFrom(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlBinaryExpr_isNotDistinctFrom(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlNotExpr_isNotTrueContext extends SqlExpr_primaryContext {
		public SqlExpr_primaryContext sqlExpr_primary() {
			return getRuleContext(SqlExpr_primaryContext.class,0);
		}
		public TerminalNode IS() { return getToken(EqlParser.IS, 0); }
		public TerminalNode NOT() { return getToken(EqlParser.NOT, 0); }
		public TerminalNode TRUE() { return getToken(EqlParser.TRUE, 0); }
		public SqlNotExpr_isNotTrueContext(SqlExpr_primaryContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlNotExpr_isNotTrue(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlNotExpr_isNotTrue(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlNotExpr_isNotTrue(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlNotExpr_isDistinctFromContext extends SqlExpr_primaryContext {
		public SqlExpr_primaryContext sqlExpr_primary() {
			return getRuleContext(SqlExpr_primaryContext.class,0);
		}
		public TerminalNode IS() { return getToken(EqlParser.IS, 0); }
		public TerminalNode DISTINCT() { return getToken(EqlParser.DISTINCT, 0); }
		public TerminalNode FROM() { return getToken(EqlParser.FROM, 0); }
		public SqlExpr_predicateContext sqlExpr_predicate() {
			return getRuleContext(SqlExpr_predicateContext.class,0);
		}
		public SqlNotExpr_isDistinctFromContext(SqlExpr_primaryContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlNotExpr_isDistinctFrom(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlNotExpr_isDistinctFrom(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlNotExpr_isDistinctFrom(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlExpr_primaryContext sqlExpr_primary() throws RecognitionException {
		return sqlExpr_primary(0);
	}

	private SqlExpr_primaryContext sqlExpr_primary(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		SqlExpr_primaryContext _localctx = new SqlExpr_primaryContext(_ctx, _parentState);
		SqlExpr_primaryContext _prevctx = _localctx;
		int _startState = 142;
		enterRecursionRule(_localctx, 142, RULE_sqlExpr_primary, _p);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			{
			_localctx = new SqlExpr_predicate2Context(_localctx);
			_ctx = _localctx;
			_prevctx = _localctx;

			setState(797);
			sqlExpr_predicate();
			}
			_ctx.stop = _input.LT(-1);
			setState(847);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,91,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(845);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,90,_ctx) ) {
					case 1:
						{
						_localctx = new SqlIsNullExprContext(new SqlExpr_primaryContext(_parentctx, _parentState));
						((SqlIsNullExprContext)_localctx).expr = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_sqlExpr_primary);
						setState(799);
						if (!(precpred(_ctx, 11))) throw new FailedPredicateException(this, "precpred(_ctx, 11)");
						setState(800);
						match(IS);
						setState(802);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==NOT) {
							{
							setState(801);
							((SqlIsNullExprContext)_localctx).not = match(NOT);
							}
						}

						setState(804);
						match(NULL);
						}
						break;
					case 2:
						{
						_localctx = new SqlBinaryExpr_isNotDistinctFromContext(new SqlExpr_primaryContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_sqlExpr_primary);
						setState(805);
						if (!(precpred(_ctx, 10))) throw new FailedPredicateException(this, "precpred(_ctx, 10)");
						setState(806);
						match(IS);
						setState(807);
						match(NOT);
						setState(808);
						match(DISTINCT);
						setState(809);
						match(FROM);
						setState(810);
						sqlExpr_predicate();
						}
						break;
					case 3:
						{
						_localctx = new SqlNotExpr_isDistinctFromContext(new SqlExpr_primaryContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_sqlExpr_primary);
						setState(811);
						if (!(precpred(_ctx, 9))) throw new FailedPredicateException(this, "precpred(_ctx, 9)");
						setState(812);
						match(IS);
						setState(813);
						match(DISTINCT);
						setState(814);
						match(FROM);
						setState(815);
						sqlExpr_predicate();
						}
						break;
					case 4:
						{
						_localctx = new SqlIsNullExpr_isUnknownContext(new SqlExpr_primaryContext(_parentctx, _parentState));
						((SqlIsNullExpr_isUnknownContext)_localctx).expr = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_sqlExpr_primary);
						setState(816);
						if (!(precpred(_ctx, 8))) throw new FailedPredicateException(this, "precpred(_ctx, 8)");
						setState(817);
						match(IS);
						setState(819);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==NOT) {
							{
							setState(818);
							((SqlIsNullExpr_isUnknownContext)_localctx).not = match(NOT);
							}
						}

						setState(821);
						match(UNKNOWN);
						}
						break;
					case 5:
						{
						_localctx = new SqlAndExpr_isTrueContext(new SqlExpr_primaryContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_sqlExpr_primary);
						setState(822);
						if (!(precpred(_ctx, 7))) throw new FailedPredicateException(this, "precpred(_ctx, 7)");
						setState(823);
						match(IS);
						setState(824);
						match(TRUE);
						}
						break;
					case 6:
						{
						_localctx = new SqlNotExpr_isNotTrueContext(new SqlExpr_primaryContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_sqlExpr_primary);
						setState(825);
						if (!(precpred(_ctx, 6))) throw new FailedPredicateException(this, "precpred(_ctx, 6)");
						setState(826);
						match(IS);
						setState(827);
						match(NOT);
						setState(828);
						match(TRUE);
						}
						break;
					case 7:
						{
						_localctx = new SqlAndExpr_isFalseContext(new SqlExpr_primaryContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_sqlExpr_primary);
						setState(829);
						if (!(precpred(_ctx, 5))) throw new FailedPredicateException(this, "precpred(_ctx, 5)");
						setState(830);
						match(IS);
						setState(831);
						match(FALSE);
						}
						break;
					case 8:
						{
						_localctx = new SqlNotExpr_isNotFalseContext(new SqlExpr_primaryContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_sqlExpr_primary);
						setState(832);
						if (!(precpred(_ctx, 4))) throw new FailedPredicateException(this, "precpred(_ctx, 4)");
						setState(833);
						match(IS);
						setState(834);
						match(NOT);
						setState(835);
						match(FALSE);
						}
						break;
					case 9:
						{
						_localctx = new SqlBinaryExpr_compareContext(new SqlExpr_primaryContext(_parentctx, _parentState));
						((SqlBinaryExpr_compareContext)_localctx).left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_sqlExpr_primary);
						setState(836);
						if (!(precpred(_ctx, 3))) throw new FailedPredicateException(this, "precpred(_ctx, 3)");
						setState(837);
						((SqlBinaryExpr_compareContext)_localctx).operator = comparisonOperator_();
						setState(838);
						((SqlBinaryExpr_compareContext)_localctx).right = sqlExpr_predicate();
						}
						break;
					case 10:
						{
						_localctx = new SqlCompareWithQueryExprContext(new SqlExpr_primaryContext(_parentctx, _parentState));
						((SqlCompareWithQueryExprContext)_localctx).expr = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_sqlExpr_primary);
						setState(840);
						if (!(precpred(_ctx, 2))) throw new FailedPredicateException(this, "precpred(_ctx, 2)");
						setState(841);
						((SqlCompareWithQueryExprContext)_localctx).operator = comparisonOperator_();
						setState(842);
						((SqlCompareWithQueryExprContext)_localctx).compareRange = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==ALL || _la==ANY || _la==SOME) ) {
							((SqlCompareWithQueryExprContext)_localctx).compareRange = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(843);
						((SqlCompareWithQueryExprContext)_localctx).query = sqlSubQueryExpr();
						}
						break;
					}
					} 
				}
				setState(849);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,91,_ctx);
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
	public static class ComparisonOperator_Context extends ParserRuleContext {
		public TerminalNode EQ_() { return getToken(EqlParser.EQ_, 0); }
		public TerminalNode GTE_() { return getToken(EqlParser.GTE_, 0); }
		public TerminalNode GT_() { return getToken(EqlParser.GT_, 0); }
		public TerminalNode LTE_() { return getToken(EqlParser.LTE_, 0); }
		public TerminalNode LT_() { return getToken(EqlParser.LT_, 0); }
		public TerminalNode NEQ_() { return getToken(EqlParser.NEQ_, 0); }
		public ComparisonOperator_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_comparisonOperator_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterComparisonOperator_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitComparisonOperator_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitComparisonOperator_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ComparisonOperator_Context comparisonOperator_() throws RecognitionException {
		ComparisonOperator_Context _localctx = new ComparisonOperator_Context(_ctx, getState());
		enterRule(_localctx, 144, RULE_comparisonOperator_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(850);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 132120576L) != 0)) ) {
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
	public static class SqlExpr_predicateContext extends ParserRuleContext {
		public SqlExpr_predicateContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlExpr_predicate; }
	 
		public SqlExpr_predicateContext() { }
		public void copyFrom(SqlExpr_predicateContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlExpr_bit2Context extends SqlExpr_predicateContext {
		public SqlExpr_bitContext sqlExpr_bit() {
			return getRuleContext(SqlExpr_bitContext.class,0);
		}
		public SqlExpr_bit2Context(SqlExpr_predicateContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlExpr_bit2(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlExpr_bit2(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlExpr_bit2(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlInQueryExprContext extends SqlExpr_predicateContext {
		public SqlExpr_bitContext expr;
		public Token not;
		public SqlSubQueryExprContext query;
		public TerminalNode IN() { return getToken(EqlParser.IN, 0); }
		public SqlExpr_bitContext sqlExpr_bit() {
			return getRuleContext(SqlExpr_bitContext.class,0);
		}
		public SqlSubQueryExprContext sqlSubQueryExpr() {
			return getRuleContext(SqlSubQueryExprContext.class,0);
		}
		public TerminalNode NOT() { return getToken(EqlParser.NOT, 0); }
		public SqlInQueryExprContext(SqlExpr_predicateContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlInQueryExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlInQueryExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlInQueryExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlInValuesExprContext extends SqlExpr_predicateContext {
		public SqlExpr_bitContext expr;
		public Token not;
		public SqlInValues_Context values;
		public TerminalNode IN() { return getToken(EqlParser.IN, 0); }
		public TerminalNode LP_() { return getToken(EqlParser.LP_, 0); }
		public TerminalNode RP_() { return getToken(EqlParser.RP_, 0); }
		public SqlExpr_bitContext sqlExpr_bit() {
			return getRuleContext(SqlExpr_bitContext.class,0);
		}
		public SqlInValues_Context sqlInValues_() {
			return getRuleContext(SqlInValues_Context.class,0);
		}
		public TerminalNode NOT() { return getToken(EqlParser.NOT, 0); }
		public SqlInValuesExprContext(SqlExpr_predicateContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlInValuesExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlInValuesExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlInValuesExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlBetweenExprContext extends SqlExpr_predicateContext {
		public SqlExpr_bitContext test;
		public Token not;
		public SqlExpr_bitContext begin;
		public SqlExpr_predicateContext end;
		public TerminalNode BETWEEN() { return getToken(EqlParser.BETWEEN, 0); }
		public TerminalNode AND() { return getToken(EqlParser.AND, 0); }
		public List<SqlExpr_bitContext> sqlExpr_bit() {
			return getRuleContexts(SqlExpr_bitContext.class);
		}
		public SqlExpr_bitContext sqlExpr_bit(int i) {
			return getRuleContext(SqlExpr_bitContext.class,i);
		}
		public SqlExpr_predicateContext sqlExpr_predicate() {
			return getRuleContext(SqlExpr_predicateContext.class,0);
		}
		public TerminalNode NOT() { return getToken(EqlParser.NOT, 0); }
		public SqlBetweenExprContext(SqlExpr_predicateContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlBetweenExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlBetweenExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlBetweenExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlLikeExprContext extends SqlExpr_predicateContext {
		public SqlExpr_bitContext expr;
		public Token not;
		public SqlExpr_simpleContext value;
		public SqlExpr_simpleContext escape;
		public Token ignoreCase;
		public TerminalNode LIKE() { return getToken(EqlParser.LIKE, 0); }
		public SqlExpr_bitContext sqlExpr_bit() {
			return getRuleContext(SqlExpr_bitContext.class,0);
		}
		public List<SqlExpr_simpleContext> sqlExpr_simple() {
			return getRuleContexts(SqlExpr_simpleContext.class);
		}
		public SqlExpr_simpleContext sqlExpr_simple(int i) {
			return getRuleContext(SqlExpr_simpleContext.class,i);
		}
		public TerminalNode ESCAPE() { return getToken(EqlParser.ESCAPE, 0); }
		public TerminalNode NOT() { return getToken(EqlParser.NOT, 0); }
		public TerminalNode ILIKE() { return getToken(EqlParser.ILIKE, 0); }
		public SqlLikeExprContext(SqlExpr_predicateContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlLikeExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlLikeExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlLikeExpr(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlExpr_predicateContext sqlExpr_predicate() throws RecognitionException {
		SqlExpr_predicateContext _localctx = new SqlExpr_predicateContext(_ctx, getState());
		enterRule(_localctx, 146, RULE_sqlExpr_predicate);
		int _la;
		try {
			setState(898);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,99,_ctx) ) {
			case 1:
				_localctx = new SqlInQueryExprContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(852);
				((SqlInQueryExprContext)_localctx).expr = sqlExpr_bit(0);
				setState(854);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(853);
					((SqlInQueryExprContext)_localctx).not = match(NOT);
					}
				}

				setState(856);
				match(IN);
				setState(857);
				((SqlInQueryExprContext)_localctx).query = sqlSubQueryExpr();
				}
				break;
			case 2:
				_localctx = new SqlInValuesExprContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(859);
				((SqlInValuesExprContext)_localctx).expr = sqlExpr_bit(0);
				setState(861);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(860);
					((SqlInValuesExprContext)_localctx).not = match(NOT);
					}
				}

				setState(863);
				match(IN);
				setState(864);
				match(LP_);
				setState(865);
				((SqlInValuesExprContext)_localctx).values = sqlInValues_();
				setState(866);
				match(RP_);
				}
				break;
			case 3:
				_localctx = new SqlBetweenExprContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(868);
				((SqlBetweenExprContext)_localctx).test = sqlExpr_bit(0);
				setState(870);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(869);
					((SqlBetweenExprContext)_localctx).not = match(NOT);
					}
				}

				setState(872);
				match(BETWEEN);
				setState(873);
				((SqlBetweenExprContext)_localctx).begin = sqlExpr_bit(0);
				setState(874);
				match(AND);
				setState(875);
				((SqlBetweenExprContext)_localctx).end = sqlExpr_predicate();
				}
				break;
			case 4:
				_localctx = new SqlLikeExprContext(_localctx);
				enterOuterAlt(_localctx, 4);
				{
				setState(877);
				((SqlLikeExprContext)_localctx).expr = sqlExpr_bit(0);
				setState(879);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(878);
					((SqlLikeExprContext)_localctx).not = match(NOT);
					}
				}

				setState(881);
				match(LIKE);
				setState(882);
				((SqlLikeExprContext)_localctx).value = sqlExpr_simple();
				setState(885);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,96,_ctx) ) {
				case 1:
					{
					setState(883);
					match(ESCAPE);
					setState(884);
					((SqlLikeExprContext)_localctx).escape = sqlExpr_simple();
					}
					break;
				}
				}
				break;
			case 5:
				_localctx = new SqlLikeExprContext(_localctx);
				enterOuterAlt(_localctx, 5);
				{
				setState(887);
				((SqlLikeExprContext)_localctx).expr = sqlExpr_bit(0);
				setState(889);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NOT) {
					{
					setState(888);
					((SqlLikeExprContext)_localctx).not = match(NOT);
					}
				}

				setState(891);
				((SqlLikeExprContext)_localctx).ignoreCase = match(ILIKE);
				setState(892);
				((SqlLikeExprContext)_localctx).value = sqlExpr_simple();
				setState(895);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,98,_ctx) ) {
				case 1:
					{
					setState(893);
					match(ESCAPE);
					setState(894);
					((SqlLikeExprContext)_localctx).escape = sqlExpr_simple();
					}
					break;
				}
				}
				break;
			case 6:
				_localctx = new SqlExpr_bit2Context(_localctx);
				enterOuterAlt(_localctx, 6);
				{
				setState(897);
				sqlExpr_bit(0);
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
	public static class SqlInValues_Context extends ParserRuleContext {
		public SqlExprContext e;
		public List<SqlExprContext> sqlExpr() {
			return getRuleContexts(SqlExprContext.class);
		}
		public SqlExprContext sqlExpr(int i) {
			return getRuleContext(SqlExprContext.class,i);
		}
		public List<TerminalNode> COMMA_() { return getTokens(EqlParser.COMMA_); }
		public TerminalNode COMMA_(int i) {
			return getToken(EqlParser.COMMA_, i);
		}
		public SqlInValues_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlInValues_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlInValues_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlInValues_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlInValues_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlInValues_Context sqlInValues_() throws RecognitionException {
		SqlInValues_Context _localctx = new SqlInValues_Context(_ctx, getState());
		enterRule(_localctx, 148, RULE_sqlInValues_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(900);
			((SqlInValues_Context)_localctx).e = sqlExpr(0);
			setState(905);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA_) {
				{
				{
				setState(901);
				match(COMMA_);
				setState(902);
				((SqlInValues_Context)_localctx).e = sqlExpr(0);
				}
				}
				setState(907);
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
	public static class SqlExpr_bitContext extends ParserRuleContext {
		public SqlExpr_bitContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlExpr_bit; }
	 
		public SqlExpr_bitContext() { }
		public void copyFrom(SqlExpr_bitContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlRegularFunction_atTimeZoneContext extends SqlExpr_bitContext {
		public List<SqlExpr_simpleContext> sqlExpr_simple() {
			return getRuleContexts(SqlExpr_simpleContext.class);
		}
		public SqlExpr_simpleContext sqlExpr_simple(int i) {
			return getRuleContext(SqlExpr_simpleContext.class,i);
		}
		public TerminalNode AT() { return getToken(EqlParser.AT, 0); }
		public TerminalNode TIME() { return getToken(EqlParser.TIME, 0); }
		public TerminalNode ZONE() { return getToken(EqlParser.ZONE, 0); }
		public SqlRegularFunction_atTimeZoneContext(SqlExpr_bitContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlRegularFunction_atTimeZone(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlRegularFunction_atTimeZone(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlRegularFunction_atTimeZone(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlBinaryExprContext extends SqlExpr_bitContext {
		public SqlExpr_bitContext left;
		public Token operator;
		public SqlExpr_bitContext right;
		public List<SqlExpr_bitContext> sqlExpr_bit() {
			return getRuleContexts(SqlExpr_bitContext.class);
		}
		public SqlExpr_bitContext sqlExpr_bit(int i) {
			return getRuleContext(SqlExpr_bitContext.class,i);
		}
		public TerminalNode CARET_() { return getToken(EqlParser.CARET_, 0); }
		public TerminalNode ASTERISK_() { return getToken(EqlParser.ASTERISK_, 0); }
		public TerminalNode SLASH_() { return getToken(EqlParser.SLASH_, 0); }
		public TerminalNode MOD_() { return getToken(EqlParser.MOD_, 0); }
		public TerminalNode PLUS_() { return getToken(EqlParser.PLUS_, 0); }
		public TerminalNode MINUS_() { return getToken(EqlParser.MINUS_, 0); }
		public TerminalNode SIGNED_LEFT_SHIFT_() { return getToken(EqlParser.SIGNED_LEFT_SHIFT_, 0); }
		public TerminalNode SIGNED_RIGHT_SHIFT_() { return getToken(EqlParser.SIGNED_RIGHT_SHIFT_, 0); }
		public TerminalNode AMPERSAND_() { return getToken(EqlParser.AMPERSAND_, 0); }
		public TerminalNode VERTICAL_BAR_() { return getToken(EqlParser.VERTICAL_BAR_, 0); }
		public SqlBinaryExprContext(SqlExpr_bitContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlBinaryExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlBinaryExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlBinaryExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlExpr_simple2Context extends SqlExpr_bitContext {
		public SqlExpr_simpleContext sqlExpr_simple() {
			return getRuleContext(SqlExpr_simpleContext.class,0);
		}
		public SqlExpr_simple2Context(SqlExpr_bitContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlExpr_simple2(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlExpr_simple2(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlExpr_simple2(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlExpr_bitContext sqlExpr_bit() throws RecognitionException {
		return sqlExpr_bit(0);
	}

	private SqlExpr_bitContext sqlExpr_bit(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		SqlExpr_bitContext _localctx = new SqlExpr_bitContext(_ctx, _parentState);
		SqlExpr_bitContext _prevctx = _localctx;
		int _startState = 150;
		enterRecursionRule(_localctx, 150, RULE_sqlExpr_bit, _p);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(916);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,101,_ctx) ) {
			case 1:
				{
				_localctx = new SqlRegularFunction_atTimeZoneContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;

				setState(909);
				sqlExpr_simple();
				setState(910);
				match(AT);
				setState(911);
				match(TIME);
				setState(912);
				match(ZONE);
				setState(913);
				sqlExpr_simple();
				}
				break;
			case 2:
				{
				_localctx = new SqlExpr_simple2Context(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(915);
				sqlExpr_simple();
				}
				break;
			}
			_ctx.stop = _input.LT(-1);
			setState(938);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,103,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(936);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,102,_ctx) ) {
					case 1:
						{
						_localctx = new SqlBinaryExprContext(new SqlExpr_bitContext(_parentctx, _parentState));
						((SqlBinaryExprContext)_localctx).left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_sqlExpr_bit);
						setState(918);
						if (!(precpred(_ctx, 8))) throw new FailedPredicateException(this, "precpred(_ctx, 8)");
						setState(919);
						((SqlBinaryExprContext)_localctx).operator = match(CARET_);
						setState(920);
						((SqlBinaryExprContext)_localctx).right = sqlExpr_bit(9);
						}
						break;
					case 2:
						{
						_localctx = new SqlBinaryExprContext(new SqlExpr_bitContext(_parentctx, _parentState));
						((SqlBinaryExprContext)_localctx).left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_sqlExpr_bit);
						setState(921);
						if (!(precpred(_ctx, 7))) throw new FailedPredicateException(this, "precpred(_ctx, 7)");
						setState(922);
						((SqlBinaryExprContext)_localctx).operator = _input.LT(1);
						_la = _input.LA(1);
						if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 50176L) != 0)) ) {
							((SqlBinaryExprContext)_localctx).operator = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(923);
						((SqlBinaryExprContext)_localctx).right = sqlExpr_bit(8);
						}
						break;
					case 3:
						{
						_localctx = new SqlBinaryExprContext(new SqlExpr_bitContext(_parentctx, _parentState));
						((SqlBinaryExprContext)_localctx).left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_sqlExpr_bit);
						setState(924);
						if (!(precpred(_ctx, 6))) throw new FailedPredicateException(this, "precpred(_ctx, 6)");
						setState(925);
						((SqlBinaryExprContext)_localctx).operator = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==PLUS_ || _la==MINUS_) ) {
							((SqlBinaryExprContext)_localctx).operator = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(926);
						((SqlBinaryExprContext)_localctx).right = sqlExpr_bit(7);
						}
						break;
					case 4:
						{
						_localctx = new SqlBinaryExprContext(new SqlExpr_bitContext(_parentctx, _parentState));
						((SqlBinaryExprContext)_localctx).left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_sqlExpr_bit);
						setState(927);
						if (!(precpred(_ctx, 5))) throw new FailedPredicateException(this, "precpred(_ctx, 5)");
						setState(928);
						((SqlBinaryExprContext)_localctx).operator = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==SIGNED_LEFT_SHIFT_ || _la==SIGNED_RIGHT_SHIFT_) ) {
							((SqlBinaryExprContext)_localctx).operator = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(929);
						((SqlBinaryExprContext)_localctx).right = sqlExpr_bit(6);
						}
						break;
					case 5:
						{
						_localctx = new SqlBinaryExprContext(new SqlExpr_bitContext(_parentctx, _parentState));
						((SqlBinaryExprContext)_localctx).left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_sqlExpr_bit);
						setState(930);
						if (!(precpred(_ctx, 4))) throw new FailedPredicateException(this, "precpred(_ctx, 4)");
						setState(931);
						((SqlBinaryExprContext)_localctx).operator = match(AMPERSAND_);
						setState(932);
						((SqlBinaryExprContext)_localctx).right = sqlExpr_bit(5);
						}
						break;
					case 6:
						{
						_localctx = new SqlBinaryExprContext(new SqlExpr_bitContext(_parentctx, _parentState));
						((SqlBinaryExprContext)_localctx).left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_sqlExpr_bit);
						setState(933);
						if (!(precpred(_ctx, 3))) throw new FailedPredicateException(this, "precpred(_ctx, 3)");
						setState(934);
						((SqlBinaryExprContext)_localctx).operator = match(VERTICAL_BAR_);
						setState(935);
						((SqlBinaryExprContext)_localctx).right = sqlExpr_bit(4);
						}
						break;
					}
					} 
				}
				setState(940);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,103,_ctx);
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
	public static class SqlExpr_simpleContext extends ParserRuleContext {
		public SqlExpr_functionCallContext sqlExpr_functionCall() {
			return getRuleContext(SqlExpr_functionCallContext.class,0);
		}
		public SqlWindowExprContext sqlWindowExpr() {
			return getRuleContext(SqlWindowExprContext.class,0);
		}
		public SqlParameterMarkerContext sqlParameterMarker() {
			return getRuleContext(SqlParameterMarkerContext.class,0);
		}
		public SqlLiteralContext sqlLiteral() {
			return getRuleContext(SqlLiteralContext.class,0);
		}
		public SqlColumnNameContext sqlColumnName() {
			return getRuleContext(SqlColumnNameContext.class,0);
		}
		public SqlSubQueryExprContext sqlSubQueryExpr() {
			return getRuleContext(SqlSubQueryExprContext.class,0);
		}
		public SqlUnaryExprContext sqlUnaryExpr() {
			return getRuleContext(SqlUnaryExprContext.class,0);
		}
		public SqlExpr_braceContext sqlExpr_brace() {
			return getRuleContext(SqlExpr_braceContext.class,0);
		}
		public SqlMultiValueExprContext sqlMultiValueExpr() {
			return getRuleContext(SqlMultiValueExprContext.class,0);
		}
		public SqlExistsExprContext sqlExistsExpr() {
			return getRuleContext(SqlExistsExprContext.class,0);
		}
		public SqlCaseExprContext sqlCaseExpr() {
			return getRuleContext(SqlCaseExprContext.class,0);
		}
		public SqlIntervalExprContext sqlIntervalExpr() {
			return getRuleContext(SqlIntervalExprContext.class,0);
		}
		public SqlExpr_simpleContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlExpr_simple; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlExpr_simple(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlExpr_simple(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlExpr_simple(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlExpr_simpleContext sqlExpr_simple() throws RecognitionException {
		SqlExpr_simpleContext _localctx = new SqlExpr_simpleContext(_ctx, getState());
		enterRule(_localctx, 152, RULE_sqlExpr_simple);
		try {
			setState(953);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,104,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(941);
				sqlExpr_functionCall();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(942);
				sqlWindowExpr();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(943);
				sqlParameterMarker();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(944);
				sqlLiteral();
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(945);
				sqlColumnName();
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(946);
				sqlSubQueryExpr();
				}
				break;
			case 7:
				enterOuterAlt(_localctx, 7);
				{
				setState(947);
				sqlUnaryExpr();
				}
				break;
			case 8:
				enterOuterAlt(_localctx, 8);
				{
				setState(948);
				sqlExpr_brace();
				}
				break;
			case 9:
				enterOuterAlt(_localctx, 9);
				{
				setState(949);
				sqlMultiValueExpr();
				}
				break;
			case 10:
				enterOuterAlt(_localctx, 10);
				{
				setState(950);
				sqlExistsExpr();
				}
				break;
			case 11:
				enterOuterAlt(_localctx, 11);
				{
				setState(951);
				sqlCaseExpr();
				}
				break;
			case 12:
				enterOuterAlt(_localctx, 12);
				{
				setState(952);
				sqlIntervalExpr();
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
	public static class SqlUnaryExprContext extends ParserRuleContext {
		public Token operator;
		public SqlExpr_simpleContext expr;
		public SqlExpr_simpleContext sqlExpr_simple() {
			return getRuleContext(SqlExpr_simpleContext.class,0);
		}
		public TerminalNode PLUS_() { return getToken(EqlParser.PLUS_, 0); }
		public TerminalNode MINUS_() { return getToken(EqlParser.MINUS_, 0); }
		public TerminalNode TILDE_() { return getToken(EqlParser.TILDE_, 0); }
		public TerminalNode NOT_() { return getToken(EqlParser.NOT_, 0); }
		public SqlUnaryExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlUnaryExpr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlUnaryExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlUnaryExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlUnaryExpr(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlUnaryExprContext sqlUnaryExpr() throws RecognitionException {
		SqlUnaryExprContext _localctx = new SqlUnaryExprContext(_ctx, getState());
		enterRule(_localctx, 154, RULE_sqlUnaryExpr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(955);
			((SqlUnaryExprContext)_localctx).operator = _input.LT(1);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 12312L) != 0)) ) {
				((SqlUnaryExprContext)_localctx).operator = (Token)_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(956);
			((SqlUnaryExprContext)_localctx).expr = sqlExpr_simple();
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
	public static class SqlExpr_braceContext extends ParserRuleContext {
		public TerminalNode LP_() { return getToken(EqlParser.LP_, 0); }
		public SqlExprContext sqlExpr() {
			return getRuleContext(SqlExprContext.class,0);
		}
		public TerminalNode RP_() { return getToken(EqlParser.RP_, 0); }
		public SqlExpr_braceContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlExpr_brace; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlExpr_brace(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlExpr_brace(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlExpr_brace(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlExpr_braceContext sqlExpr_brace() throws RecognitionException {
		SqlExpr_braceContext _localctx = new SqlExpr_braceContext(_ctx, getState());
		enterRule(_localctx, 156, RULE_sqlExpr_brace);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(958);
			match(LP_);
			setState(959);
			sqlExpr(0);
			setState(960);
			match(RP_);
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
	public static class SqlMultiValueExprContext extends ParserRuleContext {
		public SqlInValues_Context values;
		public TerminalNode LP_() { return getToken(EqlParser.LP_, 0); }
		public TerminalNode RP_() { return getToken(EqlParser.RP_, 0); }
		public SqlInValues_Context sqlInValues_() {
			return getRuleContext(SqlInValues_Context.class,0);
		}
		public SqlMultiValueExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlMultiValueExpr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlMultiValueExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlMultiValueExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlMultiValueExpr(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlMultiValueExprContext sqlMultiValueExpr() throws RecognitionException {
		SqlMultiValueExprContext _localctx = new SqlMultiValueExprContext(_ctx, getState());
		enterRule(_localctx, 158, RULE_sqlMultiValueExpr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(962);
			match(LP_);
			setState(963);
			((SqlMultiValueExprContext)_localctx).values = sqlInValues_();
			setState(964);
			match(RP_);
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
	public static class SqlExistsExprContext extends ParserRuleContext {
		public SqlSubQueryExprContext query;
		public TerminalNode EXISTS() { return getToken(EqlParser.EXISTS, 0); }
		public SqlSubQueryExprContext sqlSubQueryExpr() {
			return getRuleContext(SqlSubQueryExprContext.class,0);
		}
		public SqlExistsExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlExistsExpr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlExistsExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlExistsExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlExistsExpr(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlExistsExprContext sqlExistsExpr() throws RecognitionException {
		SqlExistsExprContext _localctx = new SqlExistsExprContext(_ctx, getState());
		enterRule(_localctx, 160, RULE_sqlExistsExpr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(966);
			match(EXISTS);
			setState(967);
			((SqlExistsExprContext)_localctx).query = sqlSubQueryExpr();
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
	public static class SqlExpr_functionCallContext extends ParserRuleContext {
		public SqlAggregateFunctionContext sqlAggregateFunction() {
			return getRuleContext(SqlAggregateFunctionContext.class,0);
		}
		public SqlExpr_specialContext sqlExpr_special() {
			return getRuleContext(SqlExpr_specialContext.class,0);
		}
		public SqlRegularFunctionContext sqlRegularFunction() {
			return getRuleContext(SqlRegularFunctionContext.class,0);
		}
		public SqlExpr_functionCallContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlExpr_functionCall; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlExpr_functionCall(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlExpr_functionCall(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlExpr_functionCall(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlExpr_functionCallContext sqlExpr_functionCall() throws RecognitionException {
		SqlExpr_functionCallContext _localctx = new SqlExpr_functionCallContext(_ctx, getState());
		enterRule(_localctx, 162, RULE_sqlExpr_functionCall);
		try {
			setState(972);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,105,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(969);
				sqlAggregateFunction();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(970);
				sqlExpr_special();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(971);
				sqlRegularFunction();
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
	public static class SqlAggregateFunctionContext extends ParserRuleContext {
		public SqlIdentifier_agg_Context name;
		public Distinct_Context distinct;
		public FunctionArgs_Context args;
		public Token selectAll;
		public TerminalNode LP_() { return getToken(EqlParser.LP_, 0); }
		public TerminalNode RP_() { return getToken(EqlParser.RP_, 0); }
		public SqlIdentifier_agg_Context sqlIdentifier_agg_() {
			return getRuleContext(SqlIdentifier_agg_Context.class,0);
		}
		public Distinct_Context distinct_() {
			return getRuleContext(Distinct_Context.class,0);
		}
		public FunctionArgs_Context functionArgs_() {
			return getRuleContext(FunctionArgs_Context.class,0);
		}
		public TerminalNode ASTERISK_() { return getToken(EqlParser.ASTERISK_, 0); }
		public SqlAggregateFunctionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlAggregateFunction; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlAggregateFunction(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlAggregateFunction(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlAggregateFunction(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlAggregateFunctionContext sqlAggregateFunction() throws RecognitionException {
		SqlAggregateFunctionContext _localctx = new SqlAggregateFunctionContext(_ctx, getState());
		enterRule(_localctx, 164, RULE_sqlAggregateFunction);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(974);
			((SqlAggregateFunctionContext)_localctx).name = sqlIdentifier_agg_();
			setState(975);
			match(LP_);
			setState(977);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==DISTINCT) {
				{
				setState(976);
				((SqlAggregateFunctionContext)_localctx).distinct = distinct_();
				}
			}

			setState(981);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case NOT_:
			case TILDE_:
			case PLUS_:
			case MINUS_:
			case LP_:
			case QUESTION_:
			case POSITION:
			case CASE:
			case CAST:
			case TRIM:
			case SUBSTRING:
			case LATERAL:
			case CROSS:
			case IF:
			case NOT:
			case NULL:
			case TRUE:
			case FALSE:
			case EXISTS:
			case ORDER:
			case GROUP:
			case BEGIN:
			case INTERVAL:
			case DATE:
			case TIME:
			case TIMESTAMP:
			case LOCALTIME:
			case LOCALTIMESTAMP:
			case YEAR:
			case QUARTER:
			case MONTH:
			case DAY:
			case HOUR:
			case MINUTE:
			case SECOND:
			case MAX:
			case MIN:
			case SUM:
			case COUNT:
			case AVG:
			case CURRENT_USER:
			case NAME:
			case TYPE:
			case RECURSIVE:
			case UPSERT:
			case CONFLICT:
			case CATALOG_NAME:
			case CHARACTER_SET_CATALOG:
			case CHARACTER_SET_NAME:
			case CHARACTER_SET_SCHEMA:
			case CLASS_ORIGIN:
			case COBOL:
			case COLLATION_CATALOG:
			case COLLATION_NAME:
			case COLLATION_SCHEMA:
			case COLUMN_NAME:
			case COMMAND_FUNCTION:
			case COMMITTED:
			case CONDITION_NUMBER:
			case CONNECTION_NAME:
			case CONSTRAINT_CATALOG:
			case CONSTRAINT_NAME:
			case CONSTRAINT_SCHEMA:
			case CURSOR_NAME:
			case DATA:
			case DATETIME_INTERVAL_CODE:
			case DATETIME_INTERVAL_PRECISION:
			case DYNAMIC_FUNCTION:
			case FORTRAN:
			case LENGTH:
			case MESSAGE_LENGTH:
			case MESSAGE_OCTET_LENGTH:
			case MESSAGE_TEXT:
			case MORE92:
			case MUMPS:
			case NULLABLE:
			case NUMBER:
			case PASCAL:
			case PLI:
			case REPEATABLE:
			case RETURNED_LENGTH:
			case RETURNED_OCTET_LENGTH:
			case RETURNED_SQLSTATE:
			case ROW_COUNT:
			case SCALE:
			case SCHEMA_NAME:
			case SERIALIZABLE:
			case SERVER_NAME:
			case SUBCLASS_ORIGIN:
			case TABLE_NAME:
			case UNCOMMITTED:
			case UNNAMED:
			case BIT_LENGTH:
			case COALESCE:
			case CURRENT_DATE:
			case CURRENT_TIMESTAMP:
			case END:
			case EXTRACT:
			case FOLLOWING:
			case GROUPS:
			case INDICATOR:
			case INSENSITIVE:
			case LANGUAGE:
			case NULLS:
			case LEVEL:
			case LOWER:
			case NULLIF:
			case OCTET_LENGTH:
			case PRECEDING:
			case RANGE:
			case ROW:
			case SECTION:
			case SESSION:
			case TUMBLE:
			case HOP:
			case UNBOUNDED:
			case UPPER:
			case USER:
			case VALUE:
			case WINDOW:
			case WORK:
			case ZONE:
			case ROLLUP:
			case CUBE:
			case NOWAIT:
			case SKIP_:
			case LOCKED:
			case IDENTIFIER_:
			case STRING_:
			case NUMBER_:
			case HEX_DIGIT_:
			case BIT_NUM_:
				{
				setState(979);
				((SqlAggregateFunctionContext)_localctx).args = functionArgs_();
				}
				break;
			case ASTERISK_:
				{
				setState(980);
				((SqlAggregateFunctionContext)_localctx).selectAll = match(ASTERISK_);
				}
				break;
			case RP_:
				break;
			default:
				break;
			}
			setState(983);
			match(RP_);
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
	public static class SqlWindowExprContext extends ParserRuleContext {
		public SqlWindowFunction_Context function;
		public SqlPartitionByContext partitionBy;
		public SqlOrderByContext orderBy;
		public SqlWindowFrameContext frame;
		public SqlIdentifier_Context windowName;
		public TerminalNode OVER() { return getToken(EqlParser.OVER, 0); }
		public SqlWindowFunction_Context sqlWindowFunction_() {
			return getRuleContext(SqlWindowFunction_Context.class,0);
		}
		public TerminalNode LP_() { return getToken(EqlParser.LP_, 0); }
		public TerminalNode RP_() { return getToken(EqlParser.RP_, 0); }
		public SqlIdentifier_Context sqlIdentifier_() {
			return getRuleContext(SqlIdentifier_Context.class,0);
		}
		public SqlPartitionByContext sqlPartitionBy() {
			return getRuleContext(SqlPartitionByContext.class,0);
		}
		public SqlOrderByContext sqlOrderBy() {
			return getRuleContext(SqlOrderByContext.class,0);
		}
		public SqlWindowFrameContext sqlWindowFrame() {
			return getRuleContext(SqlWindowFrameContext.class,0);
		}
		public SqlWindowExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlWindowExpr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlWindowExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlWindowExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlWindowExpr(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlWindowExprContext sqlWindowExpr() throws RecognitionException {
		SqlWindowExprContext _localctx = new SqlWindowExprContext(_ctx, getState());
		enterRule(_localctx, 166, RULE_sqlWindowExpr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(985);
			((SqlWindowExprContext)_localctx).function = sqlWindowFunction_();
			setState(986);
			match(OVER);
			setState(999);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case LP_:
				{
				setState(987);
				match(LP_);
				setState(989);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==PARTITION) {
					{
					setState(988);
					((SqlWindowExprContext)_localctx).partitionBy = sqlPartitionBy();
					}
				}

				setState(992);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==ORDER) {
					{
					setState(991);
					((SqlWindowExprContext)_localctx).orderBy = sqlOrderBy();
					}
				}

				setState(995);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (((((_la - 280)) & ~0x3f) == 0 && ((1L << (_la - 280)) & 2233382993921L) != 0)) {
					{
					setState(994);
					((SqlWindowExprContext)_localctx).frame = sqlWindowFrame();
					}
				}

				setState(997);
				match(RP_);
				}
				break;
			case POSITION:
			case LATERAL:
			case CROSS:
			case ORDER:
			case GROUP:
			case BEGIN:
			case DATE:
			case TIMESTAMP:
			case YEAR:
			case QUARTER:
			case MONTH:
			case DAY:
			case HOUR:
			case MINUTE:
			case SECOND:
			case COUNT:
			case CURRENT_USER:
			case NAME:
			case TYPE:
			case RECURSIVE:
			case UPSERT:
			case CONFLICT:
			case CATALOG_NAME:
			case CHARACTER_SET_CATALOG:
			case CHARACTER_SET_NAME:
			case CHARACTER_SET_SCHEMA:
			case CLASS_ORIGIN:
			case COBOL:
			case COLLATION_CATALOG:
			case COLLATION_NAME:
			case COLLATION_SCHEMA:
			case COLUMN_NAME:
			case COMMAND_FUNCTION:
			case COMMITTED:
			case CONDITION_NUMBER:
			case CONNECTION_NAME:
			case CONSTRAINT_CATALOG:
			case CONSTRAINT_NAME:
			case CONSTRAINT_SCHEMA:
			case CURSOR_NAME:
			case DATA:
			case DATETIME_INTERVAL_CODE:
			case DATETIME_INTERVAL_PRECISION:
			case DYNAMIC_FUNCTION:
			case FORTRAN:
			case LENGTH:
			case MESSAGE_LENGTH:
			case MESSAGE_OCTET_LENGTH:
			case MESSAGE_TEXT:
			case MORE92:
			case MUMPS:
			case NULLABLE:
			case NUMBER:
			case PASCAL:
			case PLI:
			case REPEATABLE:
			case RETURNED_LENGTH:
			case RETURNED_OCTET_LENGTH:
			case RETURNED_SQLSTATE:
			case ROW_COUNT:
			case SCALE:
			case SCHEMA_NAME:
			case SERIALIZABLE:
			case SERVER_NAME:
			case SUBCLASS_ORIGIN:
			case TABLE_NAME:
			case UNCOMMITTED:
			case UNNAMED:
			case BIT_LENGTH:
			case COALESCE:
			case CURRENT_DATE:
			case END:
			case FOLLOWING:
			case GROUPS:
			case INDICATOR:
			case INSENSITIVE:
			case LANGUAGE:
			case NULLS:
			case LEVEL:
			case LOWER:
			case OCTET_LENGTH:
			case PRECEDING:
			case RANGE:
			case ROW:
			case SECTION:
			case SESSION:
			case TUMBLE:
			case HOP:
			case UNBOUNDED:
			case UPPER:
			case USER:
			case VALUE:
			case WINDOW:
			case WORK:
			case ZONE:
			case ROLLUP:
			case CUBE:
			case NOWAIT:
			case SKIP_:
			case LOCKED:
			case IDENTIFIER_:
				{
				setState(998);
				((SqlWindowExprContext)_localctx).windowName = sqlIdentifier_();
				}
				break;
			default:
				throw new NoViableAltException(this);
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
	public static class SqlWindowFunction_Context extends ParserRuleContext {
		public SqlAggregateFunctionContext sqlAggregateFunction() {
			return getRuleContext(SqlAggregateFunctionContext.class,0);
		}
		public SqlRegularFunctionContext sqlRegularFunction() {
			return getRuleContext(SqlRegularFunctionContext.class,0);
		}
		public SqlWindowFunction_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlWindowFunction_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlWindowFunction_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlWindowFunction_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlWindowFunction_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlWindowFunction_Context sqlWindowFunction_() throws RecognitionException {
		SqlWindowFunction_Context _localctx = new SqlWindowFunction_Context(_ctx, getState());
		enterRule(_localctx, 168, RULE_sqlWindowFunction_);
		try {
			setState(1003);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,112,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1001);
				sqlAggregateFunction();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(1002);
				sqlRegularFunction();
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
	public static class SqlPartitionByContext extends ParserRuleContext {
		public SqlPartitionByItems_Context items;
		public TerminalNode PARTITION() { return getToken(EqlParser.PARTITION, 0); }
		public TerminalNode BY() { return getToken(EqlParser.BY, 0); }
		public SqlPartitionByItems_Context sqlPartitionByItems_() {
			return getRuleContext(SqlPartitionByItems_Context.class,0);
		}
		public SqlPartitionByContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlPartitionBy; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlPartitionBy(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlPartitionBy(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlPartitionBy(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlPartitionByContext sqlPartitionBy() throws RecognitionException {
		SqlPartitionByContext _localctx = new SqlPartitionByContext(_ctx, getState());
		enterRule(_localctx, 170, RULE_sqlPartitionBy);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1005);
			match(PARTITION);
			setState(1006);
			match(BY);
			setState(1007);
			((SqlPartitionByContext)_localctx).items = sqlPartitionByItems_();
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
	public static class SqlPartitionByItems_Context extends ParserRuleContext {
		public SqlExprContext e;
		public List<SqlExprContext> sqlExpr() {
			return getRuleContexts(SqlExprContext.class);
		}
		public SqlExprContext sqlExpr(int i) {
			return getRuleContext(SqlExprContext.class,i);
		}
		public List<TerminalNode> COMMA_() { return getTokens(EqlParser.COMMA_); }
		public TerminalNode COMMA_(int i) {
			return getToken(EqlParser.COMMA_, i);
		}
		public SqlPartitionByItems_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlPartitionByItems_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlPartitionByItems_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlPartitionByItems_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlPartitionByItems_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlPartitionByItems_Context sqlPartitionByItems_() throws RecognitionException {
		SqlPartitionByItems_Context _localctx = new SqlPartitionByItems_Context(_ctx, getState());
		enterRule(_localctx, 172, RULE_sqlPartitionByItems_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1009);
			((SqlPartitionByItems_Context)_localctx).e = sqlExpr(0);
			setState(1014);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA_) {
				{
				{
				setState(1010);
				match(COMMA_);
				setState(1011);
				((SqlPartitionByItems_Context)_localctx).e = sqlExpr(0);
				}
				}
				setState(1016);
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
	public static class SqlWindowFrameContext extends ParserRuleContext {
		public SqlWindowFrameUnit_Context unit;
		public SqlWindowFrameBoundContext start;
		public SqlWindowFrameBoundContext end;
		public SqlWindowFrameUnit_Context sqlWindowFrameUnit_() {
			return getRuleContext(SqlWindowFrameUnit_Context.class,0);
		}
		public List<SqlWindowFrameBoundContext> sqlWindowFrameBound() {
			return getRuleContexts(SqlWindowFrameBoundContext.class);
		}
		public SqlWindowFrameBoundContext sqlWindowFrameBound(int i) {
			return getRuleContext(SqlWindowFrameBoundContext.class,i);
		}
		public TerminalNode BETWEEN() { return getToken(EqlParser.BETWEEN, 0); }
		public TerminalNode AND() { return getToken(EqlParser.AND, 0); }
		public SqlWindowFrameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlWindowFrame; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlWindowFrame(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlWindowFrame(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlWindowFrame(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlWindowFrameContext sqlWindowFrame() throws RecognitionException {
		SqlWindowFrameContext _localctx = new SqlWindowFrameContext(_ctx, getState());
		enterRule(_localctx, 174, RULE_sqlWindowFrame);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1017);
			((SqlWindowFrameContext)_localctx).unit = sqlWindowFrameUnit_();
			setState(1019);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==BETWEEN) {
				{
				setState(1018);
				match(BETWEEN);
				}
			}

			setState(1021);
			((SqlWindowFrameContext)_localctx).start = sqlWindowFrameBound();
			setState(1024);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==AND) {
				{
				setState(1022);
				match(AND);
				setState(1023);
				((SqlWindowFrameContext)_localctx).end = sqlWindowFrameBound();
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
	public static class SqlWindowFrameUnit_Context extends ParserRuleContext {
		public TerminalNode ROWS() { return getToken(EqlParser.ROWS, 0); }
		public TerminalNode RANGE() { return getToken(EqlParser.RANGE, 0); }
		public TerminalNode GROUPS() { return getToken(EqlParser.GROUPS, 0); }
		public SqlWindowFrameUnit_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlWindowFrameUnit_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlWindowFrameUnit_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlWindowFrameUnit_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlWindowFrameUnit_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlWindowFrameUnit_Context sqlWindowFrameUnit_() throws RecognitionException {
		SqlWindowFrameUnit_Context _localctx = new SqlWindowFrameUnit_Context(_ctx, getState());
		enterRule(_localctx, 176, RULE_sqlWindowFrameUnit_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1026);
			_la = _input.LA(1);
			if ( !(((((_la - 280)) & ~0x3f) == 0 && ((1L << (_la - 280)) & 2233382993921L) != 0)) ) {
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
	public static class SqlWindowFrameBoundContext extends ParserRuleContext {
		public SqlExprContext offset;
		public SqlWindowFrameBoundType_Context boundType;
		public SqlWindowFrameBoundType_Context sqlWindowFrameBoundType_() {
			return getRuleContext(SqlWindowFrameBoundType_Context.class,0);
		}
		public SqlExprContext sqlExpr() {
			return getRuleContext(SqlExprContext.class,0);
		}
		public SqlWindowFrameBoundContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlWindowFrameBound; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlWindowFrameBound(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlWindowFrameBound(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlWindowFrameBound(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlWindowFrameBoundContext sqlWindowFrameBound() throws RecognitionException {
		SqlWindowFrameBoundContext _localctx = new SqlWindowFrameBoundContext(_ctx, getState());
		enterRule(_localctx, 178, RULE_sqlWindowFrameBound);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1029);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,116,_ctx) ) {
			case 1:
				{
				setState(1028);
				((SqlWindowFrameBoundContext)_localctx).offset = sqlExpr(0);
				}
				break;
			}
			setState(1031);
			((SqlWindowFrameBoundContext)_localctx).boundType = sqlWindowFrameBoundType_();
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
	public static class SqlWindowFrameBoundType_Context extends ParserRuleContext {
		public TerminalNode UNBOUNDED() { return getToken(EqlParser.UNBOUNDED, 0); }
		public TerminalNode PRECEDING() { return getToken(EqlParser.PRECEDING, 0); }
		public TerminalNode CURRENT() { return getToken(EqlParser.CURRENT, 0); }
		public TerminalNode ROW() { return getToken(EqlParser.ROW, 0); }
		public TerminalNode FOLLOWING() { return getToken(EqlParser.FOLLOWING, 0); }
		public SqlWindowFrameBoundType_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlWindowFrameBoundType_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlWindowFrameBoundType_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlWindowFrameBoundType_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlWindowFrameBoundType_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlWindowFrameBoundType_Context sqlWindowFrameBoundType_() throws RecognitionException {
		SqlWindowFrameBoundType_Context _localctx = new SqlWindowFrameBoundType_Context(_ctx, getState());
		enterRule(_localctx, 180, RULE_sqlWindowFrameBoundType_);
		try {
			setState(1041);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,117,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1033);
				match(UNBOUNDED);
				setState(1034);
				match(PRECEDING);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(1035);
				match(PRECEDING);
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(1036);
				match(CURRENT);
				setState(1037);
				match(ROW);
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(1038);
				match(FOLLOWING);
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(1039);
				match(UNBOUNDED);
				setState(1040);
				match(FOLLOWING);
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
	public static class SqlWindowClauseContext extends ParserRuleContext {
		public SqlWindowDeclItems_Context items;
		public TerminalNode WINDOW() { return getToken(EqlParser.WINDOW, 0); }
		public SqlWindowDeclItems_Context sqlWindowDeclItems_() {
			return getRuleContext(SqlWindowDeclItems_Context.class,0);
		}
		public SqlWindowClauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlWindowClause; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlWindowClause(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlWindowClause(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlWindowClause(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlWindowClauseContext sqlWindowClause() throws RecognitionException {
		SqlWindowClauseContext _localctx = new SqlWindowClauseContext(_ctx, getState());
		enterRule(_localctx, 182, RULE_sqlWindowClause);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1043);
			match(WINDOW);
			setState(1044);
			((SqlWindowClauseContext)_localctx).items = sqlWindowDeclItems_();
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
	public static class SqlWindowDeclItems_Context extends ParserRuleContext {
		public SqlWindowDeclContext e;
		public List<SqlWindowDeclContext> sqlWindowDecl() {
			return getRuleContexts(SqlWindowDeclContext.class);
		}
		public SqlWindowDeclContext sqlWindowDecl(int i) {
			return getRuleContext(SqlWindowDeclContext.class,i);
		}
		public List<TerminalNode> COMMA_() { return getTokens(EqlParser.COMMA_); }
		public TerminalNode COMMA_(int i) {
			return getToken(EqlParser.COMMA_, i);
		}
		public SqlWindowDeclItems_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlWindowDeclItems_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlWindowDeclItems_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlWindowDeclItems_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlWindowDeclItems_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlWindowDeclItems_Context sqlWindowDeclItems_() throws RecognitionException {
		SqlWindowDeclItems_Context _localctx = new SqlWindowDeclItems_Context(_ctx, getState());
		enterRule(_localctx, 184, RULE_sqlWindowDeclItems_);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(1046);
			((SqlWindowDeclItems_Context)_localctx).e = sqlWindowDecl();
			setState(1051);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,118,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(1047);
					match(COMMA_);
					setState(1048);
					((SqlWindowDeclItems_Context)_localctx).e = sqlWindowDecl();
					}
					} 
				}
				setState(1053);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,118,_ctx);
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
	public static class SqlWindowDeclContext extends ParserRuleContext {
		public SqlIdentifier_Context name;
		public SqlPartitionByContext partitionBy;
		public SqlOrderByContext orderBy;
		public SqlWindowFrameContext frame;
		public TerminalNode AS() { return getToken(EqlParser.AS, 0); }
		public TerminalNode LP_() { return getToken(EqlParser.LP_, 0); }
		public TerminalNode RP_() { return getToken(EqlParser.RP_, 0); }
		public SqlIdentifier_Context sqlIdentifier_() {
			return getRuleContext(SqlIdentifier_Context.class,0);
		}
		public SqlPartitionByContext sqlPartitionBy() {
			return getRuleContext(SqlPartitionByContext.class,0);
		}
		public SqlOrderByContext sqlOrderBy() {
			return getRuleContext(SqlOrderByContext.class,0);
		}
		public SqlWindowFrameContext sqlWindowFrame() {
			return getRuleContext(SqlWindowFrameContext.class,0);
		}
		public SqlWindowDeclContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlWindowDecl; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlWindowDecl(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlWindowDecl(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlWindowDecl(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlWindowDeclContext sqlWindowDecl() throws RecognitionException {
		SqlWindowDeclContext _localctx = new SqlWindowDeclContext(_ctx, getState());
		enterRule(_localctx, 186, RULE_sqlWindowDecl);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1054);
			((SqlWindowDeclContext)_localctx).name = sqlIdentifier_();
			setState(1055);
			match(AS);
			setState(1056);
			match(LP_);
			setState(1058);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==PARTITION) {
				{
				setState(1057);
				((SqlWindowDeclContext)_localctx).partitionBy = sqlPartitionBy();
				}
			}

			setState(1061);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ORDER) {
				{
				setState(1060);
				((SqlWindowDeclContext)_localctx).orderBy = sqlOrderBy();
				}
			}

			setState(1064);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (((((_la - 280)) & ~0x3f) == 0 && ((1L << (_la - 280)) & 2233382993921L) != 0)) {
				{
				setState(1063);
				((SqlWindowDeclContext)_localctx).frame = sqlWindowFrame();
				}
			}

			setState(1066);
			match(RP_);
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
	public static class SqlIdentifier_agg_Context extends ParserRuleContext {
		public TerminalNode MAX() { return getToken(EqlParser.MAX, 0); }
		public TerminalNode MIN() { return getToken(EqlParser.MIN, 0); }
		public TerminalNode SUM() { return getToken(EqlParser.SUM, 0); }
		public TerminalNode COUNT() { return getToken(EqlParser.COUNT, 0); }
		public TerminalNode AVG() { return getToken(EqlParser.AVG, 0); }
		public SqlIdentifier_agg_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlIdentifier_agg_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlIdentifier_agg_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlIdentifier_agg_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlIdentifier_agg_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlIdentifier_agg_Context sqlIdentifier_agg_() throws RecognitionException {
		SqlIdentifier_agg_Context _localctx = new SqlIdentifier_agg_Context(_ctx, getState());
		enterRule(_localctx, 188, RULE_sqlIdentifier_agg_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1068);
			_la = _input.LA(1);
			if ( !(((((_la - 143)) & ~0x3f) == 0 && ((1L << (_la - 143)) & 31L) != 0)) ) {
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
	public static class Distinct_Context extends ParserRuleContext {
		public TerminalNode DISTINCT() { return getToken(EqlParser.DISTINCT, 0); }
		public Distinct_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_distinct_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterDistinct_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitDistinct_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitDistinct_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Distinct_Context distinct_() throws RecognitionException {
		Distinct_Context _localctx = new Distinct_Context(_ctx, getState());
		enterRule(_localctx, 190, RULE_distinct_);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1070);
			match(DISTINCT);
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
	public static class FunctionArgs_Context extends ParserRuleContext {
		public SqlExprContext e;
		public List<SqlExprContext> sqlExpr() {
			return getRuleContexts(SqlExprContext.class);
		}
		public SqlExprContext sqlExpr(int i) {
			return getRuleContext(SqlExprContext.class,i);
		}
		public List<TerminalNode> COMMA_() { return getTokens(EqlParser.COMMA_); }
		public TerminalNode COMMA_(int i) {
			return getToken(EqlParser.COMMA_, i);
		}
		public FunctionArgs_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_functionArgs_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterFunctionArgs_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitFunctionArgs_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitFunctionArgs_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final FunctionArgs_Context functionArgs_() throws RecognitionException {
		FunctionArgs_Context _localctx = new FunctionArgs_Context(_ctx, getState());
		enterRule(_localctx, 192, RULE_functionArgs_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1072);
			((FunctionArgs_Context)_localctx).e = sqlExpr(0);
			setState(1077);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA_) {
				{
				{
				setState(1073);
				match(COMMA_);
				setState(1074);
				((FunctionArgs_Context)_localctx).e = sqlExpr(0);
				}
				}
				setState(1079);
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
	public static class SqlExpr_specialContext extends ParserRuleContext {
		public SqlExpr_specialContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlExpr_special; }
	 
		public SqlExpr_specialContext() { }
		public void copyFrom(SqlExpr_specialContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlCastExpr_castContext extends SqlExpr_specialContext {
		public SqlCastExprContext sqlCastExpr() {
			return getRuleContext(SqlCastExprContext.class,0);
		}
		public SqlCastExpr_castContext(SqlExpr_specialContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlCastExpr_cast(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlCastExpr_cast(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlCastExpr_cast(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlRegularFunction_trimDirectionCharsContext extends SqlExpr_specialContext {
		public TerminalNode TRIM() { return getToken(EqlParser.TRIM, 0); }
		public TerminalNode LP_() { return getToken(EqlParser.LP_, 0); }
		public TrimDirection__Context trimDirection__() {
			return getRuleContext(TrimDirection__Context.class,0);
		}
		public SqlExpr_simpleContext sqlExpr_simple() {
			return getRuleContext(SqlExpr_simpleContext.class,0);
		}
		public TerminalNode FROM() { return getToken(EqlParser.FROM, 0); }
		public SqlExprContext sqlExpr() {
			return getRuleContext(SqlExprContext.class,0);
		}
		public TerminalNode RP_() { return getToken(EqlParser.RP_, 0); }
		public SqlRegularFunction_trimDirectionCharsContext(SqlExpr_specialContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlRegularFunction_trimDirectionChars(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlRegularFunction_trimDirectionChars(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlRegularFunction_trimDirectionChars(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlRegularFunction_trimDirectionContext extends SqlExpr_specialContext {
		public TerminalNode TRIM() { return getToken(EqlParser.TRIM, 0); }
		public TerminalNode LP_() { return getToken(EqlParser.LP_, 0); }
		public TrimDirection__Context trimDirection__() {
			return getRuleContext(TrimDirection__Context.class,0);
		}
		public TerminalNode FROM() { return getToken(EqlParser.FROM, 0); }
		public SqlExprContext sqlExpr() {
			return getRuleContext(SqlExprContext.class,0);
		}
		public TerminalNode RP_() { return getToken(EqlParser.RP_, 0); }
		public SqlRegularFunction_trimDirectionContext(SqlExpr_specialContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlRegularFunction_trimDirection(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlRegularFunction_trimDirection(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlRegularFunction_trimDirection(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlRegularFunction_trimCharsContext extends SqlExpr_specialContext {
		public TerminalNode TRIM() { return getToken(EqlParser.TRIM, 0); }
		public TerminalNode LP_() { return getToken(EqlParser.LP_, 0); }
		public SqlExpr_simpleContext sqlExpr_simple() {
			return getRuleContext(SqlExpr_simpleContext.class,0);
		}
		public TerminalNode FROM() { return getToken(EqlParser.FROM, 0); }
		public SqlExprContext sqlExpr() {
			return getRuleContext(SqlExprContext.class,0);
		}
		public TerminalNode RP_() { return getToken(EqlParser.RP_, 0); }
		public SqlRegularFunction_trimCharsContext(SqlExpr_specialContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlRegularFunction_trimChars(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlRegularFunction_trimChars(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlRegularFunction_trimChars(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlRegularFunction_positionContext extends SqlExpr_specialContext {
		public TerminalNode POSITION() { return getToken(EqlParser.POSITION, 0); }
		public TerminalNode LP_() { return getToken(EqlParser.LP_, 0); }
		public SqlExpr_bitContext sqlExpr_bit() {
			return getRuleContext(SqlExpr_bitContext.class,0);
		}
		public TerminalNode IN() { return getToken(EqlParser.IN, 0); }
		public SqlExprContext sqlExpr() {
			return getRuleContext(SqlExprContext.class,0);
		}
		public TerminalNode RP_() { return getToken(EqlParser.RP_, 0); }
		public SqlRegularFunction_positionContext(SqlExpr_specialContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlRegularFunction_position(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlRegularFunction_position(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlRegularFunction_position(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlRegularFunction_substringContext extends SqlExpr_specialContext {
		public TerminalNode SUBSTRING() { return getToken(EqlParser.SUBSTRING, 0); }
		public TerminalNode LP_() { return getToken(EqlParser.LP_, 0); }
		public List<SqlExpr_bitContext> sqlExpr_bit() {
			return getRuleContexts(SqlExpr_bitContext.class);
		}
		public SqlExpr_bitContext sqlExpr_bit(int i) {
			return getRuleContext(SqlExpr_bitContext.class,i);
		}
		public TerminalNode FROM() { return getToken(EqlParser.FROM, 0); }
		public TerminalNode RP_() { return getToken(EqlParser.RP_, 0); }
		public TerminalNode FOR() { return getToken(EqlParser.FOR, 0); }
		public SqlRegularFunction_substringContext(SqlExpr_specialContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlRegularFunction_substring(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlRegularFunction_substring(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlRegularFunction_substring(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlRegularFunction_extractContext extends SqlExpr_specialContext {
		public TerminalNode EXTRACT() { return getToken(EqlParser.EXTRACT, 0); }
		public TerminalNode LP_() { return getToken(EqlParser.LP_, 0); }
		public ExtractField__Context extractField__() {
			return getRuleContext(ExtractField__Context.class,0);
		}
		public TerminalNode FROM() { return getToken(EqlParser.FROM, 0); }
		public SqlExprContext sqlExpr() {
			return getRuleContext(SqlExprContext.class,0);
		}
		public TerminalNode RP_() { return getToken(EqlParser.RP_, 0); }
		public SqlRegularFunction_extractContext(SqlExpr_specialContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlRegularFunction_extract(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlRegularFunction_extract(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlRegularFunction_extract(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlExpr_specialContext sqlExpr_special() throws RecognitionException {
		SqlExpr_specialContext _localctx = new SqlExpr_specialContext(_ctx, getState());
		enterRule(_localctx, 194, RULE_sqlExpr_special);
		int _la;
		try {
			setState(1128);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,124,_ctx) ) {
			case 1:
				_localctx = new SqlCastExpr_castContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(1080);
				sqlCastExpr();
				}
				break;
			case 2:
				_localctx = new SqlRegularFunction_positionContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(1081);
				match(POSITION);
				setState(1082);
				match(LP_);
				setState(1083);
				sqlExpr_bit(0);
				setState(1084);
				match(IN);
				setState(1085);
				sqlExpr(0);
				setState(1086);
				match(RP_);
				}
				break;
			case 3:
				_localctx = new SqlRegularFunction_substringContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(1088);
				match(SUBSTRING);
				setState(1089);
				match(LP_);
				setState(1090);
				sqlExpr_bit(0);
				setState(1091);
				match(FROM);
				setState(1092);
				sqlExpr_bit(0);
				setState(1095);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==FOR) {
					{
					setState(1093);
					match(FOR);
					setState(1094);
					sqlExpr_bit(0);
					}
				}

				setState(1097);
				match(RP_);
				}
				break;
			case 4:
				_localctx = new SqlRegularFunction_extractContext(_localctx);
				enterOuterAlt(_localctx, 4);
				{
				setState(1099);
				match(EXTRACT);
				setState(1100);
				match(LP_);
				setState(1101);
				extractField__();
				setState(1102);
				match(FROM);
				setState(1103);
				sqlExpr(0);
				setState(1104);
				match(RP_);
				}
				break;
			case 5:
				_localctx = new SqlRegularFunction_trimDirectionContext(_localctx);
				enterOuterAlt(_localctx, 5);
				{
				setState(1106);
				match(TRIM);
				setState(1107);
				match(LP_);
				setState(1108);
				trimDirection__();
				setState(1109);
				match(FROM);
				setState(1110);
				sqlExpr(0);
				setState(1111);
				match(RP_);
				}
				break;
			case 6:
				_localctx = new SqlRegularFunction_trimDirectionCharsContext(_localctx);
				enterOuterAlt(_localctx, 6);
				{
				setState(1113);
				match(TRIM);
				setState(1114);
				match(LP_);
				setState(1115);
				trimDirection__();
				setState(1116);
				sqlExpr_simple();
				setState(1117);
				match(FROM);
				setState(1118);
				sqlExpr(0);
				setState(1119);
				match(RP_);
				}
				break;
			case 7:
				_localctx = new SqlRegularFunction_trimCharsContext(_localctx);
				enterOuterAlt(_localctx, 7);
				{
				setState(1121);
				match(TRIM);
				setState(1122);
				match(LP_);
				setState(1123);
				sqlExpr_simple();
				setState(1124);
				match(FROM);
				setState(1125);
				sqlExpr(0);
				setState(1126);
				match(RP_);
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
	public static class TrimDirection__Context extends ParserRuleContext {
		public TerminalNode LEADING() { return getToken(EqlParser.LEADING, 0); }
		public TerminalNode BOTH() { return getToken(EqlParser.BOTH, 0); }
		public TerminalNode TRAILING() { return getToken(EqlParser.TRAILING, 0); }
		public TrimDirection__Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_trimDirection__; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterTrimDirection__(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitTrimDirection__(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitTrimDirection__(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TrimDirection__Context trimDirection__() throws RecognitionException {
		TrimDirection__Context _localctx = new TrimDirection__Context(_ctx, getState());
		enterRule(_localctx, 196, RULE_trimDirection__);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1130);
			_la = _input.LA(1);
			if ( !(_la==BOTH || _la==LEADING || _la==TRAILING) ) {
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
	public static class ExtractField__Context extends ParserRuleContext {
		public TerminalNode YEAR() { return getToken(EqlParser.YEAR, 0); }
		public TerminalNode MONTH() { return getToken(EqlParser.MONTH, 0); }
		public TerminalNode DAY() { return getToken(EqlParser.DAY, 0); }
		public TerminalNode HOUR() { return getToken(EqlParser.HOUR, 0); }
		public TerminalNode MINUTE() { return getToken(EqlParser.MINUTE, 0); }
		public TerminalNode SECOND() { return getToken(EqlParser.SECOND, 0); }
		public TerminalNode QUARTER() { return getToken(EqlParser.QUARTER, 0); }
		public ExtractField__Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_extractField__; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterExtractField__(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitExtractField__(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitExtractField__(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ExtractField__Context extractField__() throws RecognitionException {
		ExtractField__Context _localctx = new ExtractField__Context(_ctx, getState());
		enterRule(_localctx, 198, RULE_extractField__);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1132);
			_la = _input.LA(1);
			if ( !(((((_la - 134)) & ~0x3f) == 0 && ((1L << (_la - 134)) & 247L) != 0)) ) {
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
	public static class SqlCastExprContext extends ParserRuleContext {
		public SqlExprContext expr;
		public SqlTypeExprContext dataType;
		public TerminalNode CAST() { return getToken(EqlParser.CAST, 0); }
		public TerminalNode LP_() { return getToken(EqlParser.LP_, 0); }
		public TerminalNode AS() { return getToken(EqlParser.AS, 0); }
		public TerminalNode RP_() { return getToken(EqlParser.RP_, 0); }
		public SqlTypeExprContext sqlTypeExpr() {
			return getRuleContext(SqlTypeExprContext.class,0);
		}
		public TerminalNode NULL() { return getToken(EqlParser.NULL, 0); }
		public SqlExprContext sqlExpr() {
			return getRuleContext(SqlExprContext.class,0);
		}
		public SqlCastExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlCastExpr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlCastExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlCastExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlCastExpr(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlCastExprContext sqlCastExpr() throws RecognitionException {
		SqlCastExprContext _localctx = new SqlCastExprContext(_ctx, getState());
		enterRule(_localctx, 200, RULE_sqlCastExpr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1134);
			match(CAST);
			setState(1135);
			match(LP_);
			setState(1138);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,125,_ctx) ) {
			case 1:
				{
				setState(1136);
				((SqlCastExprContext)_localctx).expr = sqlExpr(0);
				}
				break;
			case 2:
				{
				setState(1137);
				match(NULL);
				}
				break;
			}
			setState(1140);
			match(AS);
			setState(1141);
			((SqlCastExprContext)_localctx).dataType = sqlTypeExpr();
			setState(1142);
			match(RP_);
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
	public static class SqlRegularFunctionContext extends ParserRuleContext {
		public SqlIdentifier_func_Context name;
		public FunctionArgs_Context args;
		public TerminalNode LP_() { return getToken(EqlParser.LP_, 0); }
		public TerminalNode RP_() { return getToken(EqlParser.RP_, 0); }
		public SqlIdentifier_func_Context sqlIdentifier_func_() {
			return getRuleContext(SqlIdentifier_func_Context.class,0);
		}
		public FunctionArgs_Context functionArgs_() {
			return getRuleContext(FunctionArgs_Context.class,0);
		}
		public SqlRegularFunctionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlRegularFunction; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlRegularFunction(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlRegularFunction(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlRegularFunction(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlRegularFunctionContext sqlRegularFunction() throws RecognitionException {
		SqlRegularFunctionContext _localctx = new SqlRegularFunctionContext(_ctx, getState());
		enterRule(_localctx, 202, RULE_sqlRegularFunction);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1144);
			((SqlRegularFunctionContext)_localctx).name = sqlIdentifier_func_();
			setState(1145);
			match(LP_);
			setState(1147);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 137707401240L) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & 36453140077275137L) != 0) || ((((_la - 128)) & ~0x3f) == 0 && ((1L << (_la - 128)) & -2775098091127297L) != 0) || ((((_la - 192)) & ~0x3f) == 0 && ((1L << (_la - 192)) & 721143305559080959L) != 0) || ((((_la - 263)) & ~0x3f) == 0 && ((1L << (_la - 263)) & 3607524729773230337L) != 0) || ((((_la - 335)) & ~0x3f) == 0 && ((1L << (_la - 335)) & 1073427715L) != 0)) {
				{
				setState(1146);
				((SqlRegularFunctionContext)_localctx).args = functionArgs_();
				}
			}

			setState(1149);
			match(RP_);
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
	public static class SqlIdentifier_func_Context extends ParserRuleContext {
		public SqlIdentifier_Context sqlIdentifier_() {
			return getRuleContext(SqlIdentifier_Context.class,0);
		}
		public TerminalNode IF() { return getToken(EqlParser.IF, 0); }
		public TerminalNode CURRENT_TIMESTAMP() { return getToken(EqlParser.CURRENT_TIMESTAMP, 0); }
		public TerminalNode LOCALTIME() { return getToken(EqlParser.LOCALTIME, 0); }
		public TerminalNode LOCALTIMESTAMP() { return getToken(EqlParser.LOCALTIMESTAMP, 0); }
		public TerminalNode INTERVAL() { return getToken(EqlParser.INTERVAL, 0); }
		public TerminalNode EXTRACT() { return getToken(EqlParser.EXTRACT, 0); }
		public TerminalNode NULLIF() { return getToken(EqlParser.NULLIF, 0); }
		public TerminalNode TRIM() { return getToken(EqlParser.TRIM, 0); }
		public TerminalNode SUBSTRING() { return getToken(EqlParser.SUBSTRING, 0); }
		public SqlIdentifier_func_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlIdentifier_func_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlIdentifier_func_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlIdentifier_func_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlIdentifier_func_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlIdentifier_func_Context sqlIdentifier_func_() throws RecognitionException {
		SqlIdentifier_func_Context _localctx = new SqlIdentifier_func_Context(_ctx, getState());
		enterRule(_localctx, 204, RULE_sqlIdentifier_func_);
		try {
			setState(1161);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case POSITION:
			case LATERAL:
			case CROSS:
			case ORDER:
			case GROUP:
			case BEGIN:
			case DATE:
			case TIMESTAMP:
			case YEAR:
			case QUARTER:
			case MONTH:
			case DAY:
			case HOUR:
			case MINUTE:
			case SECOND:
			case COUNT:
			case CURRENT_USER:
			case NAME:
			case TYPE:
			case RECURSIVE:
			case UPSERT:
			case CONFLICT:
			case CATALOG_NAME:
			case CHARACTER_SET_CATALOG:
			case CHARACTER_SET_NAME:
			case CHARACTER_SET_SCHEMA:
			case CLASS_ORIGIN:
			case COBOL:
			case COLLATION_CATALOG:
			case COLLATION_NAME:
			case COLLATION_SCHEMA:
			case COLUMN_NAME:
			case COMMAND_FUNCTION:
			case COMMITTED:
			case CONDITION_NUMBER:
			case CONNECTION_NAME:
			case CONSTRAINT_CATALOG:
			case CONSTRAINT_NAME:
			case CONSTRAINT_SCHEMA:
			case CURSOR_NAME:
			case DATA:
			case DATETIME_INTERVAL_CODE:
			case DATETIME_INTERVAL_PRECISION:
			case DYNAMIC_FUNCTION:
			case FORTRAN:
			case LENGTH:
			case MESSAGE_LENGTH:
			case MESSAGE_OCTET_LENGTH:
			case MESSAGE_TEXT:
			case MORE92:
			case MUMPS:
			case NULLABLE:
			case NUMBER:
			case PASCAL:
			case PLI:
			case REPEATABLE:
			case RETURNED_LENGTH:
			case RETURNED_OCTET_LENGTH:
			case RETURNED_SQLSTATE:
			case ROW_COUNT:
			case SCALE:
			case SCHEMA_NAME:
			case SERIALIZABLE:
			case SERVER_NAME:
			case SUBCLASS_ORIGIN:
			case TABLE_NAME:
			case UNCOMMITTED:
			case UNNAMED:
			case BIT_LENGTH:
			case COALESCE:
			case CURRENT_DATE:
			case END:
			case FOLLOWING:
			case GROUPS:
			case INDICATOR:
			case INSENSITIVE:
			case LANGUAGE:
			case NULLS:
			case LEVEL:
			case LOWER:
			case OCTET_LENGTH:
			case PRECEDING:
			case RANGE:
			case ROW:
			case SECTION:
			case SESSION:
			case TUMBLE:
			case HOP:
			case UNBOUNDED:
			case UPPER:
			case USER:
			case VALUE:
			case WINDOW:
			case WORK:
			case ZONE:
			case ROLLUP:
			case CUBE:
			case NOWAIT:
			case SKIP_:
			case LOCKED:
			case IDENTIFIER_:
				enterOuterAlt(_localctx, 1);
				{
				setState(1151);
				sqlIdentifier_();
				}
				break;
			case IF:
				enterOuterAlt(_localctx, 2);
				{
				setState(1152);
				match(IF);
				}
				break;
			case CURRENT_TIMESTAMP:
				enterOuterAlt(_localctx, 3);
				{
				setState(1153);
				match(CURRENT_TIMESTAMP);
				}
				break;
			case LOCALTIME:
				enterOuterAlt(_localctx, 4);
				{
				setState(1154);
				match(LOCALTIME);
				}
				break;
			case LOCALTIMESTAMP:
				enterOuterAlt(_localctx, 5);
				{
				setState(1155);
				match(LOCALTIMESTAMP);
				}
				break;
			case INTERVAL:
				enterOuterAlt(_localctx, 6);
				{
				setState(1156);
				match(INTERVAL);
				}
				break;
			case EXTRACT:
				enterOuterAlt(_localctx, 7);
				{
				setState(1157);
				match(EXTRACT);
				}
				break;
			case NULLIF:
				enterOuterAlt(_localctx, 8);
				{
				setState(1158);
				match(NULLIF);
				}
				break;
			case TRIM:
				enterOuterAlt(_localctx, 9);
				{
				setState(1159);
				match(TRIM);
				}
				break;
			case SUBSTRING:
				enterOuterAlt(_localctx, 10);
				{
				setState(1160);
				match(SUBSTRING);
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
	public static class SqlDecorators_Context extends ParserRuleContext {
		public SqlDecoratorContext e;
		public List<SqlDecoratorContext> sqlDecorator() {
			return getRuleContexts(SqlDecoratorContext.class);
		}
		public SqlDecoratorContext sqlDecorator(int i) {
			return getRuleContext(SqlDecoratorContext.class,i);
		}
		public List<TerminalNode> COMMA_() { return getTokens(EqlParser.COMMA_); }
		public TerminalNode COMMA_(int i) {
			return getToken(EqlParser.COMMA_, i);
		}
		public SqlDecorators_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlDecorators_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlDecorators_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlDecorators_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlDecorators_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlDecorators_Context sqlDecorators_() throws RecognitionException {
		SqlDecorators_Context _localctx = new SqlDecorators_Context(_ctx, getState());
		enterRule(_localctx, 206, RULE_sqlDecorators_);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(1163);
			((SqlDecorators_Context)_localctx).e = sqlDecorator();
			setState(1168);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,128,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(1164);
					match(COMMA_);
					setState(1165);
					((SqlDecorators_Context)_localctx).e = sqlDecorator();
					}
					} 
				}
				setState(1170);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,128,_ctx);
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
	public static class SqlDecoratorContext extends ParserRuleContext {
		public SqlIdentifier_Context name;
		public DecoratorArgs_Context args;
		public TerminalNode AT_() { return getToken(EqlParser.AT_, 0); }
		public SqlIdentifier_Context sqlIdentifier_() {
			return getRuleContext(SqlIdentifier_Context.class,0);
		}
		public TerminalNode LP_() { return getToken(EqlParser.LP_, 0); }
		public TerminalNode RP_() { return getToken(EqlParser.RP_, 0); }
		public DecoratorArgs_Context decoratorArgs_() {
			return getRuleContext(DecoratorArgs_Context.class,0);
		}
		public SqlDecoratorContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlDecorator; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlDecorator(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlDecorator(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlDecorator(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlDecoratorContext sqlDecorator() throws RecognitionException {
		SqlDecoratorContext _localctx = new SqlDecoratorContext(_ctx, getState());
		enterRule(_localctx, 208, RULE_sqlDecorator);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1171);
			match(AT_);
			setState(1172);
			((SqlDecoratorContext)_localctx).name = sqlIdentifier_();
			setState(1178);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,130,_ctx) ) {
			case 1:
				{
				setState(1173);
				match(LP_);
				setState(1175);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (((((_la - 101)) & ~0x3f) == 0 && ((1L << (_la - 101)) & 1879048199L) != 0) || ((((_la - 361)) & ~0x3f) == 0 && ((1L << (_la - 361)) & 15L) != 0)) {
					{
					setState(1174);
					((SqlDecoratorContext)_localctx).args = decoratorArgs_();
					}
				}

				setState(1177);
				match(RP_);
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
	public static class DecoratorArgs_Context extends ParserRuleContext {
		public SqlLiteralContext e;
		public List<SqlLiteralContext> sqlLiteral() {
			return getRuleContexts(SqlLiteralContext.class);
		}
		public SqlLiteralContext sqlLiteral(int i) {
			return getRuleContext(SqlLiteralContext.class,i);
		}
		public List<TerminalNode> COMMA_() { return getTokens(EqlParser.COMMA_); }
		public TerminalNode COMMA_(int i) {
			return getToken(EqlParser.COMMA_, i);
		}
		public DecoratorArgs_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_decoratorArgs_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterDecoratorArgs_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitDecoratorArgs_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitDecoratorArgs_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final DecoratorArgs_Context decoratorArgs_() throws RecognitionException {
		DecoratorArgs_Context _localctx = new DecoratorArgs_Context(_ctx, getState());
		enterRule(_localctx, 210, RULE_decoratorArgs_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1180);
			((DecoratorArgs_Context)_localctx).e = sqlLiteral();
			setState(1185);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA_) {
				{
				{
				setState(1181);
				match(COMMA_);
				setState(1182);
				((DecoratorArgs_Context)_localctx).e = sqlLiteral();
				}
				}
				setState(1187);
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
	public static class SqlCaseExprContext extends ParserRuleContext {
		public SqlExpr_simpleContext test;
		public CaseWhens_Context caseWhens;
		public SqlExprContext elseExpr;
		public TerminalNode CASE() { return getToken(EqlParser.CASE, 0); }
		public TerminalNode END() { return getToken(EqlParser.END, 0); }
		public CaseWhens_Context caseWhens_() {
			return getRuleContext(CaseWhens_Context.class,0);
		}
		public TerminalNode ELSE() { return getToken(EqlParser.ELSE, 0); }
		public SqlExpr_simpleContext sqlExpr_simple() {
			return getRuleContext(SqlExpr_simpleContext.class,0);
		}
		public SqlExprContext sqlExpr() {
			return getRuleContext(SqlExprContext.class,0);
		}
		public SqlCaseExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlCaseExpr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlCaseExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlCaseExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlCaseExpr(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlCaseExprContext sqlCaseExpr() throws RecognitionException {
		SqlCaseExprContext _localctx = new SqlCaseExprContext(_ctx, getState());
		enterRule(_localctx, 212, RULE_sqlCaseExpr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1188);
			match(CASE);
			setState(1190);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 137707401240L) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & 36453071357798401L) != 0) || ((((_la - 128)) & ~0x3f) == 0 && ((1L << (_la - 128)) & -2775098091127297L) != 0) || ((((_la - 192)) & ~0x3f) == 0 && ((1L << (_la - 192)) & 721143305559080959L) != 0) || ((((_la - 263)) & ~0x3f) == 0 && ((1L << (_la - 263)) & 3607524729773230337L) != 0) || ((((_la - 335)) & ~0x3f) == 0 && ((1L << (_la - 335)) & 1073427715L) != 0)) {
				{
				setState(1189);
				((SqlCaseExprContext)_localctx).test = sqlExpr_simple();
				}
			}

			setState(1192);
			((SqlCaseExprContext)_localctx).caseWhens = caseWhens_();
			setState(1195);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ELSE) {
				{
				setState(1193);
				match(ELSE);
				setState(1194);
				((SqlCaseExprContext)_localctx).elseExpr = sqlExpr(0);
				}
			}

			setState(1197);
			match(END);
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
	public static class CaseWhens_Context extends ParserRuleContext {
		public SqlCaseWhenItemContext e;
		public List<SqlCaseWhenItemContext> sqlCaseWhenItem() {
			return getRuleContexts(SqlCaseWhenItemContext.class);
		}
		public SqlCaseWhenItemContext sqlCaseWhenItem(int i) {
			return getRuleContext(SqlCaseWhenItemContext.class,i);
		}
		public CaseWhens_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_caseWhens_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterCaseWhens_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitCaseWhens_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitCaseWhens_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final CaseWhens_Context caseWhens_() throws RecognitionException {
		CaseWhens_Context _localctx = new CaseWhens_Context(_ctx, getState());
		enterRule(_localctx, 214, RULE_caseWhens_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1200); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(1199);
				((CaseWhens_Context)_localctx).e = sqlCaseWhenItem();
				}
				}
				setState(1202); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( _la==WHEN );
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
	public static class SqlCaseWhenItemContext extends ParserRuleContext {
		public SqlExprContext when;
		public SqlExprContext then;
		public TerminalNode WHEN() { return getToken(EqlParser.WHEN, 0); }
		public TerminalNode THEN() { return getToken(EqlParser.THEN, 0); }
		public List<SqlExprContext> sqlExpr() {
			return getRuleContexts(SqlExprContext.class);
		}
		public SqlExprContext sqlExpr(int i) {
			return getRuleContext(SqlExprContext.class,i);
		}
		public SqlCaseWhenItemContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlCaseWhenItem; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlCaseWhenItem(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlCaseWhenItem(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlCaseWhenItem(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlCaseWhenItemContext sqlCaseWhenItem() throws RecognitionException {
		SqlCaseWhenItemContext _localctx = new SqlCaseWhenItemContext(_ctx, getState());
		enterRule(_localctx, 216, RULE_sqlCaseWhenItem);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1204);
			match(WHEN);
			setState(1205);
			((SqlCaseWhenItemContext)_localctx).when = sqlExpr(0);
			setState(1206);
			match(THEN);
			setState(1207);
			((SqlCaseWhenItemContext)_localctx).then = sqlExpr(0);
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
	public static class SqlIntervalExprContext extends ParserRuleContext {
		public SqlIntervalExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlIntervalExpr; }
	 
		public SqlIntervalExprContext() { }
		public void copyFrom(SqlIntervalExprContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlIntervalExpr_stringContext extends SqlIntervalExprContext {
		public TerminalNode INTERVAL() { return getToken(EqlParser.INTERVAL, 0); }
		public TerminalNode STRING_() { return getToken(EqlParser.STRING_, 0); }
		public SqlIntervalExpr_stringContext(SqlIntervalExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlIntervalExpr_string(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlIntervalExpr_string(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlIntervalExpr_string(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlIntervalExpr_unitContext extends SqlIntervalExprContext {
		public SqlExprContext expr;
		public IntervalUnit_Context intervalUnit;
		public TerminalNode INTERVAL() { return getToken(EqlParser.INTERVAL, 0); }
		public SqlExprContext sqlExpr() {
			return getRuleContext(SqlExprContext.class,0);
		}
		public IntervalUnit_Context intervalUnit_() {
			return getRuleContext(IntervalUnit_Context.class,0);
		}
		public SqlIntervalExpr_unitContext(SqlIntervalExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlIntervalExpr_unit(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlIntervalExpr_unit(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlIntervalExpr_unit(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlIntervalExprContext sqlIntervalExpr() throws RecognitionException {
		SqlIntervalExprContext _localctx = new SqlIntervalExprContext(_ctx, getState());
		enterRule(_localctx, 218, RULE_sqlIntervalExpr);
		try {
			setState(1215);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,135,_ctx) ) {
			case 1:
				_localctx = new SqlIntervalExpr_unitContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(1209);
				match(INTERVAL);
				setState(1210);
				((SqlIntervalExpr_unitContext)_localctx).expr = sqlExpr(0);
				setState(1211);
				((SqlIntervalExpr_unitContext)_localctx).intervalUnit = intervalUnit_();
				}
				break;
			case 2:
				_localctx = new SqlIntervalExpr_stringContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(1213);
				match(INTERVAL);
				setState(1214);
				match(STRING_);
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
	public static class IntervalUnit_Context extends ParserRuleContext {
		public TerminalNode MICROSECOND() { return getToken(EqlParser.MICROSECOND, 0); }
		public TerminalNode SECOND() { return getToken(EqlParser.SECOND, 0); }
		public TerminalNode MINUTE() { return getToken(EqlParser.MINUTE, 0); }
		public TerminalNode HOUR() { return getToken(EqlParser.HOUR, 0); }
		public TerminalNode DAY() { return getToken(EqlParser.DAY, 0); }
		public TerminalNode WEEK() { return getToken(EqlParser.WEEK, 0); }
		public TerminalNode MONTH() { return getToken(EqlParser.MONTH, 0); }
		public TerminalNode QUARTER() { return getToken(EqlParser.QUARTER, 0); }
		public TerminalNode YEAR() { return getToken(EqlParser.YEAR, 0); }
		public IntervalUnit_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_intervalUnit_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterIntervalUnit_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitIntervalUnit_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitIntervalUnit_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final IntervalUnit_Context intervalUnit_() throws RecognitionException {
		IntervalUnit_Context _localctx = new IntervalUnit_Context(_ctx, getState());
		enterRule(_localctx, 220, RULE_intervalUnit_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1217);
			_la = _input.LA(1);
			if ( !(((((_la - 134)) & ~0x3f) == 0 && ((1L << (_la - 134)) & 511L) != 0)) ) {
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
	public static class SqlOrderByContext extends ParserRuleContext {
		public SqlOrderByItems_Context items;
		public TerminalNode ORDER() { return getToken(EqlParser.ORDER, 0); }
		public TerminalNode BY() { return getToken(EqlParser.BY, 0); }
		public SqlOrderByItems_Context sqlOrderByItems_() {
			return getRuleContext(SqlOrderByItems_Context.class,0);
		}
		public SqlOrderByContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlOrderBy; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlOrderBy(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlOrderBy(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlOrderBy(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlOrderByContext sqlOrderBy() throws RecognitionException {
		SqlOrderByContext _localctx = new SqlOrderByContext(_ctx, getState());
		enterRule(_localctx, 222, RULE_sqlOrderBy);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1219);
			match(ORDER);
			setState(1220);
			match(BY);
			setState(1221);
			((SqlOrderByContext)_localctx).items = sqlOrderByItems_();
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
	public static class SqlOrderByItems_Context extends ParserRuleContext {
		public SqlOrderByItemContext e;
		public List<SqlOrderByItemContext> sqlOrderByItem() {
			return getRuleContexts(SqlOrderByItemContext.class);
		}
		public SqlOrderByItemContext sqlOrderByItem(int i) {
			return getRuleContext(SqlOrderByItemContext.class,i);
		}
		public List<TerminalNode> COMMA_() { return getTokens(EqlParser.COMMA_); }
		public TerminalNode COMMA_(int i) {
			return getToken(EqlParser.COMMA_, i);
		}
		public SqlOrderByItems_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlOrderByItems_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlOrderByItems_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlOrderByItems_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlOrderByItems_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlOrderByItems_Context sqlOrderByItems_() throws RecognitionException {
		SqlOrderByItems_Context _localctx = new SqlOrderByItems_Context(_ctx, getState());
		enterRule(_localctx, 224, RULE_sqlOrderByItems_);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(1223);
			((SqlOrderByItems_Context)_localctx).e = sqlOrderByItem();
			setState(1228);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,136,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(1224);
					match(COMMA_);
					setState(1225);
					((SqlOrderByItems_Context)_localctx).e = sqlOrderByItem();
					}
					} 
				}
				setState(1230);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,136,_ctx);
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
	public static class SqlOrderByItemContext extends ParserRuleContext {
		public SqlExprContext expr;
		public Token asc;
		public Token nullsFirst;
		public SqlExprContext sqlExpr() {
			return getRuleContext(SqlExprContext.class,0);
		}
		public TerminalNode NULLS() { return getToken(EqlParser.NULLS, 0); }
		public TerminalNode ASC() { return getToken(EqlParser.ASC, 0); }
		public TerminalNode DESC() { return getToken(EqlParser.DESC, 0); }
		public TerminalNode FIRST() { return getToken(EqlParser.FIRST, 0); }
		public TerminalNode LAST() { return getToken(EqlParser.LAST, 0); }
		public SqlOrderByItemContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlOrderByItem; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlOrderByItem(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlOrderByItem(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlOrderByItem(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlOrderByItemContext sqlOrderByItem() throws RecognitionException {
		SqlOrderByItemContext _localctx = new SqlOrderByItemContext(_ctx, getState());
		enterRule(_localctx, 226, RULE_sqlOrderByItem);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1231);
			((SqlOrderByItemContext)_localctx).expr = sqlExpr(0);
			setState(1233);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,137,_ctx) ) {
			case 1:
				{
				setState(1232);
				((SqlOrderByItemContext)_localctx).asc = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==ASC || _la==DESC) ) {
					((SqlOrderByItemContext)_localctx).asc = (Token)_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				}
				break;
			}
			setState(1237);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,138,_ctx) ) {
			case 1:
				{
				setState(1235);
				match(NULLS);
				setState(1236);
				((SqlOrderByItemContext)_localctx).nullsFirst = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==FIRST || _la==LAST) ) {
					((SqlOrderByItemContext)_localctx).nullsFirst = (Token)_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
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
	public static class SqlGroupByItemContext extends ParserRuleContext {
		public SqlGroupByItemContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlGroupByItem; }
	 
		public SqlGroupByItemContext() { }
		public void copyFrom(SqlGroupByItemContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlGroupByItem_rollupContext extends SqlGroupByItemContext {
		public TerminalNode ROLLUP() { return getToken(EqlParser.ROLLUP, 0); }
		public TerminalNode LP_() { return getToken(EqlParser.LP_, 0); }
		public TerminalNode RP_() { return getToken(EqlParser.RP_, 0); }
		public FunctionArgs_Context functionArgs_() {
			return getRuleContext(FunctionArgs_Context.class,0);
		}
		public SqlGroupByItem_rollupContext(SqlGroupByItemContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlGroupByItem_rollup(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlGroupByItem_rollup(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlGroupByItem_rollup(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlGroupByItem_exprContext extends SqlGroupByItemContext {
		public SqlExprContext expr;
		public SqlExprContext sqlExpr() {
			return getRuleContext(SqlExprContext.class,0);
		}
		public SqlGroupByItem_exprContext(SqlGroupByItemContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlGroupByItem_expr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlGroupByItem_expr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlGroupByItem_expr(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class SqlGroupByItem_cubeContext extends SqlGroupByItemContext {
		public TerminalNode CUBE() { return getToken(EqlParser.CUBE, 0); }
		public TerminalNode LP_() { return getToken(EqlParser.LP_, 0); }
		public TerminalNode RP_() { return getToken(EqlParser.RP_, 0); }
		public FunctionArgs_Context functionArgs_() {
			return getRuleContext(FunctionArgs_Context.class,0);
		}
		public SqlGroupByItem_cubeContext(SqlGroupByItemContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlGroupByItem_cube(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlGroupByItem_cube(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlGroupByItem_cube(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlGroupByItemContext sqlGroupByItem() throws RecognitionException {
		SqlGroupByItemContext _localctx = new SqlGroupByItemContext(_ctx, getState());
		enterRule(_localctx, 228, RULE_sqlGroupByItem);
		int _la;
		try {
			setState(1252);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,141,_ctx) ) {
			case 1:
				_localctx = new SqlGroupByItem_rollupContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(1239);
				match(ROLLUP);
				setState(1240);
				match(LP_);
				setState(1242);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 137707401240L) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & 36453140077275137L) != 0) || ((((_la - 128)) & ~0x3f) == 0 && ((1L << (_la - 128)) & -2775098091127297L) != 0) || ((((_la - 192)) & ~0x3f) == 0 && ((1L << (_la - 192)) & 721143305559080959L) != 0) || ((((_la - 263)) & ~0x3f) == 0 && ((1L << (_la - 263)) & 3607524729773230337L) != 0) || ((((_la - 335)) & ~0x3f) == 0 && ((1L << (_la - 335)) & 1073427715L) != 0)) {
					{
					setState(1241);
					functionArgs_();
					}
				}

				setState(1244);
				match(RP_);
				}
				break;
			case 2:
				_localctx = new SqlGroupByItem_cubeContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(1245);
				match(CUBE);
				setState(1246);
				match(LP_);
				setState(1248);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 137707401240L) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & 36453140077275137L) != 0) || ((((_la - 128)) & ~0x3f) == 0 && ((1L << (_la - 128)) & -2775098091127297L) != 0) || ((((_la - 192)) & ~0x3f) == 0 && ((1L << (_la - 192)) & 721143305559080959L) != 0) || ((((_la - 263)) & ~0x3f) == 0 && ((1L << (_la - 263)) & 3607524729773230337L) != 0) || ((((_la - 335)) & ~0x3f) == 0 && ((1L << (_la - 335)) & 1073427715L) != 0)) {
					{
					setState(1247);
					functionArgs_();
					}
				}

				setState(1250);
				match(RP_);
				}
				break;
			case 3:
				_localctx = new SqlGroupByItem_exprContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(1251);
				((SqlGroupByItem_exprContext)_localctx).expr = sqlExpr(0);
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
	public static class SqlTypeExprContext extends ParserRuleContext {
		public DataTypeName_Context name;
		public Token precision;
		public Token scale;
		public CharacterSet_Context characterSet;
		public CollateClause_Context collate;
		public DataTypeName_Context dataTypeName_() {
			return getRuleContext(DataTypeName_Context.class,0);
		}
		public TerminalNode LP_() { return getToken(EqlParser.LP_, 0); }
		public TerminalNode RP_() { return getToken(EqlParser.RP_, 0); }
		public List<TerminalNode> NUMBER_() { return getTokens(EqlParser.NUMBER_); }
		public TerminalNode NUMBER_(int i) {
			return getToken(EqlParser.NUMBER_, i);
		}
		public CharacterSet_Context characterSet_() {
			return getRuleContext(CharacterSet_Context.class,0);
		}
		public CollateClause_Context collateClause_() {
			return getRuleContext(CollateClause_Context.class,0);
		}
		public TerminalNode COMMA_() { return getToken(EqlParser.COMMA_, 0); }
		public SqlTypeExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sqlTypeExpr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterSqlTypeExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitSqlTypeExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitSqlTypeExpr(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SqlTypeExprContext sqlTypeExpr() throws RecognitionException {
		SqlTypeExprContext _localctx = new SqlTypeExprContext(_ctx, getState());
		enterRule(_localctx, 230, RULE_sqlTypeExpr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1254);
			((SqlTypeExprContext)_localctx).name = dataTypeName_();
			setState(1262);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LP_) {
				{
				setState(1255);
				match(LP_);
				setState(1256);
				((SqlTypeExprContext)_localctx).precision = match(NUMBER_);
				setState(1259);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COMMA_) {
					{
					setState(1257);
					match(COMMA_);
					setState(1258);
					((SqlTypeExprContext)_localctx).scale = match(NUMBER_);
					}
				}

				setState(1261);
				match(RP_);
				}
			}

			setState(1265);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==CHAR || _la==CHARACTER) {
				{
				setState(1264);
				((SqlTypeExprContext)_localctx).characterSet = characterSet_();
				}
			}

			setState(1268);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COLLATE) {
				{
				setState(1267);
				((SqlTypeExprContext)_localctx).collate = collateClause_();
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
	public static class DataTypeName_Context extends ParserRuleContext {
		public TerminalNode CHAR() { return getToken(EqlParser.CHAR, 0); }
		public TerminalNode VARCHAR() { return getToken(EqlParser.VARCHAR, 0); }
		public TerminalNode NUMERIC() { return getToken(EqlParser.NUMERIC, 0); }
		public TerminalNode DECIMAL() { return getToken(EqlParser.DECIMAL, 0); }
		public TerminalNode INTEGER() { return getToken(EqlParser.INTEGER, 0); }
		public TerminalNode SMALLINT() { return getToken(EqlParser.SMALLINT, 0); }
		public TerminalNode FLOAT() { return getToken(EqlParser.FLOAT, 0); }
		public TerminalNode REAL() { return getToken(EqlParser.REAL, 0); }
		public TerminalNode DATE() { return getToken(EqlParser.DATE, 0); }
		public List<TerminalNode> TIME() { return getTokens(EqlParser.TIME); }
		public TerminalNode TIME(int i) {
			return getToken(EqlParser.TIME, i);
		}
		public TerminalNode TIMESTAMP() { return getToken(EqlParser.TIMESTAMP, 0); }
		public TerminalNode BOOLEAN() { return getToken(EqlParser.BOOLEAN, 0); }
		public TerminalNode WITH() { return getToken(EqlParser.WITH, 0); }
		public TerminalNode LOCAL() { return getToken(EqlParser.LOCAL, 0); }
		public TerminalNode ZONE() { return getToken(EqlParser.ZONE, 0); }
		public SqlIdentifier_Context sqlIdentifier_() {
			return getRuleContext(SqlIdentifier_Context.class,0);
		}
		public DataTypeName_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_dataTypeName_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterDataTypeName_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitDataTypeName_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitDataTypeName_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final DataTypeName_Context dataTypeName_() throws RecognitionException {
		DataTypeName_Context _localctx = new DataTypeName_Context(_ctx, getState());
		enterRule(_localctx, 232, RULE_dataTypeName_);
		try {
			setState(1293);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,146,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1270);
				match(CHAR);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(1271);
				match(VARCHAR);
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(1272);
				match(NUMERIC);
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(1273);
				match(DECIMAL);
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(1274);
				match(INTEGER);
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(1275);
				match(SMALLINT);
				}
				break;
			case 7:
				enterOuterAlt(_localctx, 7);
				{
				setState(1276);
				match(FLOAT);
				}
				break;
			case 8:
				enterOuterAlt(_localctx, 8);
				{
				setState(1277);
				match(REAL);
				}
				break;
			case 9:
				enterOuterAlt(_localctx, 9);
				{
				setState(1278);
				match(DATE);
				}
				break;
			case 10:
				enterOuterAlt(_localctx, 10);
				{
				setState(1279);
				match(TIME);
				}
				break;
			case 11:
				enterOuterAlt(_localctx, 11);
				{
				setState(1280);
				match(TIMESTAMP);
				}
				break;
			case 12:
				enterOuterAlt(_localctx, 12);
				{
				setState(1281);
				match(BOOLEAN);
				}
				break;
			case 13:
				enterOuterAlt(_localctx, 13);
				{
				setState(1282);
				match(TIME);
				setState(1283);
				match(WITH);
				setState(1284);
				match(LOCAL);
				setState(1285);
				match(TIME);
				setState(1286);
				match(ZONE);
				}
				break;
			case 14:
				enterOuterAlt(_localctx, 14);
				{
				setState(1287);
				match(TIMESTAMP);
				setState(1288);
				match(WITH);
				setState(1289);
				match(LOCAL);
				setState(1290);
				match(TIME);
				setState(1291);
				match(ZONE);
				}
				break;
			case 15:
				enterOuterAlt(_localctx, 15);
				{
				setState(1292);
				sqlIdentifier_();
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
	public static class CharacterSet_Context extends ParserRuleContext {
		public Token characterSet;
		public TerminalNode SET() { return getToken(EqlParser.SET, 0); }
		public TerminalNode CHARACTER() { return getToken(EqlParser.CHARACTER, 0); }
		public TerminalNode CHAR() { return getToken(EqlParser.CHAR, 0); }
		public TerminalNode STRING() { return getToken(EqlParser.STRING, 0); }
		public TerminalNode EQ_() { return getToken(EqlParser.EQ_, 0); }
		public CharacterSet_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_characterSet_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterCharacterSet_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitCharacterSet_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitCharacterSet_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final CharacterSet_Context characterSet_() throws RecognitionException {
		CharacterSet_Context _localctx = new CharacterSet_Context(_ctx, getState());
		enterRule(_localctx, 234, RULE_characterSet_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1295);
			_la = _input.LA(1);
			if ( !(_la==CHAR || _la==CHARACTER) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(1296);
			match(SET);
			setState(1298);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EQ_) {
				{
				setState(1297);
				match(EQ_);
				}
			}

			setState(1300);
			((CharacterSet_Context)_localctx).characterSet = match(STRING);
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
	public static class CollateClause_Context extends ParserRuleContext {
		public Token collate;
		public TerminalNode COLLATE() { return getToken(EqlParser.COLLATE, 0); }
		public TerminalNode STRING_() { return getToken(EqlParser.STRING_, 0); }
		public TerminalNode EQ_() { return getToken(EqlParser.EQ_, 0); }
		public CollateClause_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_collateClause_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).enterCollateClause_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof EqlListener ) ((EqlListener)listener).exitCollateClause_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof EqlVisitor ) return ((EqlVisitor<? extends T>)visitor).visitCollateClause_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final CollateClause_Context collateClause_() throws RecognitionException {
		CollateClause_Context _localctx = new CollateClause_Context(_ctx, getState());
		enterRule(_localctx, 236, RULE_collateClause_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1302);
			match(COLLATE);
			setState(1304);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EQ_) {
				{
				setState(1303);
				match(EQ_);
				}
			}

			setState(1306);
			((CollateClause_Context)_localctx).collate = match(STRING_);
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
		case 18:
			return sqlSelect_sempred((SqlSelectContext)_localctx, predIndex);
		case 29:
			return sqlTableSource_sempred((SqlTableSourceContext)_localctx, predIndex);
		case 70:
			return sqlExpr_sempred((SqlExprContext)_localctx, predIndex);
		case 71:
			return sqlExpr_primary_sempred((SqlExpr_primaryContext)_localctx, predIndex);
		case 75:
			return sqlExpr_bit_sempred((SqlExpr_bitContext)_localctx, predIndex);
		}
		return true;
	}
	private boolean sqlSelect_sempred(SqlSelectContext _localctx, int predIndex) {
		switch (predIndex) {
		case 0:
			return precpred(_ctx, 1);
		}
		return true;
	}
	private boolean sqlTableSource_sempred(SqlTableSourceContext _localctx, int predIndex) {
		switch (predIndex) {
		case 1:
			return precpred(_ctx, 1);
		}
		return true;
	}
	private boolean sqlExpr_sempred(SqlExprContext _localctx, int predIndex) {
		switch (predIndex) {
		case 2:
			return precpred(_ctx, 3);
		case 3:
			return precpred(_ctx, 2);
		}
		return true;
	}
	private boolean sqlExpr_primary_sempred(SqlExpr_primaryContext _localctx, int predIndex) {
		switch (predIndex) {
		case 4:
			return precpred(_ctx, 11);
		case 5:
			return precpred(_ctx, 10);
		case 6:
			return precpred(_ctx, 9);
		case 7:
			return precpred(_ctx, 8);
		case 8:
			return precpred(_ctx, 7);
		case 9:
			return precpred(_ctx, 6);
		case 10:
			return precpred(_ctx, 5);
		case 11:
			return precpred(_ctx, 4);
		case 12:
			return precpred(_ctx, 3);
		case 13:
			return precpred(_ctx, 2);
		}
		return true;
	}
	private boolean sqlExpr_bit_sempred(SqlExpr_bitContext _localctx, int predIndex) {
		switch (predIndex) {
		case 14:
			return precpred(_ctx, 8);
		case 15:
			return precpred(_ctx, 7);
		case 16:
			return precpred(_ctx, 6);
		case 17:
			return precpred(_ctx, 5);
		case 18:
			return precpred(_ctx, 4);
		case 19:
			return precpred(_ctx, 3);
		}
		return true;
	}

	public static final String _serializedATN =
		"\u0004\u0001\u016d\u051d\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001"+
		"\u0002\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004"+
		"\u0002\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007"+
		"\u0002\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002\u000b\u0007\u000b"+
		"\u0002\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e\u0002\u000f\u0007"+
		"\u000f\u0002\u0010\u0007\u0010\u0002\u0011\u0007\u0011\u0002\u0012\u0007"+
		"\u0012\u0002\u0013\u0007\u0013\u0002\u0014\u0007\u0014\u0002\u0015\u0007"+
		"\u0015\u0002\u0016\u0007\u0016\u0002\u0017\u0007\u0017\u0002\u0018\u0007"+
		"\u0018\u0002\u0019\u0007\u0019\u0002\u001a\u0007\u001a\u0002\u001b\u0007"+
		"\u001b\u0002\u001c\u0007\u001c\u0002\u001d\u0007\u001d\u0002\u001e\u0007"+
		"\u001e\u0002\u001f\u0007\u001f\u0002 \u0007 \u0002!\u0007!\u0002\"\u0007"+
		"\"\u0002#\u0007#\u0002$\u0007$\u0002%\u0007%\u0002&\u0007&\u0002\'\u0007"+
		"\'\u0002(\u0007(\u0002)\u0007)\u0002*\u0007*\u0002+\u0007+\u0002,\u0007"+
		",\u0002-\u0007-\u0002.\u0007.\u0002/\u0007/\u00020\u00070\u00021\u0007"+
		"1\u00022\u00072\u00023\u00073\u00024\u00074\u00025\u00075\u00026\u0007"+
		"6\u00027\u00077\u00028\u00078\u00029\u00079\u0002:\u0007:\u0002;\u0007"+
		";\u0002<\u0007<\u0002=\u0007=\u0002>\u0007>\u0002?\u0007?\u0002@\u0007"+
		"@\u0002A\u0007A\u0002B\u0007B\u0002C\u0007C\u0002D\u0007D\u0002E\u0007"+
		"E\u0002F\u0007F\u0002G\u0007G\u0002H\u0007H\u0002I\u0007I\u0002J\u0007"+
		"J\u0002K\u0007K\u0002L\u0007L\u0002M\u0007M\u0002N\u0007N\u0002O\u0007"+
		"O\u0002P\u0007P\u0002Q\u0007Q\u0002R\u0007R\u0002S\u0007S\u0002T\u0007"+
		"T\u0002U\u0007U\u0002V\u0007V\u0002W\u0007W\u0002X\u0007X\u0002Y\u0007"+
		"Y\u0002Z\u0007Z\u0002[\u0007[\u0002\\\u0007\\\u0002]\u0007]\u0002^\u0007"+
		"^\u0002_\u0007_\u0002`\u0007`\u0002a\u0007a\u0002b\u0007b\u0002c\u0007"+
		"c\u0002d\u0007d\u0002e\u0007e\u0002f\u0007f\u0002g\u0007g\u0002h\u0007"+
		"h\u0002i\u0007i\u0002j\u0007j\u0002k\u0007k\u0002l\u0007l\u0002m\u0007"+
		"m\u0002n\u0007n\u0002o\u0007o\u0002p\u0007p\u0002q\u0007q\u0002r\u0007"+
		"r\u0002s\u0007s\u0002t\u0007t\u0002u\u0007u\u0002v\u0007v\u0001\u0000"+
		"\u0001\u0000\u0001\u0001\u0001\u0001\u0001\u0001\u0005\u0001\u00f4\b\u0001"+
		"\n\u0001\f\u0001\u00f7\t\u0001\u0001\u0001\u0003\u0001\u00fa\b\u0001\u0001"+
		"\u0002\u0001\u0002\u0003\u0002\u00fe\b\u0002\u0001\u0003\u0001\u0003\u0001"+
		"\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0003\u0003\u0106\b\u0003\u0001"+
		"\u0004\u0001\u0004\u0003\u0004\u010a\b\u0004\u0001\u0005\u0001\u0005\u0001"+
		"\u0006\u0001\u0006\u0001\u0007\u0003\u0007\u0111\b\u0007\u0001\u0007\u0001"+
		"\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001"+
		"\u0007\u0003\u0007\u011b\b\u0007\u0001\b\u0003\b\u011e\b\b\u0001\b\u0001"+
		"\b\u0001\b\u0001\b\u0001\b\u0001\b\u0001\b\u0001\b\u0001\b\u0001\b\u0001"+
		"\b\u0001\b\u0001\b\u0001\b\u0001\b\u0001\b\u0001\t\u0003\t\u0131\b\t\u0001"+
		"\t\u0001\t\u0001\t\u0003\t\u0136\b\t\u0001\t\u0003\t\u0139\b\t\u0001\t"+
		"\u0001\t\u0003\t\u013d\b\t\u0001\t\u0001\t\u0001\t\u0003\t\u0142\b\t\u0003"+
		"\t\u0144\b\t\u0001\n\u0001\n\u0001\n\u0001\n\u0005\n\u014a\b\n\n\n\f\n"+
		"\u014d\t\n\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\f\u0001"+
		"\f\u0001\r\u0001\r\u0001\r\u0001\r\u0001\r\u0005\r\u015a\b\r\n\r\f\r\u015d"+
		"\t\r\u0001\r\u0001\r\u0001\u000e\u0003\u000e\u0162\b\u000e\u0001\u000e"+
		"\u0001\u000e\u0001\u000e\u0001\u000e\u0003\u000e\u0168\b\u000e\u0001\u000e"+
		"\u0003\u000e\u016b\b\u000e\u0001\u000e\u0003\u000e\u016e\b\u000e\u0001"+
		"\u000f\u0003\u000f\u0171\b\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0001"+
		"\u0010\u0003\u0010\u0177\b\u0010\u0001\u0010\u0001\u0010\u0001\u0010\u0001"+
		"\u0010\u0001\u0010\u0001\u0010\u0001\u0011\u0001\u0011\u0001\u0011\u0001"+
		"\u0011\u0005\u0011\u0183\b\u0011\n\u0011\f\u0011\u0186\t\u0011\u0001\u0012"+
		"\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012\u0003\u0012"+
		"\u018e\b\u0012\u0001\u0012\u0001\u0012\u0003\u0012\u0192\b\u0012\u0001"+
		"\u0012\u0001\u0012\u0001\u0012\u0005\u0012\u0197\b\u0012\n\u0012\f\u0012"+
		"\u019a\t\u0012\u0001\u0013\u0001\u0013\u0003\u0013\u019e\b\u0013\u0001"+
		"\u0014\u0003\u0014\u01a1\b\u0014\u0001\u0014\u0001\u0014\u0003\u0014\u01a5"+
		"\b\u0014\u0001\u0014\u0001\u0014\u0003\u0014\u01a9\b\u0014\u0001\u0014"+
		"\u0003\u0014\u01ac\b\u0014\u0001\u0014\u0003\u0014\u01af\b\u0014\u0001"+
		"\u0014\u0003\u0014\u01b2\b\u0014\u0001\u0014\u0003\u0014\u01b5\b\u0014"+
		"\u0001\u0014\u0003\u0014\u01b8\b\u0014\u0001\u0014\u0003\u0014\u01bb\b"+
		"\u0014\u0001\u0014\u0003\u0014\u01be\b\u0014\u0001\u0014\u0001\u0014\u0001"+
		"\u0014\u0003\u0014\u01c3\b\u0014\u0003\u0014\u01c5\b\u0014\u0001\u0015"+
		"\u0001\u0015\u0001\u0015\u0005\u0015\u01ca\b\u0015\n\u0015\f\u0015\u01cd"+
		"\t\u0015\u0001\u0016\u0001\u0016\u0003\u0016\u01d1\b\u0016\u0001\u0017"+
		"\u0001\u0017\u0003\u0017\u01d5\b\u0017\u0001\u0017\u0003\u0017\u01d8\b"+
		"\u0017\u0001\u0017\u0003\u0017\u01db\b\u0017\u0001\u0018\u0001\u0018\u0001"+
		"\u0018\u0001\u0019\u0001\u0019\u0001\u001a\u0001\u001a\u0003\u001a\u01e4"+
		"\b\u001a\u0001\u001b\u0001\u001b\u0003\u001b\u01e8\b\u001b\u0001\u001b"+
		"\u0001\u001b\u0001\u001c\u0001\u001c\u0001\u001c\u0005\u001c\u01ef\b\u001c"+
		"\n\u001c\f\u001c\u01f2\t\u001c\u0001\u001d\u0001\u001d\u0001\u001d\u0001"+
		"\u001d\u0001\u001d\u0001\u001d\u0001\u001d\u0003\u001d\u01fb\b\u001d\u0001"+
		"\u001d\u0001\u001d\u0001\u001d\u0001\u001d\u0001\u001d\u0003\u001d\u0202"+
		"\b\u001d\u0005\u001d\u0204\b\u001d\n\u001d\f\u001d\u0207\t\u001d\u0001"+
		"\u001e\u0001\u001e\u0003\u001e\u020b\b\u001e\u0001\u001e\u0003\u001e\u020e"+
		"\b\u001e\u0001\u001e\u0003\u001e\u0211\b\u001e\u0001\u001f\u0001\u001f"+
		"\u0001\u001f\u0001\u001f\u0001\u001f\u0001\u001f\u0001\u001f\u0001\u001f"+
		"\u0001\u001f\u0003\u001f\u021c\b\u001f\u0001\u001f\u0003\u001f\u021f\b"+
		"\u001f\u0001\u001f\u0003\u001f\u0222\b\u001f\u0001 \u0001 \u0001 \u0001"+
		" \u0001 \u0001 \u0001 \u0001 \u0001 \u0001 \u0001 \u0003 \u022f\b \u0001"+
		" \u0003 \u0232\b \u0001 \u0003 \u0235\b \u0001!\u0001!\u0001!\u0001!\u0001"+
		"!\u0001!\u0001!\u0001!\u0001!\u0003!\u0240\b!\u0001!\u0003!\u0243\b!\u0001"+
		"!\u0003!\u0246\b!\u0001\"\u0003\"\u0249\b\"\u0001\"\u0001\"\u0001\"\u0001"+
		"\"\u0003\"\u024f\b\"\u0001\"\u0001\"\u0001#\u0001#\u0001#\u0001#\u0001"+
		"#\u0003#\u0258\b#\u0001#\u0001#\u0003#\u025c\b#\u0001$\u0001$\u0001$\u0005"+
		"$\u0261\b$\n$\f$\u0264\t$\u0001%\u0001%\u0001%\u0001%\u0005%\u026a\b%"+
		"\n%\f%\u026d\t%\u0001%\u0001%\u0001&\u0001&\u0001&\u0001&\u0001&\u0003"+
		"&\u0276\b&\u0001\'\u0001\'\u0003\'\u027a\b\'\u0001(\u0003(\u027d\b(\u0001"+
		"(\u0001(\u0001)\u0001)\u0003)\u0283\b)\u0001)\u0001)\u0001*\u0001*\u0003"+
		"*\u0289\b*\u0001*\u0001*\u0001+\u0001+\u0003+\u028f\b+\u0001+\u0001+\u0001"+
		",\u0001,\u0001,\u0001-\u0001-\u0001-\u0001.\u0001.\u0001.\u0001.\u0001"+
		"/\u0001/\u0001/\u0005/\u02a0\b/\n/\f/\u02a3\t/\u00010\u00010\u00010\u0001"+
		"1\u00011\u00011\u00011\u00031\u02ac\b1\u00011\u00011\u00011\u00011\u0001"+
		"1\u00011\u00011\u00011\u00011\u00011\u00011\u00011\u00011\u00011\u0001"+
		"1\u00031\u02bd\b1\u00012\u00012\u00032\u02c1\b2\u00013\u00013\u00033\u02c5"+
		"\b3\u00014\u00014\u00014\u00014\u00015\u00015\u00015\u00016\u00016\u0001"+
		"6\u00017\u00017\u00018\u00018\u00018\u00018\u00018\u00018\u00018\u0003"+
		"8\u02da\b8\u00019\u00019\u0001:\u0001:\u0001;\u0001;\u0001;\u0001<\u0001"+
		"<\u0001=\u0001=\u0001>\u0001>\u0001?\u0001?\u0001@\u0001@\u0003@\u02ed"+
		"\b@\u0001A\u0001A\u0001B\u0001B\u0001B\u0003B\u02f4\bB\u0001B\u0001B\u0001"+
		"C\u0001C\u0001C\u0003C\u02fb\bC\u0001C\u0001C\u0001D\u0001D\u0001D\u0003"+
		"D\u0302\bD\u0001E\u0001E\u0001E\u0005E\u0307\bE\nE\fE\u030a\tE\u0001F"+
		"\u0001F\u0001F\u0001F\u0003F\u0310\bF\u0001F\u0001F\u0001F\u0001F\u0001"+
		"F\u0001F\u0005F\u0318\bF\nF\fF\u031b\tF\u0001G\u0001G\u0001G\u0001G\u0001"+
		"G\u0001G\u0003G\u0323\bG\u0001G\u0001G\u0001G\u0001G\u0001G\u0001G\u0001"+
		"G\u0001G\u0001G\u0001G\u0001G\u0001G\u0001G\u0001G\u0001G\u0003G\u0334"+
		"\bG\u0001G\u0001G\u0001G\u0001G\u0001G\u0001G\u0001G\u0001G\u0001G\u0001"+
		"G\u0001G\u0001G\u0001G\u0001G\u0001G\u0001G\u0001G\u0001G\u0001G\u0001"+
		"G\u0001G\u0001G\u0001G\u0001G\u0005G\u034e\bG\nG\fG\u0351\tG\u0001H\u0001"+
		"H\u0001I\u0001I\u0003I\u0357\bI\u0001I\u0001I\u0001I\u0001I\u0001I\u0003"+
		"I\u035e\bI\u0001I\u0001I\u0001I\u0001I\u0001I\u0001I\u0001I\u0003I\u0367"+
		"\bI\u0001I\u0001I\u0001I\u0001I\u0001I\u0001I\u0001I\u0003I\u0370\bI\u0001"+
		"I\u0001I\u0001I\u0001I\u0003I\u0376\bI\u0001I\u0001I\u0003I\u037a\bI\u0001"+
		"I\u0001I\u0001I\u0001I\u0003I\u0380\bI\u0001I\u0003I\u0383\bI\u0001J\u0001"+
		"J\u0001J\u0005J\u0388\bJ\nJ\fJ\u038b\tJ\u0001K\u0001K\u0001K\u0001K\u0001"+
		"K\u0001K\u0001K\u0001K\u0003K\u0395\bK\u0001K\u0001K\u0001K\u0001K\u0001"+
		"K\u0001K\u0001K\u0001K\u0001K\u0001K\u0001K\u0001K\u0001K\u0001K\u0001"+
		"K\u0001K\u0001K\u0001K\u0005K\u03a9\bK\nK\fK\u03ac\tK\u0001L\u0001L\u0001"+
		"L\u0001L\u0001L\u0001L\u0001L\u0001L\u0001L\u0001L\u0001L\u0001L\u0003"+
		"L\u03ba\bL\u0001M\u0001M\u0001M\u0001N\u0001N\u0001N\u0001N\u0001O\u0001"+
		"O\u0001O\u0001O\u0001P\u0001P\u0001P\u0001Q\u0001Q\u0001Q\u0003Q\u03cd"+
		"\bQ\u0001R\u0001R\u0001R\u0003R\u03d2\bR\u0001R\u0001R\u0003R\u03d6\b"+
		"R\u0001R\u0001R\u0001S\u0001S\u0001S\u0001S\u0003S\u03de\bS\u0001S\u0003"+
		"S\u03e1\bS\u0001S\u0003S\u03e4\bS\u0001S\u0001S\u0003S\u03e8\bS\u0001"+
		"T\u0001T\u0003T\u03ec\bT\u0001U\u0001U\u0001U\u0001U\u0001V\u0001V\u0001"+
		"V\u0005V\u03f5\bV\nV\fV\u03f8\tV\u0001W\u0001W\u0003W\u03fc\bW\u0001W"+
		"\u0001W\u0001W\u0003W\u0401\bW\u0001X\u0001X\u0001Y\u0003Y\u0406\bY\u0001"+
		"Y\u0001Y\u0001Z\u0001Z\u0001Z\u0001Z\u0001Z\u0001Z\u0001Z\u0001Z\u0003"+
		"Z\u0412\bZ\u0001[\u0001[\u0001[\u0001\\\u0001\\\u0001\\\u0005\\\u041a"+
		"\b\\\n\\\f\\\u041d\t\\\u0001]\u0001]\u0001]\u0001]\u0003]\u0423\b]\u0001"+
		"]\u0003]\u0426\b]\u0001]\u0003]\u0429\b]\u0001]\u0001]\u0001^\u0001^\u0001"+
		"_\u0001_\u0001`\u0001`\u0001`\u0005`\u0434\b`\n`\f`\u0437\t`\u0001a\u0001"+
		"a\u0001a\u0001a\u0001a\u0001a\u0001a\u0001a\u0001a\u0001a\u0001a\u0001"+
		"a\u0001a\u0001a\u0001a\u0003a\u0448\ba\u0001a\u0001a\u0001a\u0001a\u0001"+
		"a\u0001a\u0001a\u0001a\u0001a\u0001a\u0001a\u0001a\u0001a\u0001a\u0001"+
		"a\u0001a\u0001a\u0001a\u0001a\u0001a\u0001a\u0001a\u0001a\u0001a\u0001"+
		"a\u0001a\u0001a\u0001a\u0001a\u0001a\u0001a\u0003a\u0469\ba\u0001b\u0001"+
		"b\u0001c\u0001c\u0001d\u0001d\u0001d\u0001d\u0003d\u0473\bd\u0001d\u0001"+
		"d\u0001d\u0001d\u0001e\u0001e\u0001e\u0003e\u047c\be\u0001e\u0001e\u0001"+
		"f\u0001f\u0001f\u0001f\u0001f\u0001f\u0001f\u0001f\u0001f\u0001f\u0003"+
		"f\u048a\bf\u0001g\u0001g\u0001g\u0005g\u048f\bg\ng\fg\u0492\tg\u0001h"+
		"\u0001h\u0001h\u0001h\u0003h\u0498\bh\u0001h\u0003h\u049b\bh\u0001i\u0001"+
		"i\u0001i\u0005i\u04a0\bi\ni\fi\u04a3\ti\u0001j\u0001j\u0003j\u04a7\bj"+
		"\u0001j\u0001j\u0001j\u0003j\u04ac\bj\u0001j\u0001j\u0001k\u0004k\u04b1"+
		"\bk\u000bk\fk\u04b2\u0001l\u0001l\u0001l\u0001l\u0001l\u0001m\u0001m\u0001"+
		"m\u0001m\u0001m\u0001m\u0003m\u04c0\bm\u0001n\u0001n\u0001o\u0001o\u0001"+
		"o\u0001o\u0001p\u0001p\u0001p\u0005p\u04cb\bp\np\fp\u04ce\tp\u0001q\u0001"+
		"q\u0003q\u04d2\bq\u0001q\u0001q\u0003q\u04d6\bq\u0001r\u0001r\u0001r\u0003"+
		"r\u04db\br\u0001r\u0001r\u0001r\u0001r\u0003r\u04e1\br\u0001r\u0001r\u0003"+
		"r\u04e5\br\u0001s\u0001s\u0001s\u0001s\u0001s\u0003s\u04ec\bs\u0001s\u0003"+
		"s\u04ef\bs\u0001s\u0003s\u04f2\bs\u0001s\u0003s\u04f5\bs\u0001t\u0001"+
		"t\u0001t\u0001t\u0001t\u0001t\u0001t\u0001t\u0001t\u0001t\u0001t\u0001"+
		"t\u0001t\u0001t\u0001t\u0001t\u0001t\u0001t\u0001t\u0001t\u0001t\u0001"+
		"t\u0001t\u0003t\u050e\bt\u0001u\u0001u\u0001u\u0003u\u0513\bu\u0001u\u0001"+
		"u\u0001v\u0001v\u0003v\u0519\bv\u0001v\u0001v\u0001v\u0000\u0005$:\u008c"+
		"\u008e\u0096w\u0000\u0002\u0004\u0006\b\n\f\u000e\u0010\u0012\u0014\u0016"+
		"\u0018\u001a\u001c\u001e \"$&(*,.02468:<>@BDFHJLNPRTVXZ\\^`bdfhjlnprt"+
		"vxz|~\u0080\u0082\u0084\u0086\u0088\u008a\u008c\u008e\u0090\u0092\u0094"+
		"\u0096\u0098\u009a\u009c\u009e\u00a0\u00a2\u00a4\u00a6\u00a8\u00aa\u00ac"+
		"\u00ae\u00b0\u00b2\u00b4\u00b6\u00b8\u00ba\u00bc\u00be\u00c0\u00c2\u00c4"+
		"\u00c6\u00c8\u00ca\u00cc\u00ce\u00d0\u00d2\u00d4\u00d6\u00d8\u00da\u00dc"+
		"\u00de\u00e0\u00e2\u00e4\u00e6\u00e8\u00ea\u00ec\u0000\u0014\u0003\u0000"+
		"II\u010a\u010a\u011f\u011f\u0001\u0000\u0081\u0083\u0001\u0000fg&\u0000"+
		"@@VVXXopww\u0081\u0081\u0083\u0083\u0086\u0088\u008a\u008d\u0092\u0092"+
		"\u009d\u009d\u00a4\u00a4\u00aa\u00aa\u00ad\u00ad\u00b1\u00b2\u00b4\u00e1"+
		"\u00ea\u00ea\u00f1\u00f1\u00f9\u00f9\u0107\u0107\u0112\u0112\u0118\u0118"+
		"\u011b\u011b\u011e\u011e\u0121\u0121\u0123\u0123\u0125\u0126\u012e\u012e"+
		"\u0136\u0136\u013b\u013b\u0140\u0140\u0143\u0144\u014f\u0150\u0157\u0157"+
		"\u0159\u0159\u015b\u015c\u015f\u0160\u0162\u0167\u0002\u0000\u0003\u0003"+
		"dd\u0002\u0000\u0001\u0001aa\u0002\u0000kl\u0148\u0148\u0001\u0000\u0015"+
		"\u001a\u0002\u0000\n\n\u000e\u000f\u0001\u0000\f\r\u0001\u0000\u0007\b"+
		"\u0002\u0000\u0003\u0004\f\r\u0003\u0000\u0118\u0118\u013b\u013b\u0141"+
		"\u0141\u0001\u0000\u008f\u0093\u0003\u0000\u00eb\u00eb\u0124\u0124\u0153"+
		"\u0153\u0002\u0000\u0086\u0088\u008a\u008d\u0001\u0000\u0086\u008e\u0001"+
		"\u0000rs\u0002\u0000\u0111\u0111\u0122\u0122\u0001\u0000}~\u0586\u0000"+
		"\u00ee\u0001\u0000\u0000\u0000\u0002\u00f0\u0001\u0000\u0000\u0000\u0004"+
		"\u00fd\u0001\u0000\u0000\u0000\u0006\u0105\u0001\u0000\u0000\u0000\b\u0109"+
		"\u0001\u0000\u0000\u0000\n\u010b\u0001\u0000\u0000\u0000\f\u010d\u0001"+
		"\u0000\u0000\u0000\u000e\u0110\u0001\u0000\u0000\u0000\u0010\u011d\u0001"+
		"\u0000\u0000\u0000\u0012\u0130\u0001\u0000\u0000\u0000\u0014\u0145\u0001"+
		"\u0000\u0000\u0000\u0016\u014e\u0001\u0000\u0000\u0000\u0018\u0152\u0001"+
		"\u0000\u0000\u0000\u001a\u0154\u0001\u0000\u0000\u0000\u001c\u0161\u0001"+
		"\u0000\u0000\u0000\u001e\u0170\u0001\u0000\u0000\u0000 \u0176\u0001\u0000"+
		"\u0000\u0000\"\u017e\u0001\u0000\u0000\u0000$\u018d\u0001\u0000\u0000"+
		"\u0000&\u019b\u0001\u0000\u0000\u0000(\u01a0\u0001\u0000\u0000\u0000*"+
		"\u01c6\u0001\u0000\u0000\u0000,\u01d0\u0001\u0000\u0000\u0000.\u01d2\u0001"+
		"\u0000\u0000\u00000\u01dc\u0001\u0000\u0000\u00002\u01df\u0001\u0000\u0000"+
		"\u00004\u01e3\u0001\u0000\u0000\u00006\u01e5\u0001\u0000\u0000\u00008"+
		"\u01eb\u0001\u0000\u0000\u0000:\u01fa\u0001\u0000\u0000\u0000<\u0208\u0001"+
		"\u0000\u0000\u0000>\u0212\u0001\u0000\u0000\u0000@\u0223\u0001\u0000\u0000"+
		"\u0000B\u0236\u0001\u0000\u0000\u0000D\u0248\u0001\u0000\u0000\u0000F"+
		"\u0252\u0001\u0000\u0000\u0000H\u025d\u0001\u0000\u0000\u0000J\u0265\u0001"+
		"\u0000\u0000\u0000L\u0275\u0001\u0000\u0000\u0000N\u0279\u0001\u0000\u0000"+
		"\u0000P\u027c\u0001\u0000\u0000\u0000R\u0280\u0001\u0000\u0000\u0000T"+
		"\u0286\u0001\u0000\u0000\u0000V\u028c\u0001\u0000\u0000\u0000X\u0292\u0001"+
		"\u0000\u0000\u0000Z\u0295\u0001\u0000\u0000\u0000\\\u0298\u0001\u0000"+
		"\u0000\u0000^\u029c\u0001\u0000\u0000\u0000`\u02a4\u0001\u0000\u0000\u0000"+
		"b\u02bc\u0001\u0000\u0000\u0000d\u02c0\u0001\u0000\u0000\u0000f\u02c4"+
		"\u0001\u0000\u0000\u0000h\u02c6\u0001\u0000\u0000\u0000j\u02ca\u0001\u0000"+
		"\u0000\u0000l\u02cd\u0001\u0000\u0000\u0000n\u02d0\u0001\u0000\u0000\u0000"+
		"p\u02d9\u0001\u0000\u0000\u0000r\u02db\u0001\u0000\u0000\u0000t\u02dd"+
		"\u0001\u0000\u0000\u0000v\u02df\u0001\u0000\u0000\u0000x\u02e2\u0001\u0000"+
		"\u0000\u0000z\u02e4\u0001\u0000\u0000\u0000|\u02e6\u0001\u0000\u0000\u0000"+
		"~\u02e8\u0001\u0000\u0000\u0000\u0080\u02ec\u0001\u0000\u0000\u0000\u0082"+
		"\u02ee\u0001\u0000\u0000\u0000\u0084\u02f3\u0001\u0000\u0000\u0000\u0086"+
		"\u02fa\u0001\u0000\u0000\u0000\u0088\u02fe\u0001\u0000\u0000\u0000\u008a"+
		"\u0303\u0001\u0000\u0000\u0000\u008c\u030f\u0001\u0000\u0000\u0000\u008e"+
		"\u031c\u0001\u0000\u0000\u0000\u0090\u0352\u0001\u0000\u0000\u0000\u0092"+
		"\u0382\u0001\u0000\u0000\u0000\u0094\u0384\u0001\u0000\u0000\u0000\u0096"+
		"\u0394\u0001\u0000\u0000\u0000\u0098\u03b9\u0001\u0000\u0000\u0000\u009a"+
		"\u03bb\u0001\u0000\u0000\u0000\u009c\u03be\u0001\u0000\u0000\u0000\u009e"+
		"\u03c2\u0001\u0000\u0000\u0000\u00a0\u03c6\u0001\u0000\u0000\u0000\u00a2"+
		"\u03cc\u0001\u0000\u0000\u0000\u00a4\u03ce\u0001\u0000\u0000\u0000\u00a6"+
		"\u03d9\u0001\u0000\u0000\u0000\u00a8\u03eb\u0001\u0000\u0000\u0000\u00aa"+
		"\u03ed\u0001\u0000\u0000\u0000\u00ac\u03f1\u0001\u0000\u0000\u0000\u00ae"+
		"\u03f9\u0001\u0000\u0000\u0000\u00b0\u0402\u0001\u0000\u0000\u0000\u00b2"+
		"\u0405\u0001\u0000\u0000\u0000\u00b4\u0411\u0001\u0000\u0000\u0000\u00b6"+
		"\u0413\u0001\u0000\u0000\u0000\u00b8\u0416\u0001\u0000\u0000\u0000\u00ba"+
		"\u041e\u0001\u0000\u0000\u0000\u00bc\u042c\u0001\u0000\u0000\u0000\u00be"+
		"\u042e\u0001\u0000\u0000\u0000\u00c0\u0430\u0001\u0000\u0000\u0000\u00c2"+
		"\u0468\u0001\u0000\u0000\u0000\u00c4\u046a\u0001\u0000\u0000\u0000\u00c6"+
		"\u046c\u0001\u0000\u0000\u0000\u00c8\u046e\u0001\u0000\u0000\u0000\u00ca"+
		"\u0478\u0001\u0000\u0000\u0000\u00cc\u0489\u0001\u0000\u0000\u0000\u00ce"+
		"\u048b\u0001\u0000\u0000\u0000\u00d0\u0493\u0001\u0000\u0000\u0000\u00d2"+
		"\u049c\u0001\u0000\u0000\u0000\u00d4\u04a4\u0001\u0000\u0000\u0000\u00d6"+
		"\u04b0\u0001\u0000\u0000\u0000\u00d8\u04b4\u0001\u0000\u0000\u0000\u00da"+
		"\u04bf\u0001\u0000\u0000\u0000\u00dc\u04c1\u0001\u0000\u0000\u0000\u00de"+
		"\u04c3\u0001\u0000\u0000\u0000\u00e0\u04c7\u0001\u0000\u0000\u0000\u00e2"+
		"\u04cf\u0001\u0000\u0000\u0000\u00e4\u04e4\u0001\u0000\u0000\u0000\u00e6"+
		"\u04e6\u0001\u0000\u0000\u0000\u00e8\u050d\u0001\u0000\u0000\u0000\u00ea"+
		"\u050f\u0001\u0000\u0000\u0000\u00ec\u0516\u0001\u0000\u0000\u0000\u00ee"+
		"\u00ef\u0003\u0002\u0001\u0000\u00ef\u0001\u0001\u0000\u0000\u0000\u00f0"+
		"\u00f5\u0003\u0004\u0002\u0000\u00f1\u00f2\u0005\'\u0000\u0000\u00f2\u00f4"+
		"\u0003\u0004\u0002\u0000\u00f3\u00f1\u0001\u0000\u0000\u0000\u00f4\u00f7"+
		"\u0001\u0000\u0000\u0000\u00f5\u00f3\u0001\u0000\u0000\u0000\u00f5\u00f6"+
		"\u0001\u0000\u0000\u0000\u00f6\u00f9\u0001\u0000\u0000\u0000\u00f7\u00f5"+
		"\u0001\u0000\u0000\u0000\u00f8\u00fa\u0005\'\u0000\u0000\u00f9\u00f8\u0001"+
		"\u0000\u0000\u0000\u00f9\u00fa\u0001\u0000\u0000\u0000\u00fa\u0003\u0001"+
		"\u0000\u0000\u0000\u00fb\u00fe\u0003\u0006\u0003\u0000\u00fc\u00fe\u0003"+
		"\b\u0004\u0000\u00fd\u00fb\u0001\u0000\u0000\u0000\u00fd\u00fc\u0001\u0000"+
		"\u0000\u0000\u00fe\u0005\u0001\u0000\u0000\u0000\u00ff\u0106\u0003\u001e"+
		"\u000f\u0000\u0100\u0106\u0003$\u0012\u0000\u0101\u0106\u0003\u000e\u0007"+
		"\u0000\u0102\u0106\u0003\u0010\b\u0000\u0103\u0106\u0003\u0012\t\u0000"+
		"\u0104\u0106\u0003\u001c\u000e\u0000\u0105\u00ff\u0001\u0000\u0000\u0000"+
		"\u0105\u0100\u0001\u0000\u0000\u0000\u0105\u0101\u0001\u0000\u0000\u0000"+
		"\u0105\u0102\u0001\u0000\u0000\u0000\u0105\u0103\u0001\u0000\u0000\u0000"+
		"\u0105\u0104\u0001\u0000\u0000\u0000\u0106\u0007\u0001\u0000\u0000\u0000"+
		"\u0107\u010a\u0003\n\u0005\u0000\u0108\u010a\u0003\f\u0006\u0000\u0109"+
		"\u0107\u0001\u0000\u0000\u0000\u0109\u0108\u0001\u0000\u0000\u0000\u010a"+
		"\t\u0001\u0000\u0000\u0000\u010b\u010c\u0005x\u0000\u0000\u010c\u000b"+
		"\u0001\u0000\u0000\u0000\u010d\u010e\u0005y\u0000\u0000\u010e\r\u0001"+
		"\u0000\u0000\u0000\u010f\u0111\u0003\u00ceg\u0000\u0110\u010f\u0001\u0000"+
		"\u0000\u0000\u0110\u0111\u0001\u0000\u0000\u0000\u0111\u0112\u0001\u0000"+
		"\u0000\u0000\u0112\u0113\u0005,\u0000\u0000\u0113\u0114\u0005F\u0000\u0000"+
		"\u0114\u0115\u0003\u0084B\u0000\u0115\u0116\u0005\u001c\u0000\u0000\u0116"+
		"\u0117\u0003\u008aE\u0000\u0117\u011a\u0005\u001d\u0000\u0000\u0118\u011b"+
		"\u0003\u0018\f\u0000\u0119\u011b\u0003$\u0012\u0000\u011a\u0118\u0001"+
		"\u0000\u0000\u0000\u011a\u0119\u0001\u0000\u0000\u0000\u011b\u000f\u0001"+
		"\u0000\u0000\u0000\u011c\u011e\u0003\u00ceg\u0000\u011d\u011c\u0001\u0000"+
		"\u0000\u0000\u011d\u011e\u0001\u0000\u0000\u0000\u011e\u011f\u0001\u0000"+
		"\u0000\u0000\u011f\u0120\u0005\u00b1\u0000\u0000\u0120\u0121\u0005F\u0000"+
		"\u0000\u0121\u0122\u0003\u0084B\u0000\u0122\u0123\u0005\u001c\u0000\u0000"+
		"\u0123\u0124\u0003\u008aE\u0000\u0124\u0125\u0005\u001d\u0000\u0000\u0125"+
		"\u0126\u0003\u0018\f\u0000\u0126\u0127\u0005[\u0000\u0000\u0127\u0128"+
		"\u0005\u00b2\u0000\u0000\u0128\u0129\u0005\u001c\u0000\u0000\u0129\u012a"+
		"\u0003\u008aE\u0000\u012a\u012b\u0005\u001d\u0000\u0000\u012b\u012c\u0005"+
		"\u009b\u0000\u0000\u012c\u012d\u0005-\u0000\u0000\u012d\u012e\u0003\u0014"+
		"\n\u0000\u012e\u0011\u0001\u0000\u0000\u0000\u012f\u0131\u0003\u00ceg"+
		"\u0000\u0130\u012f\u0001\u0000\u0000\u0000\u0130\u0131\u0001\u0000\u0000"+
		"\u0000\u0131\u0132\u0001\u0000\u0000\u0000\u0132\u0133\u0005-\u0000\u0000"+
		"\u0133\u0135\u0003\u0084B\u0000\u0134\u0136\u0005Z\u0000\u0000\u0135\u0134"+
		"\u0001\u0000\u0000\u0000\u0135\u0136\u0001\u0000\u0000\u0000\u0136\u0138"+
		"\u0001\u0000\u0000\u0000\u0137\u0139\u00032\u0019\u0000\u0138\u0137\u0001"+
		"\u0000\u0000\u0000\u0138\u0139\u0001\u0000\u0000\u0000\u0139\u013a\u0001"+
		"\u0000\u0000\u0000\u013a\u013c\u0003\u0014\n\u0000\u013b\u013d\u0003Z"+
		"-\u0000\u013c\u013b\u0001\u0000\u0000\u0000\u013c\u013d\u0001\u0000\u0000"+
		"\u0000\u013d\u0143\u0001\u0000\u0000\u0000\u013e\u0141\u0005\u00b0\u0000"+
		"\u0000\u013f\u0142\u0005\u000e\u0000\u0000\u0140\u0142\u0003*\u0015\u0000"+
		"\u0141\u013f\u0001\u0000\u0000\u0000\u0141\u0140\u0001\u0000\u0000\u0000"+
		"\u0142\u0144\u0001\u0000\u0000\u0000\u0143\u013e\u0001\u0000\u0000\u0000"+
		"\u0143\u0144\u0001\u0000\u0000\u0000\u0144\u0013\u0001\u0000\u0000\u0000"+
		"\u0145\u0146\u00057\u0000\u0000\u0146\u014b\u0003\u0016\u000b\u0000\u0147"+
		"\u0148\u0005\"\u0000\u0000\u0148\u014a\u0003\u0016\u000b\u0000\u0149\u0147"+
		"\u0001\u0000\u0000\u0000\u014a\u014d\u0001\u0000\u0000\u0000\u014b\u0149"+
		"\u0001\u0000\u0000\u0000\u014b\u014c\u0001\u0000\u0000\u0000\u014c\u0015"+
		"\u0001\u0000\u0000\u0000\u014d\u014b\u0001\u0000\u0000\u0000\u014e\u014f"+
		"\u0003\u0086C\u0000\u014f\u0150\u0005\u0015\u0000\u0000\u0150\u0151\u0003"+
		"\u008cF\u0000\u0151\u0017\u0001\u0000\u0000\u0000\u0152\u0153\u0003\u001a"+
		"\r\u0000\u0153\u0019\u0001\u0000\u0000\u0000\u0154\u0155\u0005G\u0000"+
		"\u0000\u0155\u0156\u0005\u001c\u0000\u0000\u0156\u015b\u0003\u008cF\u0000"+
		"\u0157\u0158\u0005\"\u0000\u0000\u0158\u015a\u0003\u008cF\u0000\u0159"+
		"\u0157\u0001\u0000\u0000\u0000\u015a\u015d\u0001\u0000\u0000\u0000\u015b"+
		"\u0159\u0001\u0000\u0000\u0000\u015b\u015c\u0001\u0000\u0000\u0000\u015c"+
		"\u015e\u0001\u0000\u0000\u0000\u015d\u015b\u0001\u0000\u0000\u0000\u015e"+
		"\u015f\u0005\u001d\u0000\u0000\u015f\u001b\u0001\u0000\u0000\u0000\u0160"+
		"\u0162\u0003\u00ceg\u0000\u0161\u0160\u0001\u0000\u0000\u0000\u0161\u0162"+
		"\u0001\u0000\u0000\u0000\u0162\u0163\u0001\u0000\u0000\u0000\u0163\u0164"+
		"\u0005.\u0000\u0000\u0164\u0165\u0005P\u0000\u0000\u0165\u0167\u0003\u0084"+
		"B\u0000\u0166\u0168\u0005Z\u0000\u0000\u0167\u0166\u0001\u0000\u0000\u0000"+
		"\u0167\u0168\u0001\u0000\u0000\u0000\u0168\u016a\u0001\u0000\u0000\u0000"+
		"\u0169\u016b\u00032\u0019\u0000\u016a\u0169\u0001\u0000\u0000\u0000\u016a"+
		"\u016b\u0001\u0000\u0000\u0000\u016b\u016d\u0001\u0000\u0000\u0000\u016c"+
		"\u016e\u0003Z-\u0000\u016d\u016c\u0001\u0000\u0000\u0000\u016d\u016e\u0001"+
		"\u0000\u0000\u0000\u016e\u001d\u0001\u0000\u0000\u0000\u016f\u0171\u0003"+
		"\u00ceg\u0000\u0170\u016f\u0001\u0000\u0000\u0000\u0170\u0171\u0001\u0000"+
		"\u0000\u0000\u0171\u0172\u0001\u0000\u0000\u0000\u0172\u0173\u0003\"\u0011"+
		"\u0000\u0173\u0174\u0003$\u0012\u0000\u0174\u001f\u0001\u0000\u0000\u0000"+
		"\u0175\u0177\u0005\u00ad\u0000\u0000\u0176\u0175\u0001\u0000\u0000\u0000"+
		"\u0176\u0177\u0001\u0000\u0000\u0000\u0177\u0178\u0001\u0000\u0000\u0000"+
		"\u0178\u0179\u0003\u0080@\u0000\u0179\u017a\u0005Z\u0000\u0000\u017a\u017b"+
		"\u0005\u001c\u0000\u0000\u017b\u017c\u0003$\u0012\u0000\u017c\u017d\u0005"+
		"\u001d\u0000\u0000\u017d!\u0001\u0000\u0000\u0000\u017e\u017f\u0005H\u0000"+
		"\u0000\u017f\u0184\u0003 \u0010\u0000\u0180\u0181\u0005\"\u0000\u0000"+
		"\u0181\u0183\u0003 \u0010\u0000\u0182\u0180\u0001\u0000\u0000\u0000\u0183"+
		"\u0186\u0001\u0000\u0000\u0000\u0184\u0182\u0001\u0000\u0000\u0000\u0184"+
		"\u0185\u0001\u0000\u0000\u0000\u0185#\u0001\u0000\u0000\u0000\u0186\u0184"+
		"\u0001\u0000\u0000\u0000\u0187\u0188\u0006\u0012\uffff\uffff\u0000\u0188"+
		"\u018e\u0003(\u0014\u0000\u0189\u018a\u0005\u001c\u0000\u0000\u018a\u018b"+
		"\u0003$\u0012\u0000\u018b\u018c\u0005\u001d\u0000\u0000\u018c\u018e\u0001"+
		"\u0000\u0000\u0000\u018d\u0187\u0001\u0000\u0000\u0000\u018d\u0189\u0001"+
		"\u0000\u0000\u0000\u018e\u0198\u0001\u0000\u0000\u0000\u018f\u0191\n\u0001"+
		"\u0000\u0000\u0190\u0192\u0003\u00ceg\u0000\u0191\u0190\u0001\u0000\u0000"+
		"\u0000\u0191\u0192\u0001\u0000\u0000\u0000\u0192\u0193\u0001\u0000\u0000"+
		"\u0000\u0193\u0194\u0003&\u0013\u0000\u0194\u0195\u0003$\u0012\u0002\u0195"+
		"\u0197\u0001\u0000\u0000\u0000\u0196\u018f\u0001\u0000\u0000\u0000\u0197"+
		"\u019a\u0001\u0000\u0000\u0000\u0198\u0196\u0001\u0000\u0000\u0000\u0198"+
		"\u0199\u0001\u0000\u0000\u0000\u0199%\u0001\u0000\u0000\u0000\u019a\u0198"+
		"\u0001\u0000\u0000\u0000\u019b\u019d\u0007\u0000\u0000\u0000\u019c\u019e"+
		"\u0005k\u0000\u0000\u019d\u019c\u0001\u0000\u0000\u0000\u019d\u019e\u0001"+
		"\u0000\u0000\u0000\u019e\'\u0001\u0000\u0000\u0000\u019f\u01a1\u0003\u00ce"+
		"g\u0000\u01a0\u019f\u0001\u0000\u0000\u0000\u01a0\u01a1\u0001\u0000\u0000"+
		"\u0000\u01a1\u01a2\u0001\u0000\u0000\u0000\u01a2\u01a4\u0005+\u0000\u0000"+
		"\u01a3\u01a5\u0003\u00be_\u0000\u01a4\u01a3\u0001\u0000\u0000\u0000\u01a4"+
		"\u01a5\u0001\u0000\u0000\u0000\u01a5\u01a8\u0001\u0000\u0000\u0000\u01a6"+
		"\u01a9\u0005\u000e\u0000\u0000\u01a7\u01a9\u0003*\u0015\u0000\u01a8\u01a6"+
		"\u0001\u0000\u0000\u0000\u01a8\u01a7\u0001\u0000\u0000\u0000\u01a9\u01ab"+
		"\u0001\u0000\u0000\u0000\u01aa\u01ac\u00036\u001b\u0000\u01ab\u01aa\u0001"+
		"\u0000\u0000\u0000\u01ab\u01ac\u0001\u0000\u0000\u0000\u01ac\u01ae\u0001"+
		"\u0000\u0000\u0000\u01ad\u01af\u0003Z-\u0000\u01ae\u01ad\u0001\u0000\u0000"+
		"\u0000\u01ae\u01af\u0001\u0000\u0000\u0000\u01af\u01b1\u0001\u0000\u0000"+
		"\u0000\u01b0\u01b2\u0003\\.\u0000\u01b1\u01b0\u0001\u0000\u0000\u0000"+
		"\u01b1\u01b2\u0001\u0000\u0000\u0000\u01b2\u01b4\u0001\u0000\u0000\u0000"+
		"\u01b3\u01b5\u0003`0\u0000\u01b4\u01b3\u0001\u0000\u0000\u0000\u01b4\u01b5"+
		"\u0001\u0000\u0000\u0000\u01b5\u01b7\u0001\u0000\u0000\u0000\u01b6\u01b8"+
		"\u0003\u00b6[\u0000\u01b7\u01b6\u0001\u0000\u0000\u0000\u01b7\u01b8\u0001"+
		"\u0000\u0000\u0000\u01b8\u01ba\u0001\u0000\u0000\u0000\u01b9\u01bb\u0003"+
		"\u00deo\u0000\u01ba\u01b9\u0001\u0000\u0000\u0000\u01ba\u01bb\u0001\u0000"+
		"\u0000\u0000\u01bb\u01bd\u0001\u0000\u0000\u0000\u01bc\u01be\u0003b1\u0000"+
		"\u01bd\u01bc\u0001\u0000\u0000\u0000\u01bd\u01be\u0001\u0000\u0000\u0000"+
		"\u01be\u01c4\u0001\u0000\u0000\u0000\u01bf\u01c2\u0003j5\u0000\u01c0\u01c3"+
		"\u0005\u0165\u0000\u0000\u01c1\u01c3\u0003l6\u0000\u01c2\u01c0\u0001\u0000"+
		"\u0000\u0000\u01c2\u01c1\u0001\u0000\u0000\u0000\u01c2\u01c3\u0001\u0000"+
		"\u0000\u0000\u01c3\u01c5\u0001\u0000\u0000\u0000\u01c4\u01bf\u0001\u0000"+
		"\u0000\u0000\u01c4\u01c5\u0001\u0000\u0000\u0000\u01c5)\u0001\u0000\u0000"+
		"\u0000\u01c6\u01cb\u0003,\u0016\u0000\u01c7\u01c8\u0005\"\u0000\u0000"+
		"\u01c8\u01ca\u0003,\u0016\u0000\u01c9\u01c7\u0001\u0000\u0000\u0000\u01ca"+
		"\u01cd\u0001\u0000\u0000\u0000\u01cb\u01c9\u0001\u0000\u0000\u0000\u01cb"+
		"\u01cc\u0001\u0000\u0000\u0000\u01cc+\u0001\u0000\u0000\u0000\u01cd\u01cb"+
		"\u0001\u0000\u0000\u0000\u01ce\u01d1\u0003.\u0017\u0000\u01cf\u01d1\u0003"+
		"0\u0018\u0000\u01d0\u01ce\u0001\u0000\u0000\u0000\u01d0\u01cf\u0001\u0000"+
		"\u0000\u0000\u01d1-\u0001\u0000\u0000\u0000\u01d2\u01d4\u0003\u008cF\u0000"+
		"\u01d3\u01d5\u0005Z\u0000\u0000\u01d4\u01d3\u0001\u0000\u0000\u0000\u01d4"+
		"\u01d5\u0001\u0000\u0000\u0000\u01d5\u01d7\u0001\u0000\u0000\u0000\u01d6"+
		"\u01d8\u00032\u0019\u0000\u01d7\u01d6\u0001\u0000\u0000\u0000\u01d7\u01d8"+
		"\u0001\u0000\u0000\u0000\u01d8\u01da\u0001\u0000\u0000\u0000\u01d9\u01db"+
		"\u0003\u00ceg\u0000\u01da\u01d9\u0001\u0000\u0000\u0000\u01da\u01db\u0001"+
		"\u0000\u0000\u0000\u01db/\u0001\u0000\u0000\u0000\u01dc\u01dd\u0003\u0088"+
		"D\u0000\u01dd\u01de\u0005\u0012\u0000\u0000\u01de1\u0001\u0000\u0000\u0000"+
		"\u01df\u01e0\u00034\u001a\u0000\u01e03\u0001\u0000\u0000\u0000\u01e1\u01e4"+
		"\u0003\u0080@\u0000\u01e2\u01e4\u0005\u0169\u0000\u0000\u01e3\u01e1\u0001"+
		"\u0000\u0000\u0000\u01e3\u01e2\u0001\u0000\u0000\u0000\u01e45\u0001\u0000"+
		"\u0000\u0000\u01e5\u01e7\u0005P\u0000\u0000\u01e6\u01e8\u0003\u00ceg\u0000"+
		"\u01e7\u01e6\u0001\u0000\u0000\u0000\u01e7\u01e8\u0001\u0000\u0000\u0000"+
		"\u01e8\u01e9\u0001\u0000\u0000\u0000\u01e9\u01ea\u00038\u001c\u0000\u01ea"+
		"7\u0001\u0000\u0000\u0000\u01eb\u01f0\u0003:\u001d\u0000\u01ec\u01ed\u0005"+
		"\"\u0000\u0000\u01ed\u01ef\u0003:\u001d\u0000\u01ee\u01ec\u0001\u0000"+
		"\u0000\u0000\u01ef\u01f2\u0001\u0000\u0000\u0000\u01f0\u01ee\u0001\u0000"+
		"\u0000\u0000\u01f0\u01f1\u0001\u0000\u0000\u0000\u01f19\u0001\u0000\u0000"+
		"\u0000\u01f2\u01f0\u0001\u0000\u0000\u0000\u01f3\u01f4\u0006\u001d\uffff"+
		"\uffff\u0000\u01f4\u01fb\u0003<\u001e\u0000\u01f5\u01fb\u0003D\"\u0000"+
		"\u01f6\u01fb\u0003>\u001f\u0000\u01f7\u01fb\u0003@ \u0000\u01f8\u01fb"+
		"\u0003B!\u0000\u01f9\u01fb\u0003F#\u0000\u01fa\u01f3\u0001\u0000\u0000"+
		"\u0000\u01fa\u01f5\u0001\u0000\u0000\u0000\u01fa\u01f6\u0001\u0000\u0000"+
		"\u0000\u01fa\u01f7\u0001\u0000\u0000\u0000\u01fa\u01f8\u0001\u0000\u0000"+
		"\u0000\u01fa\u01f9\u0001\u0000\u0000\u0000\u01fb\u0205\u0001\u0000\u0000"+
		"\u0000\u01fc\u01fd\n\u0001\u0000\u0000\u01fd\u01fe\u0003L&\u0000\u01fe"+
		"\u0201\u0003N\'\u0000\u01ff\u0200\u0005[\u0000\u0000\u0200\u0202\u0003"+
		"\u008cF\u0000\u0201\u01ff\u0001\u0000\u0000\u0000\u0201\u0202\u0001\u0000"+
		"\u0000\u0000\u0202\u0204\u0001\u0000\u0000\u0000\u0203\u01fc\u0001\u0000"+
		"\u0000\u0000\u0204\u0207\u0001\u0000\u0000\u0000\u0205\u0203\u0001\u0000"+
		"\u0000\u0000\u0205\u0206\u0001\u0000\u0000\u0000\u0206;\u0001\u0000\u0000"+
		"\u0000\u0207\u0205\u0001\u0000\u0000\u0000\u0208\u020a\u0003\u0084B\u0000"+
		"\u0209\u020b\u0005Z\u0000\u0000\u020a\u0209\u0001\u0000\u0000\u0000\u020a"+
		"\u020b\u0001\u0000\u0000\u0000\u020b\u020d\u0001\u0000\u0000\u0000\u020c"+
		"\u020e\u00032\u0019\u0000\u020d\u020c\u0001\u0000\u0000\u0000\u020d\u020e"+
		"\u0001\u0000\u0000\u0000\u020e\u0210\u0001\u0000\u0000\u0000\u020f\u0211"+
		"\u0003\u00ceg\u0000\u0210\u020f\u0001\u0000\u0000\u0000\u0210\u0211\u0001"+
		"\u0000\u0000\u0000\u0211=\u0001\u0000\u0000\u0000\u0212\u0213\u0005\u014f"+
		"\u0000\u0000\u0213\u0214\u0005\u001c\u0000\u0000\u0214\u0215\u0003\u0080"+
		"@\u0000\u0215\u0216\u0005\u0011\u0000\u0000\u0216\u0217\u0003\u0080@\u0000"+
		"\u0217\u0218\u0005\"\u0000\u0000\u0218\u0219\u0003\u00dam\u0000\u0219"+
		"\u021b\u0005\u001d\u0000\u0000\u021a\u021c\u0005Z\u0000\u0000\u021b\u021a"+
		"\u0001\u0000\u0000\u0000\u021b\u021c\u0001\u0000\u0000\u0000\u021c\u021e"+
		"\u0001\u0000\u0000\u0000\u021d\u021f\u00032\u0019\u0000\u021e\u021d\u0001"+
		"\u0000\u0000\u0000\u021e\u021f\u0001\u0000\u0000\u0000\u021f\u0221\u0001"+
		"\u0000\u0000\u0000\u0220\u0222\u0003\u00ceg\u0000\u0221\u0220\u0001\u0000"+
		"\u0000\u0000\u0221\u0222\u0001\u0000\u0000\u0000\u0222?\u0001\u0000\u0000"+
		"\u0000\u0223\u0224\u0005\u0150\u0000\u0000\u0224\u0225\u0005\u001c\u0000"+
		"\u0000\u0225\u0226\u0003\u0080@\u0000\u0226\u0227\u0005\u0011\u0000\u0000"+
		"\u0227\u0228\u0003\u0080@\u0000\u0228\u0229\u0005\"\u0000\u0000\u0229"+
		"\u022a\u0003\u00dam\u0000\u022a\u022b\u0005\"\u0000\u0000\u022b\u022c"+
		"\u0003\u00dam\u0000\u022c\u022e\u0005\u001d\u0000\u0000\u022d\u022f\u0005"+
		"Z\u0000\u0000\u022e\u022d\u0001\u0000\u0000\u0000\u022e\u022f\u0001\u0000"+
		"\u0000\u0000\u022f\u0231\u0001\u0000\u0000\u0000\u0230\u0232\u00032\u0019"+
		"\u0000\u0231\u0230\u0001\u0000\u0000\u0000\u0231\u0232\u0001\u0000\u0000"+
		"\u0000\u0232\u0234\u0001\u0000\u0000\u0000\u0233\u0235\u0003\u00ceg\u0000"+
		"\u0234\u0233\u0001\u0000\u0000\u0000\u0234\u0235\u0001\u0000\u0000\u0000"+
		"\u0235A\u0001\u0000\u0000\u0000\u0236\u0237\u0005\u0144\u0000\u0000\u0237"+
		"\u0238\u0005\u001c\u0000\u0000\u0238\u0239\u0003\u0080@\u0000\u0239\u023a"+
		"\u0005\u0011\u0000\u0000\u023a\u023b\u0003\u0080@\u0000\u023b\u023c\u0005"+
		"\"\u0000\u0000\u023c\u023d\u0003\u00dam\u0000\u023d\u023f\u0005\u001d"+
		"\u0000\u0000\u023e\u0240\u0005Z\u0000\u0000\u023f\u023e\u0001\u0000\u0000"+
		"\u0000\u023f\u0240\u0001\u0000\u0000\u0000\u0240\u0242\u0001\u0000\u0000"+
		"\u0000\u0241\u0243\u00032\u0019\u0000\u0242\u0241\u0001\u0000\u0000\u0000"+
		"\u0242\u0243\u0001\u0000\u0000\u0000\u0243\u0245\u0001\u0000\u0000\u0000"+
		"\u0244\u0246\u0003\u00ceg\u0000\u0245\u0244\u0001\u0000\u0000\u0000\u0245"+
		"\u0246\u0001\u0000\u0000\u0000\u0246C\u0001\u0000\u0000\u0000\u0247\u0249"+
		"\u0005V\u0000\u0000\u0248\u0247\u0001\u0000\u0000\u0000\u0248\u0249\u0001"+
		"\u0000\u0000\u0000\u0249\u024a\u0001\u0000\u0000\u0000\u024a\u024b\u0005"+
		"\u001c\u0000\u0000\u024b\u024c\u0003$\u0012\u0000\u024c\u024e\u0005\u001d"+
		"\u0000\u0000\u024d\u024f\u0005Z\u0000\u0000\u024e\u024d\u0001\u0000\u0000"+
		"\u0000\u024e\u024f\u0001\u0000\u0000\u0000\u024f\u0250\u0001\u0000\u0000"+
		"\u0000\u0250\u0251\u00032\u0019\u0000\u0251E\u0001\u0000\u0000\u0000\u0252"+
		"\u0253\u0005\u001c\u0000\u0000\u0253\u0254\u0005G\u0000\u0000\u0254\u0255"+
		"\u0003H$\u0000\u0255\u0257\u0005\u001d\u0000\u0000\u0256\u0258\u0005Z"+
		"\u0000\u0000\u0257\u0256\u0001\u0000\u0000\u0000\u0257\u0258\u0001\u0000"+
		"\u0000\u0000\u0258\u0259\u0001\u0000\u0000\u0000\u0259\u025b\u00032\u0019"+
		"\u0000\u025a\u025c\u0003J%\u0000\u025b\u025a\u0001\u0000\u0000\u0000\u025b"+
		"\u025c\u0001\u0000\u0000\u0000\u025cG\u0001\u0000\u0000\u0000\u025d\u0262"+
		"\u0003\u009eO\u0000\u025e\u025f\u0005\"\u0000\u0000\u025f\u0261\u0003"+
		"\u009eO\u0000\u0260\u025e\u0001\u0000\u0000\u0000\u0261\u0264\u0001\u0000"+
		"\u0000\u0000\u0262\u0260\u0001\u0000\u0000\u0000\u0262\u0263\u0001\u0000"+
		"\u0000\u0000\u0263I\u0001\u0000\u0000\u0000\u0264\u0262\u0001\u0000\u0000"+
		"\u0000\u0265\u0266\u0005\u001c\u0000\u0000\u0266\u026b\u0003\u0080@\u0000"+
		"\u0267\u0268\u0005\"\u0000\u0000\u0268\u026a\u0003\u0080@\u0000\u0269"+
		"\u0267\u0001\u0000\u0000\u0000\u026a\u026d\u0001\u0000\u0000\u0000\u026b"+
		"\u0269\u0001\u0000\u0000\u0000\u026b\u026c\u0001\u0000\u0000\u0000\u026c"+
		"\u026e\u0001\u0000\u0000\u0000\u026d\u026b\u0001\u0000\u0000\u0000\u026e"+
		"\u026f\u0005\u001d\u0000\u0000\u026fK\u0001\u0000\u0000\u0000\u0270\u0276"+
		"\u0003P(\u0000\u0271\u0276\u0003T*\u0000\u0272\u0276\u0003V+\u0000\u0273"+
		"\u0276\u0003R)\u0000\u0274\u0276\u0003X,\u0000\u0275\u0270\u0001\u0000"+
		"\u0000\u0000\u0275\u0271\u0001\u0000\u0000\u0000\u0275\u0272\u0001\u0000"+
		"\u0000\u0000\u0275\u0273\u0001\u0000\u0000\u0000\u0275\u0274\u0001\u0000"+
		"\u0000\u0000\u0276M\u0001\u0000\u0000\u0000\u0277\u027a\u0003<\u001e\u0000"+
		"\u0278\u027a\u0003D\"\u0000\u0279\u0277\u0001\u0000\u0000\u0000\u0279"+
		"\u0278\u0001\u0000\u0000\u0000\u027aO\u0001\u0000\u0000\u0000\u027b\u027d"+
		"\u0005S\u0000\u0000\u027c\u027b\u0001\u0000\u0000\u0000\u027c\u027d\u0001"+
		"\u0000\u0000\u0000\u027d\u027e\u0001\u0000\u0000\u0000\u027e\u027f\u0005"+
		"Q\u0000\u0000\u027fQ\u0001\u0000\u0000\u0000\u0280\u0282\u0005R\u0000"+
		"\u0000\u0281\u0283\u0005T\u0000\u0000\u0282\u0281\u0001\u0000\u0000\u0000"+
		"\u0282\u0283\u0001\u0000\u0000\u0000\u0283\u0284\u0001\u0000\u0000\u0000"+
		"\u0284\u0285\u0005Q\u0000\u0000\u0285S\u0001\u0000\u0000\u0000\u0286\u0288"+
		"\u0005U\u0000\u0000\u0287\u0289\u0005T\u0000\u0000\u0288\u0287\u0001\u0000"+
		"\u0000\u0000\u0288\u0289\u0001\u0000\u0000\u0000\u0289\u028a\u0001\u0000"+
		"\u0000\u0000\u028a\u028b\u0005Q\u0000\u0000\u028bU\u0001\u0000\u0000\u0000"+
		"\u028c\u028e\u0005W\u0000\u0000\u028d\u028f\u0005T\u0000\u0000\u028e\u028d"+
		"\u0001\u0000\u0000\u0000\u028e\u028f\u0001\u0000\u0000\u0000\u028f\u0290"+
		"\u0001\u0000\u0000\u0000\u0290\u0291\u0005Q\u0000\u0000\u0291W\u0001\u0000"+
		"\u0000\u0000\u0292\u0293\u0005X\u0000\u0000\u0293\u0294\u0005Q\u0000\u0000"+
		"\u0294Y\u0001\u0000\u0000\u0000\u0295\u0296\u0005Y\u0000\u0000\u0296\u0297"+
		"\u0003\u008cF\u0000\u0297[\u0001\u0000\u0000\u0000\u0298\u0299\u0005p"+
		"\u0000\u0000\u0299\u029a\u0005q\u0000\u0000\u029a\u029b\u0003^/\u0000"+
		"\u029b]\u0001\u0000\u0000\u0000\u029c\u02a1\u0003\u00e4r\u0000\u029d\u029e"+
		"\u0005\"\u0000\u0000\u029e\u02a0\u0003\u00e4r\u0000\u029f\u029d\u0001"+
		"\u0000\u0000\u0000\u02a0\u02a3\u0001\u0000\u0000\u0000\u02a1\u029f\u0001"+
		"\u0000\u0000\u0000\u02a1\u02a2\u0001\u0000\u0000\u0000\u02a2_\u0001\u0000"+
		"\u0000\u0000\u02a3\u02a1\u0001\u0000\u0000\u0000\u02a4\u02a5\u0005t\u0000"+
		"\u0000\u02a5\u02a6\u0003\u008cF\u0000\u02a6a\u0001\u0000\u0000\u0000\u02a7"+
		"\u02a8\u0005u\u0000\u0000\u02a8\u02ab\u0003d2\u0000\u02a9\u02aa\u0005"+
		"v\u0000\u0000\u02aa\u02ac\u0003f3\u0000\u02ab\u02a9\u0001\u0000\u0000"+
		"\u0000\u02ab\u02ac\u0001\u0000\u0000\u0000\u02ac\u02bd\u0001\u0000\u0000"+
		"\u0000\u02ad\u02ae\u0005\u0110\u0000\u0000\u02ae\u02af\u0005\u0111\u0000"+
		"\u0000\u02af\u02b0\u0003d2\u0000\u02b0\u02b1\u0005\u0141\u0000\u0000\u02b1"+
		"\u02b2\u0005\u0130\u0000\u0000\u02b2\u02bd\u0001\u0000\u0000\u0000\u02b3"+
		"\u02b4\u0005v\u0000\u0000\u02b4\u02b5\u0003f3\u0000\u02b5\u02b6\u0005"+
		"\u0141\u0000\u0000\u02b6\u02b7\u0005\u0110\u0000\u0000\u02b7\u02b8\u0005"+
		"\u00a3\u0000\u0000\u02b8\u02b9\u0003d2\u0000\u02b9\u02ba\u0005\u0141\u0000"+
		"\u0000\u02ba\u02bb\u0005\u0130\u0000\u0000\u02bb\u02bd\u0001\u0000\u0000"+
		"\u0000\u02bc\u02a7\u0001\u0000\u0000\u0000\u02bc\u02ad\u0001\u0000\u0000"+
		"\u0000\u02bc\u02b3\u0001\u0000\u0000\u0000\u02bdc\u0001\u0000\u0000\u0000"+
		"\u02be\u02c1\u0003t:\u0000\u02bf\u02c1\u0003n7\u0000\u02c0\u02be\u0001"+
		"\u0000\u0000\u0000\u02c0\u02bf\u0001\u0000\u0000\u0000\u02c1e\u0001\u0000"+
		"\u0000\u0000\u02c2\u02c5\u0003t:\u0000\u02c3\u02c5\u0003n7\u0000\u02c4"+
		"\u02c2\u0001\u0000\u0000\u0000\u02c4\u02c3\u0001\u0000\u0000\u0000\u02c5"+
		"g\u0001\u0000\u0000\u0000\u02c6\u02c7\u0005\u001c\u0000\u0000\u02c7\u02c8"+
		"\u0003$\u0012\u0000\u02c8\u02c9\u0005\u001d\u0000\u0000\u02c9i\u0001\u0000"+
		"\u0000\u0000\u02ca\u02cb\u0005_\u0000\u0000\u02cb\u02cc\u0005-\u0000\u0000"+
		"\u02cck\u0001\u0000\u0000\u0000\u02cd\u02ce\u0005\u0166\u0000\u0000\u02ce"+
		"\u02cf\u0005\u0167\u0000\u0000\u02cfm\u0001\u0000\u0000\u0000\u02d0\u02d1"+
		"\u0005%\u0000\u0000\u02d1o\u0001\u0000\u0000\u0000\u02d2\u02da\u0003r"+
		"9\u0000\u02d3\u02da\u0003t:\u0000\u02d4\u02da\u0003v;\u0000\u02d5\u02da"+
		"\u0003x<\u0000\u02d6\u02da\u0003z=\u0000\u02d7\u02da\u0003|>\u0000\u02d8"+
		"\u02da\u0003~?\u0000\u02d9\u02d2\u0001\u0000\u0000\u0000\u02d9\u02d3\u0001"+
		"\u0000\u0000\u0000\u02d9\u02d4\u0001\u0000\u0000\u0000\u02d9\u02d5\u0001"+
		"\u0000\u0000\u0000\u02d9\u02d6\u0001\u0000\u0000\u0000\u02d9\u02d7\u0001"+
		"\u0000\u0000\u0000\u02d9\u02d8\u0001\u0000\u0000\u0000\u02daq\u0001\u0000"+
		"\u0000\u0000\u02db\u02dc\u0005\u0169\u0000\u0000\u02dcs\u0001\u0000\u0000"+
		"\u0000\u02dd\u02de\u0005\u016a\u0000\u0000\u02deu\u0001\u0000\u0000\u0000"+
		"\u02df\u02e0\u0007\u0001\u0000\u0000\u02e0\u02e1\u0005\u0169\u0000\u0000"+
		"\u02e1w\u0001\u0000\u0000\u0000\u02e2\u02e3\u0005\u016b\u0000\u0000\u02e3"+
		"y\u0001\u0000\u0000\u0000\u02e4\u02e5\u0005\u016c\u0000\u0000\u02e5{\u0001"+
		"\u0000\u0000\u0000\u02e6\u02e7\u0007\u0002\u0000\u0000\u02e7}\u0001\u0000"+
		"\u0000\u0000\u02e8\u02e9\u0005e\u0000\u0000\u02e9\u007f\u0001\u0000\u0000"+
		"\u0000\u02ea\u02ed\u0005\u0168\u0000\u0000\u02eb\u02ed\u0003\u0082A\u0000"+
		"\u02ec\u02ea\u0001\u0000\u0000\u0000\u02ec\u02eb\u0001\u0000\u0000\u0000"+
		"\u02ed\u0081\u0001\u0000\u0000\u0000\u02ee\u02ef\u0007\u0003\u0000\u0000"+
		"\u02ef\u0083\u0001\u0000\u0000\u0000\u02f0\u02f1\u0003\u0088D\u0000\u02f1"+
		"\u02f2\u0005\u0011\u0000\u0000\u02f2\u02f4\u0001\u0000\u0000\u0000\u02f3"+
		"\u02f0\u0001\u0000\u0000\u0000\u02f3\u02f4\u0001\u0000\u0000\u0000\u02f4"+
		"\u02f5\u0001\u0000\u0000\u0000\u02f5\u02f6\u0003\u0080@\u0000\u02f6\u0085"+
		"\u0001\u0000\u0000\u0000\u02f7\u02f8\u0003\u0088D\u0000\u02f8\u02f9\u0005"+
		"\u0011\u0000\u0000\u02f9\u02fb\u0001\u0000\u0000\u0000\u02fa\u02f7\u0001"+
		"\u0000\u0000\u0000\u02fa\u02fb\u0001\u0000\u0000\u0000\u02fb\u02fc\u0001"+
		"\u0000\u0000\u0000\u02fc\u02fd\u0003\u0080@\u0000\u02fd\u0087\u0001\u0000"+
		"\u0000\u0000\u02fe\u0301\u0003\u0080@\u0000\u02ff\u0300\u0005\u0011\u0000"+
		"\u0000\u0300\u0302\u0003\u0088D\u0000\u0301\u02ff\u0001\u0000\u0000\u0000"+
		"\u0301\u0302\u0001\u0000\u0000\u0000\u0302\u0089\u0001\u0000\u0000\u0000"+
		"\u0303\u0308\u0003\u0086C\u0000\u0304\u0305\u0005\"\u0000\u0000\u0305"+
		"\u0307\u0003\u0086C\u0000\u0306\u0304\u0001\u0000\u0000\u0000\u0307\u030a"+
		"\u0001\u0000\u0000\u0000\u0308\u0306\u0001\u0000\u0000\u0000\u0308\u0309"+
		"\u0001\u0000\u0000\u0000\u0309\u008b\u0001\u0000\u0000\u0000\u030a\u0308"+
		"\u0001\u0000\u0000\u0000\u030b\u030c\u0006F\uffff\uffff\u0000\u030c\u030d"+
		"\u0007\u0004\u0000\u0000\u030d\u0310\u0003\u008cF\u0004\u030e\u0310\u0003"+
		"\u008eG\u0000\u030f\u030b\u0001\u0000\u0000\u0000\u030f\u030e\u0001\u0000"+
		"\u0000\u0000\u0310\u0319\u0001\u0000\u0000\u0000\u0311\u0312\n\u0003\u0000"+
		"\u0000\u0312\u0313\u0007\u0005\u0000\u0000\u0313\u0318\u0003\u008cF\u0004"+
		"\u0314\u0315\n\u0002\u0000\u0000\u0315\u0316\u0005b\u0000\u0000\u0316"+
		"\u0318\u0003\u008cF\u0003\u0317\u0311\u0001\u0000\u0000\u0000\u0317\u0314"+
		"\u0001\u0000\u0000\u0000\u0318\u031b\u0001\u0000\u0000\u0000\u0319\u0317"+
		"\u0001\u0000\u0000\u0000\u0319\u031a\u0001\u0000\u0000\u0000\u031a\u008d"+
		"\u0001\u0000\u0000\u0000\u031b\u0319\u0001\u0000\u0000\u0000\u031c\u031d"+
		"\u0006G\uffff\uffff\u0000\u031d\u031e\u0003\u0092I\u0000\u031e\u034f\u0001"+
		"\u0000\u0000\u0000\u031f\u0320\n\u000b\u0000\u0000\u0320\u0322\u0005c"+
		"\u0000\u0000\u0321\u0323\u0005d\u0000\u0000\u0322\u0321\u0001\u0000\u0000"+
		"\u0000\u0322\u0323\u0001\u0000\u0000\u0000\u0323\u0324\u0001\u0000\u0000"+
		"\u0000\u0324\u034e\u0005e\u0000\u0000\u0325\u0326\n\n\u0000\u0000\u0326"+
		"\u0327\u0005c\u0000\u0000\u0327\u0328\u0005d\u0000\u0000\u0328\u0329\u0005"+
		"J\u0000\u0000\u0329\u032a\u0005P\u0000\u0000\u032a\u034e\u0003\u0092I"+
		"\u0000\u032b\u032c\n\t\u0000\u0000\u032c\u032d\u0005c\u0000\u0000\u032d"+
		"\u032e\u0005J\u0000\u0000\u032e\u032f\u0005P\u0000\u0000\u032f\u034e\u0003"+
		"\u0092I\u0000\u0330\u0331\n\b\u0000\u0000\u0331\u0333\u0005c\u0000\u0000"+
		"\u0332\u0334\u0005d\u0000\u0000\u0333\u0332\u0001\u0000\u0000\u0000\u0333"+
		"\u0334\u0001\u0000\u0000\u0000\u0334\u0335\u0001\u0000\u0000\u0000\u0335"+
		"\u034e\u0005\u0158\u0000\u0000\u0336\u0337\n\u0007\u0000\u0000\u0337\u0338"+
		"\u0005c\u0000\u0000\u0338\u034e\u0005f\u0000\u0000\u0339\u033a\n\u0006"+
		"\u0000\u0000\u033a\u033b\u0005c\u0000\u0000\u033b\u033c\u0005d\u0000\u0000"+
		"\u033c\u034e\u0005f\u0000\u0000\u033d\u033e\n\u0005\u0000\u0000\u033e"+
		"\u033f\u0005c\u0000\u0000\u033f\u034e\u0005g\u0000\u0000\u0340\u0341\n"+
		"\u0004\u0000\u0000\u0341\u0342\u0005c\u0000\u0000\u0342\u0343\u0005d\u0000"+
		"\u0000\u0343\u034e\u0005g\u0000\u0000\u0344\u0345\n\u0003\u0000\u0000"+
		"\u0345\u0346\u0003\u0090H\u0000\u0346\u0347\u0003\u0092I\u0000\u0347\u034e"+
		"\u0001\u0000\u0000\u0000\u0348\u0349\n\u0002\u0000\u0000\u0349\u034a\u0003"+
		"\u0090H\u0000\u034a\u034b\u0007\u0006\u0000\u0000\u034b\u034c\u0003h4"+
		"\u0000\u034c\u034e\u0001\u0000\u0000\u0000\u034d\u031f\u0001\u0000\u0000"+
		"\u0000\u034d\u0325\u0001\u0000\u0000\u0000\u034d\u032b\u0001\u0000\u0000"+
		"\u0000\u034d\u0330\u0001\u0000\u0000\u0000\u034d\u0336\u0001\u0000\u0000"+
		"\u0000\u034d\u0339\u0001\u0000\u0000\u0000\u034d\u033d\u0001\u0000\u0000"+
		"\u0000\u034d\u0340\u0001\u0000\u0000\u0000\u034d\u0344\u0001\u0000\u0000"+
		"\u0000\u034d\u0348\u0001\u0000\u0000\u0000\u034e\u0351\u0001\u0000\u0000"+
		"\u0000\u034f\u034d\u0001\u0000\u0000\u0000\u034f\u0350\u0001\u0000\u0000"+
		"\u0000\u0350\u008f\u0001\u0000\u0000\u0000\u0351\u034f\u0001\u0000\u0000"+
		"\u0000\u0352\u0353\u0007\u0007\u0000\u0000\u0353\u0091\u0001\u0000\u0000"+
		"\u0000\u0354\u0356\u0003\u0096K\u0000\u0355\u0357\u0005d\u0000\u0000\u0356"+
		"\u0355\u0001\u0000\u0000\u0000\u0356\u0357\u0001\u0000\u0000\u0000\u0357"+
		"\u0358\u0001\u0000\u0000\u0000\u0358\u0359\u0005j\u0000\u0000\u0359\u035a"+
		"\u0003h4\u0000\u035a\u0383\u0001\u0000\u0000\u0000\u035b\u035d\u0003\u0096"+
		"K\u0000\u035c\u035e\u0005d\u0000\u0000\u035d\u035c\u0001\u0000\u0000\u0000"+
		"\u035d\u035e\u0001\u0000\u0000\u0000\u035e\u035f\u0001\u0000\u0000\u0000"+
		"\u035f\u0360\u0005j\u0000\u0000\u0360\u0361\u0005\u001c\u0000\u0000\u0361"+
		"\u0362\u0003\u0094J\u0000\u0362\u0363\u0005\u001d\u0000\u0000\u0363\u0383"+
		"\u0001\u0000\u0000\u0000\u0364\u0366\u0003\u0096K\u0000\u0365\u0367\u0005"+
		"d\u0000\u0000\u0366\u0365\u0001\u0000\u0000\u0000\u0366\u0367\u0001\u0000"+
		"\u0000\u0000\u0367\u0368\u0001\u0000\u0000\u0000\u0368\u0369\u0005i\u0000"+
		"\u0000\u0369\u036a\u0003\u0096K\u0000\u036a\u036b\u0005a\u0000\u0000\u036b"+
		"\u036c\u0003\u0092I\u0000\u036c\u0383\u0001\u0000\u0000\u0000\u036d\u036f"+
		"\u0003\u0096K\u0000\u036e\u0370\u0005d\u0000\u0000\u036f\u036e\u0001\u0000"+
		"\u0000\u0000\u036f\u0370\u0001\u0000\u0000\u0000\u0370\u0371\u0001\u0000"+
		"\u0000\u0000\u0371\u0372\u0005m\u0000\u0000\u0372\u0375\u0003\u0098L\u0000"+
		"\u0373\u0374\u0005\u0109\u0000\u0000\u0374\u0376\u0003\u0098L\u0000\u0375"+
		"\u0373\u0001\u0000\u0000\u0000\u0375\u0376\u0001\u0000\u0000\u0000\u0376"+
		"\u0383\u0001\u0000\u0000\u0000\u0377\u0379\u0003\u0096K\u0000\u0378\u037a"+
		"\u0005d\u0000\u0000\u0379\u0378\u0001\u0000\u0000\u0000\u0379\u037a\u0001"+
		"\u0000\u0000\u0000\u037a\u037b\u0001\u0000\u0000\u0000\u037b\u037c\u0005"+
		"n\u0000\u0000\u037c\u037f\u0003\u0098L\u0000\u037d\u037e\u0005\u0109\u0000"+
		"\u0000\u037e\u0380\u0003\u0098L\u0000\u037f\u037d\u0001\u0000\u0000\u0000"+
		"\u037f\u0380\u0001\u0000\u0000\u0000\u0380\u0383\u0001\u0000\u0000\u0000"+
		"\u0381\u0383\u0003\u0096K\u0000\u0382\u0354\u0001\u0000\u0000\u0000\u0382"+
		"\u035b\u0001\u0000\u0000\u0000\u0382\u0364\u0001\u0000\u0000\u0000\u0382"+
		"\u036d\u0001\u0000\u0000\u0000\u0382\u0377\u0001\u0000\u0000\u0000\u0382"+
		"\u0381\u0001\u0000\u0000\u0000\u0383\u0093\u0001\u0000\u0000\u0000\u0384"+
		"\u0389\u0003\u008cF\u0000\u0385\u0386\u0005\"\u0000\u0000\u0386\u0388"+
		"\u0003\u008cF\u0000\u0387\u0385\u0001\u0000\u0000\u0000\u0388\u038b\u0001"+
		"\u0000\u0000\u0000\u0389\u0387\u0001\u0000\u0000\u0000\u0389\u038a\u0001"+
		"\u0000\u0000\u0000\u038a\u0095\u0001\u0000\u0000\u0000\u038b\u0389\u0001"+
		"\u0000\u0000\u0000\u038c\u038d\u0006K\uffff\uffff\u0000\u038d\u038e\u0003"+
		"\u0098L\u0000\u038e\u038f\u0005\u00e7\u0000\u0000\u038f\u0390\u0005\u0082"+
		"\u0000\u0000\u0390\u0391\u0005\u0162\u0000\u0000\u0391\u0392\u0003\u0098"+
		"L\u0000\u0392\u0395\u0001\u0000\u0000\u0000\u0393\u0395\u0003\u0098L\u0000"+
		"\u0394\u038c\u0001\u0000\u0000\u0000\u0394\u0393\u0001\u0000\u0000\u0000"+
		"\u0395\u03aa\u0001\u0000\u0000\u0000\u0396\u0397\n\b\u0000\u0000\u0397"+
		"\u0398\u0005\t\u0000\u0000\u0398\u03a9\u0003\u0096K\t\u0399\u039a\n\u0007"+
		"\u0000\u0000\u039a\u039b\u0007\b\u0000\u0000\u039b\u03a9\u0003\u0096K"+
		"\b\u039c\u039d\n\u0006\u0000\u0000\u039d\u039e\u0007\t\u0000\u0000\u039e"+
		"\u03a9\u0003\u0096K\u0007\u039f\u03a0\n\u0005\u0000\u0000\u03a0\u03a1"+
		"\u0007\n\u0000\u0000\u03a1\u03a9\u0003\u0096K\u0006\u03a2\u03a3\n\u0004"+
		"\u0000\u0000\u03a3\u03a4\u0005\u0006\u0000\u0000\u03a4\u03a9\u0003\u0096"+
		"K\u0005\u03a5\u03a6\n\u0003\u0000\u0000\u03a6\u03a7\u0005\u0005\u0000"+
		"\u0000\u03a7\u03a9\u0003\u0096K\u0004\u03a8\u0396\u0001\u0000\u0000\u0000"+
		"\u03a8\u0399\u0001\u0000\u0000\u0000\u03a8\u039c\u0001\u0000\u0000\u0000"+
		"\u03a8\u039f\u0001\u0000\u0000\u0000\u03a8\u03a2\u0001\u0000\u0000\u0000"+
		"\u03a8\u03a5\u0001\u0000\u0000\u0000\u03a9\u03ac\u0001\u0000\u0000\u0000"+
		"\u03aa\u03a8\u0001\u0000\u0000\u0000\u03aa\u03ab\u0001\u0000\u0000\u0000"+
		"\u03ab\u0097\u0001\u0000\u0000\u0000\u03ac\u03aa\u0001\u0000\u0000\u0000"+
		"\u03ad\u03ba\u0003\u00a2Q\u0000\u03ae\u03ba\u0003\u00a6S\u0000\u03af\u03ba"+
		"\u0003n7\u0000\u03b0\u03ba\u0003p8\u0000\u03b1\u03ba\u0003\u0086C\u0000"+
		"\u03b2\u03ba\u0003h4\u0000\u03b3\u03ba\u0003\u009aM\u0000\u03b4\u03ba"+
		"\u0003\u009cN\u0000\u03b5\u03ba\u0003\u009eO\u0000\u03b6\u03ba\u0003\u00a0"+
		"P\u0000\u03b7\u03ba\u0003\u00d4j\u0000\u03b8\u03ba\u0003\u00dam\u0000"+
		"\u03b9\u03ad\u0001\u0000\u0000\u0000\u03b9\u03ae\u0001\u0000\u0000\u0000"+
		"\u03b9\u03af\u0001\u0000\u0000\u0000\u03b9\u03b0\u0001\u0000\u0000\u0000"+
		"\u03b9\u03b1\u0001\u0000\u0000\u0000\u03b9\u03b2\u0001\u0000\u0000\u0000"+
		"\u03b9\u03b3\u0001\u0000\u0000\u0000\u03b9\u03b4\u0001\u0000\u0000\u0000"+
		"\u03b9\u03b5\u0001\u0000\u0000\u0000\u03b9\u03b6\u0001\u0000\u0000\u0000"+
		"\u03b9\u03b7\u0001\u0000\u0000\u0000\u03b9\u03b8\u0001\u0000\u0000\u0000"+
		"\u03ba\u0099\u0001\u0000\u0000\u0000\u03bb\u03bc\u0007\u000b\u0000\u0000"+
		"\u03bc\u03bd\u0003\u0098L\u0000\u03bd\u009b\u0001\u0000\u0000\u0000\u03be"+
		"\u03bf\u0005\u001c\u0000\u0000\u03bf\u03c0\u0003\u008cF\u0000\u03c0\u03c1"+
		"\u0005\u001d\u0000\u0000\u03c1\u009d\u0001\u0000\u0000\u0000\u03c2\u03c3"+
		"\u0005\u001c\u0000\u0000\u03c3\u03c4\u0003\u0094J\u0000\u03c4\u03c5\u0005"+
		"\u001d\u0000\u0000\u03c5\u009f\u0001\u0000\u0000\u0000\u03c6\u03c7\u0005"+
		"h\u0000\u0000\u03c7\u03c8\u0003h4\u0000\u03c8\u00a1\u0001\u0000\u0000"+
		"\u0000\u03c9\u03cd\u0003\u00a4R\u0000\u03ca\u03cd\u0003\u00c2a\u0000\u03cb"+
		"\u03cd\u0003\u00cae\u0000\u03cc\u03c9\u0001\u0000\u0000\u0000\u03cc\u03ca"+
		"\u0001\u0000\u0000\u0000\u03cc\u03cb\u0001\u0000\u0000\u0000\u03cd\u00a3"+
		"\u0001\u0000\u0000\u0000\u03ce\u03cf\u0003\u00bc^\u0000\u03cf\u03d1\u0005"+
		"\u001c\u0000\u0000\u03d0\u03d2\u0003\u00be_\u0000\u03d1\u03d0\u0001\u0000"+
		"\u0000\u0000\u03d1\u03d2\u0001\u0000\u0000\u0000\u03d2\u03d5\u0001\u0000"+
		"\u0000\u0000\u03d3\u03d6\u0003\u00c0`\u0000\u03d4\u03d6\u0005\u000e\u0000"+
		"\u0000\u03d5\u03d3\u0001\u0000\u0000\u0000\u03d5\u03d4\u0001\u0000\u0000"+
		"\u0000\u03d5\u03d6\u0001\u0000\u0000\u0000\u03d6\u03d7\u0001\u0000\u0000"+
		"\u0000\u03d7\u03d8\u0005\u001d\u0000\u0000\u03d8\u00a5\u0001\u0000\u0000"+
		"\u0000\u03d9\u03da\u0003\u00a8T\u0000\u03da\u03e7\u0005\u00ae\u0000\u0000"+
		"\u03db\u03dd\u0005\u001c\u0000\u0000\u03dc\u03de\u0003\u00aaU\u0000\u03dd"+
		"\u03dc\u0001\u0000\u0000\u0000\u03dd\u03de\u0001\u0000\u0000\u0000\u03de"+
		"\u03e0\u0001\u0000\u0000\u0000\u03df\u03e1\u0003\u00deo\u0000\u03e0\u03df"+
		"\u0001\u0000\u0000\u0000\u03e0\u03e1\u0001\u0000\u0000\u0000\u03e1\u03e3"+
		"\u0001\u0000\u0000\u0000\u03e2\u03e4\u0003\u00aeW\u0000\u03e3\u03e2\u0001"+
		"\u0000\u0000\u0000\u03e3\u03e4\u0001\u0000\u0000\u0000\u03e4\u03e5\u0001"+
		"\u0000\u0000\u0000\u03e5\u03e8\u0005\u001d\u0000\u0000\u03e6\u03e8\u0003"+
		"\u0080@\u0000\u03e7\u03db\u0001\u0000\u0000\u0000\u03e7\u03e6\u0001\u0000"+
		"\u0000\u0000\u03e8\u00a7\u0001\u0000\u0000\u0000\u03e9\u03ec\u0003\u00a4"+
		"R\u0000\u03ea\u03ec\u0003\u00cae\u0000\u03eb\u03e9\u0001\u0000\u0000\u0000"+
		"\u03eb\u03ea\u0001\u0000\u0000\u0000\u03ec\u00a9\u0001\u0000\u0000\u0000"+
		"\u03ed\u03ee\u0005\u00af\u0000\u0000\u03ee\u03ef\u0005q\u0000\u0000\u03ef"+
		"\u03f0\u0003\u00acV\u0000\u03f0\u00ab\u0001\u0000\u0000\u0000\u03f1\u03f6"+
		"\u0003\u008cF\u0000\u03f2\u03f3\u0005\"\u0000\u0000\u03f3\u03f5\u0003"+
		"\u008cF\u0000\u03f4\u03f2\u0001\u0000\u0000\u0000\u03f5\u03f8\u0001\u0000"+
		"\u0000\u0000\u03f6\u03f4\u0001\u0000\u0000\u0000\u03f6\u03f7\u0001\u0000"+
		"\u0000\u0000\u03f7\u00ad\u0001\u0000\u0000\u0000\u03f8\u03f6\u0001\u0000"+
		"\u0000\u0000\u03f9\u03fb\u0003\u00b0X\u0000\u03fa\u03fc\u0005i\u0000\u0000"+
		"\u03fb\u03fa\u0001\u0000\u0000\u0000\u03fb\u03fc\u0001\u0000\u0000\u0000"+
		"\u03fc\u03fd\u0001\u0000\u0000\u0000\u03fd\u0400\u0003\u00b2Y\u0000\u03fe"+
		"\u03ff\u0005a\u0000\u0000\u03ff\u0401\u0003\u00b2Y\u0000\u0400\u03fe\u0001"+
		"\u0000\u0000\u0000\u0400\u0401\u0001\u0000\u0000\u0000\u0401\u00af\u0001"+
		"\u0000\u0000\u0000\u0402\u0403\u0007\f\u0000\u0000\u0403\u00b1\u0001\u0000"+
		"\u0000\u0000\u0404\u0406\u0003\u008cF\u0000\u0405\u0404\u0001\u0000\u0000"+
		"\u0000\u0405\u0406\u0001\u0000\u0000\u0000\u0406\u0407\u0001\u0000\u0000"+
		"\u0000\u0407\u0408\u0003\u00b4Z\u0000\u0408\u00b3\u0001\u0000\u0000\u0000"+
		"\u0409\u040a\u0005\u0157\u0000\u0000\u040a\u0412\u0005\u0136\u0000\u0000"+
		"\u040b\u0412\u0005\u0136\u0000\u0000\u040c\u040d\u0005\u0095\u0000\u0000"+
		"\u040d\u0412\u0005\u0140\u0000\u0000\u040e\u0412\u0005\u0112\u0000\u0000"+
		"\u040f\u0410\u0005\u0157\u0000\u0000\u0410\u0412\u0005\u0112\u0000\u0000"+
		"\u0411\u0409\u0001\u0000\u0000\u0000\u0411\u040b\u0001\u0000\u0000\u0000"+
		"\u0411\u040c\u0001\u0000\u0000\u0000\u0411\u040e\u0001\u0000\u0000\u0000"+
		"\u0411\u040f\u0001\u0000\u0000\u0000\u0412\u00b5\u0001\u0000\u0000\u0000"+
		"\u0413\u0414\u0005\u015f\u0000\u0000\u0414\u0415\u0003\u00b8\\\u0000\u0415"+
		"\u00b7\u0001\u0000\u0000\u0000\u0416\u041b\u0003\u00ba]\u0000\u0417\u0418"+
		"\u0005\"\u0000\u0000\u0418\u041a\u0003\u00ba]\u0000\u0419\u0417\u0001"+
		"\u0000\u0000\u0000\u041a\u041d\u0001\u0000\u0000\u0000\u041b\u0419\u0001"+
		"\u0000\u0000\u0000\u041b\u041c\u0001\u0000\u0000\u0000\u041c\u00b9\u0001"+
		"\u0000\u0000\u0000\u041d\u041b\u0001\u0000\u0000\u0000\u041e\u041f\u0003"+
		"\u0080@\u0000\u041f\u0420\u0005Z\u0000\u0000\u0420\u0422\u0005\u001c\u0000"+
		"\u0000\u0421\u0423\u0003\u00aaU\u0000\u0422\u0421\u0001\u0000\u0000\u0000"+
		"\u0422\u0423\u0001\u0000\u0000\u0000\u0423\u0425\u0001\u0000\u0000\u0000"+
		"\u0424\u0426\u0003\u00deo\u0000\u0425\u0424\u0001\u0000\u0000\u0000\u0425"+
		"\u0426\u0001\u0000\u0000\u0000\u0426\u0428\u0001\u0000\u0000\u0000\u0427"+
		"\u0429\u0003\u00aeW\u0000\u0428\u0427\u0001\u0000\u0000\u0000\u0428\u0429"+
		"\u0001\u0000\u0000\u0000\u0429\u042a\u0001\u0000\u0000\u0000\u042a\u042b"+
		"\u0005\u001d\u0000\u0000\u042b\u00bb\u0001\u0000\u0000\u0000\u042c\u042d"+
		"\u0007\r\u0000\u0000\u042d\u00bd\u0001\u0000\u0000\u0000\u042e\u042f\u0005"+
		"J\u0000\u0000\u042f\u00bf\u0001\u0000\u0000\u0000\u0430\u0435\u0003\u008c"+
		"F\u0000\u0431\u0432\u0005\"\u0000\u0000\u0432\u0434\u0003\u008cF\u0000"+
		"\u0433\u0431\u0001\u0000\u0000\u0000\u0434\u0437\u0001\u0000\u0000\u0000"+
		"\u0435\u0433\u0001\u0000\u0000\u0000\u0435\u0436\u0001\u0000\u0000\u0000"+
		"\u0436\u00c1\u0001\u0000\u0000\u0000\u0437\u0435\u0001\u0000\u0000\u0000"+
		"\u0438\u0469\u0003\u00c8d\u0000\u0439\u043a\u0005@\u0000\u0000\u043a\u043b"+
		"\u0005\u001c\u0000\u0000\u043b\u043c\u0003\u0096K\u0000\u043c\u043d\u0005"+
		"j\u0000\u0000\u043d\u043e\u0003\u008cF\u0000\u043e\u043f\u0005\u001d\u0000"+
		"\u0000\u043f\u0469\u0001\u0000\u0000\u0000\u0440\u0441\u0005O\u0000\u0000"+
		"\u0441\u0442\u0005\u001c\u0000\u0000\u0442\u0443\u0003\u0096K\u0000\u0443"+
		"\u0444\u0005P\u0000\u0000\u0444\u0447\u0003\u0096K\u0000\u0445\u0446\u0005"+
		"_\u0000\u0000\u0446\u0448\u0003\u0096K\u0000\u0447\u0445\u0001\u0000\u0000"+
		"\u0000\u0447\u0448\u0001\u0000\u0000\u0000\u0448\u0449\u0001\u0000\u0000"+
		"\u0000\u0449\u044a\u0005\u001d\u0000\u0000\u044a\u0469\u0001\u0000\u0000"+
		"\u0000\u044b\u044c\u0005\u010f\u0000\u0000\u044c\u044d\u0005\u001c\u0000"+
		"\u0000\u044d\u044e\u0003\u00c6c\u0000\u044e\u044f\u0005P\u0000\u0000\u044f"+
		"\u0450\u0003\u008cF\u0000\u0450\u0451\u0005\u001d\u0000\u0000\u0451\u0469"+
		"\u0001\u0000\u0000\u0000\u0452\u0453\u0005N\u0000\u0000\u0453\u0454\u0005"+
		"\u001c\u0000\u0000\u0454\u0455\u0003\u00c4b\u0000\u0455\u0456\u0005P\u0000"+
		"\u0000\u0456\u0457\u0003\u008cF\u0000\u0457\u0458\u0005\u001d\u0000\u0000"+
		"\u0458\u0469\u0001\u0000\u0000\u0000\u0459\u045a\u0005N\u0000\u0000\u045a"+
		"\u045b\u0005\u001c\u0000\u0000\u045b\u045c\u0003\u00c4b\u0000\u045c\u045d"+
		"\u0003\u0098L\u0000\u045d\u045e\u0005P\u0000\u0000\u045e\u045f\u0003\u008c"+
		"F\u0000\u045f\u0460\u0005\u001d\u0000\u0000\u0460\u0469\u0001\u0000\u0000"+
		"\u0000\u0461\u0462\u0005N\u0000\u0000\u0462\u0463\u0005\u001c\u0000\u0000"+
		"\u0463\u0464\u0003\u0098L\u0000\u0464\u0465\u0005P\u0000\u0000\u0465\u0466"+
		"\u0003\u008cF\u0000\u0466\u0467\u0005\u001d\u0000\u0000\u0467\u0469\u0001"+
		"\u0000\u0000\u0000\u0468\u0438\u0001\u0000\u0000\u0000\u0468\u0439\u0001"+
		"\u0000\u0000\u0000\u0468\u0440\u0001\u0000\u0000\u0000\u0468\u044b\u0001"+
		"\u0000\u0000\u0000\u0468\u0452\u0001\u0000\u0000\u0000\u0468\u0459\u0001"+
		"\u0000\u0000\u0000\u0468\u0461\u0001\u0000\u0000\u0000\u0469\u00c3\u0001"+
		"\u0000\u0000\u0000\u046a\u046b\u0007\u000e\u0000\u0000\u046b\u00c5\u0001"+
		"\u0000\u0000\u0000\u046c\u046d\u0007\u000f\u0000\u0000\u046d\u00c7\u0001"+
		"\u0000\u0000\u0000\u046e\u046f\u0005M\u0000\u0000\u046f\u0472\u0005\u001c"+
		"\u0000\u0000\u0470\u0473\u0003\u008cF\u0000\u0471\u0473\u0005e\u0000\u0000"+
		"\u0472\u0470\u0001\u0000\u0000\u0000\u0472\u0471\u0001\u0000\u0000\u0000"+
		"\u0473\u0474\u0001\u0000\u0000\u0000\u0474\u0475\u0005Z\u0000\u0000\u0475"+
		"\u0476\u0003\u00e6s\u0000\u0476\u0477\u0005\u001d\u0000\u0000\u0477\u00c9"+
		"\u0001\u0000\u0000\u0000\u0478\u0479\u0003\u00ccf\u0000\u0479\u047b\u0005"+
		"\u001c\u0000\u0000\u047a\u047c\u0003\u00c0`\u0000\u047b\u047a\u0001\u0000"+
		"\u0000\u0000\u047b\u047c\u0001\u0000\u0000\u0000\u047c\u047d\u0001\u0000"+
		"\u0000\u0000\u047d\u047e\u0005\u001d\u0000\u0000\u047e\u00cb\u0001\u0000"+
		"\u0000\u0000\u047f\u048a\u0003\u0080@\u0000\u0480\u048a\u0005\\\u0000"+
		"\u0000\u0481\u048a\u0005\u00fb\u0000\u0000\u0482\u048a\u0005\u0084\u0000"+
		"\u0000\u0483\u048a\u0005\u0085\u0000\u0000\u0484\u048a\u0005\u0080\u0000"+
		"\u0000\u0485\u048a\u0005\u010f\u0000\u0000\u0486\u048a\u0005\u012c\u0000"+
		"\u0000\u0487\u048a\u0005N\u0000\u0000\u0488\u048a\u0005O\u0000\u0000\u0489"+
		"\u047f\u0001\u0000\u0000\u0000\u0489\u0480\u0001\u0000\u0000\u0000\u0489"+
		"\u0481\u0001\u0000\u0000\u0000\u0489\u0482\u0001\u0000\u0000\u0000\u0489"+
		"\u0483\u0001\u0000\u0000\u0000\u0489\u0484\u0001\u0000\u0000\u0000\u0489"+
		"\u0485\u0001\u0000\u0000\u0000\u0489\u0486\u0001\u0000\u0000\u0000\u0489"+
		"\u0487\u0001\u0000\u0000\u0000\u0489\u0488\u0001\u0000\u0000\u0000\u048a"+
		"\u00cd\u0001\u0000\u0000\u0000\u048b\u0490\u0003\u00d0h\u0000\u048c\u048d"+
		"\u0005\"\u0000\u0000\u048d\u048f\u0003\u00d0h\u0000\u048e\u048c\u0001"+
		"\u0000\u0000\u0000\u048f\u0492\u0001\u0000\u0000\u0000\u0490\u048e\u0001"+
		"\u0000\u0000\u0000\u0490\u0491\u0001\u0000\u0000\u0000\u0491\u00cf\u0001"+
		"\u0000\u0000\u0000\u0492\u0490\u0001\u0000\u0000\u0000\u0493\u0494\u0005"+
		"&\u0000\u0000\u0494\u049a\u0003\u0080@\u0000\u0495\u0497\u0005\u001c\u0000"+
		"\u0000\u0496\u0498\u0003\u00d2i\u0000\u0497\u0496\u0001\u0000\u0000\u0000"+
		"\u0497\u0498\u0001\u0000\u0000\u0000\u0498\u0499\u0001\u0000\u0000\u0000"+
		"\u0499\u049b\u0005\u001d\u0000\u0000\u049a\u0495\u0001\u0000\u0000\u0000"+
		"\u049a\u049b\u0001\u0000\u0000\u0000\u049b\u00d1\u0001\u0000\u0000\u0000"+
		"\u049c\u04a1\u0003p8\u0000\u049d\u049e\u0005\"\u0000\u0000\u049e\u04a0"+
		"\u0003p8\u0000\u049f\u049d\u0001\u0000\u0000\u0000\u04a0\u04a3\u0001\u0000"+
		"\u0000\u0000\u04a1\u049f\u0001\u0000\u0000\u0000\u04a1\u04a2\u0001\u0000"+
		"\u0000\u0000\u04a2\u00d3\u0001\u0000\u0000\u0000\u04a3\u04a1\u0001\u0000"+
		"\u0000\u0000\u04a4\u04a6\u0005K\u0000\u0000\u04a5\u04a7\u0003\u0098L\u0000"+
		"\u04a6\u04a5\u0001\u0000\u0000\u0000\u04a6\u04a7\u0001\u0000\u0000\u0000"+
		"\u04a7\u04a8\u0001\u0000\u0000\u0000\u04a8\u04ab\u0003\u00d6k\u0000\u04a9"+
		"\u04aa\u0005]\u0000\u0000\u04aa\u04ac\u0003\u008cF\u0000\u04ab\u04a9\u0001"+
		"\u0000\u0000\u0000\u04ab\u04ac\u0001\u0000\u0000\u0000\u04ac\u04ad\u0001"+
		"\u0000\u0000\u0000\u04ad\u04ae\u0005\u0107\u0000\u0000\u04ae\u00d5\u0001"+
		"\u0000\u0000\u0000\u04af\u04b1\u0003\u00d8l\u0000\u04b0\u04af\u0001\u0000"+
		"\u0000\u0000\u04b1\u04b2\u0001\u0000\u0000\u0000\u04b2\u04b0\u0001\u0000"+
		"\u0000\u0000\u04b2\u04b3\u0001\u0000\u0000\u0000\u04b3\u00d7\u0001\u0000"+
		"\u0000\u0000\u04b4\u04b5\u0005L\u0000\u0000\u04b5\u04b6\u0003\u008cF\u0000"+
		"\u04b6\u04b7\u0005^\u0000\u0000\u04b7\u04b8\u0003\u008cF\u0000\u04b8\u00d9"+
		"\u0001\u0000\u0000\u0000\u04b9\u04ba\u0005\u0080\u0000\u0000\u04ba\u04bb"+
		"\u0003\u008cF\u0000\u04bb\u04bc\u0003\u00dcn\u0000\u04bc\u04c0\u0001\u0000"+
		"\u0000\u0000\u04bd\u04be\u0005\u0080\u0000\u0000\u04be\u04c0\u0005\u0169"+
		"\u0000\u0000\u04bf\u04b9\u0001\u0000\u0000\u0000\u04bf\u04bd\u0001\u0000"+
		"\u0000\u0000\u04c0\u00db\u0001\u0000\u0000\u0000\u04c1\u04c2\u0007\u0010"+
		"\u0000\u0000\u04c2\u00dd\u0001\u0000\u0000\u0000\u04c3\u04c4\u0005o\u0000"+
		"\u0000\u04c4\u04c5\u0005q\u0000\u0000\u04c5\u04c6\u0003\u00e0p\u0000\u04c6"+
		"\u00df\u0001\u0000\u0000\u0000\u04c7\u04cc\u0003\u00e2q\u0000\u04c8\u04c9"+
		"\u0005\"\u0000\u0000\u04c9\u04cb\u0003\u00e2q\u0000\u04ca\u04c8\u0001"+
		"\u0000\u0000\u0000\u04cb\u04ce\u0001\u0000\u0000\u0000\u04cc\u04ca\u0001"+
		"\u0000\u0000\u0000\u04cc\u04cd\u0001\u0000\u0000\u0000\u04cd\u00e1\u0001"+
		"\u0000\u0000\u0000\u04ce\u04cc\u0001\u0000\u0000\u0000\u04cf\u04d1\u0003"+
		"\u008cF\u0000\u04d0\u04d2\u0007\u0011\u0000\u0000\u04d1\u04d0\u0001\u0000"+
		"\u0000\u0000\u04d1\u04d2\u0001\u0000\u0000\u0000\u04d2\u04d5\u0001\u0000"+
		"\u0000\u0000\u04d3\u04d4\u0005\u0123\u0000\u0000\u04d4\u04d6\u0007\u0012"+
		"\u0000\u0000\u04d5\u04d3\u0001\u0000\u0000\u0000\u04d5\u04d6\u0001\u0000"+
		"\u0000\u0000\u04d6\u00e3\u0001\u0000\u0000\u0000\u04d7\u04d8\u0005\u0163"+
		"\u0000\u0000\u04d8\u04da\u0005\u001c\u0000\u0000\u04d9\u04db\u0003\u00c0"+
		"`\u0000\u04da\u04d9\u0001\u0000\u0000\u0000\u04da\u04db\u0001\u0000\u0000"+
		"\u0000\u04db\u04dc\u0001\u0000\u0000\u0000\u04dc\u04e5\u0005\u001d\u0000"+
		"\u0000\u04dd\u04de\u0005\u0164\u0000\u0000\u04de\u04e0\u0005\u001c\u0000"+
		"\u0000\u04df\u04e1\u0003\u00c0`\u0000\u04e0\u04df\u0001\u0000\u0000\u0000"+
		"\u04e0\u04e1\u0001\u0000\u0000\u0000\u04e1\u04e2\u0001\u0000\u0000\u0000"+
		"\u04e2\u04e5\u0005\u001d\u0000\u0000\u04e3\u04e5\u0003\u008cF\u0000\u04e4"+
		"\u04d7\u0001\u0000\u0000\u0000\u04e4\u04dd\u0001\u0000\u0000\u0000\u04e4"+
		"\u04e3\u0001\u0000\u0000\u0000\u04e5\u00e5\u0001\u0000\u0000\u0000\u04e6"+
		"\u04ee\u0003\u00e8t\u0000\u04e7\u04e8\u0005\u001c\u0000\u0000\u04e8\u04eb"+
		"\u0005\u016a\u0000\u0000\u04e9\u04ea\u0005\"\u0000\u0000\u04ea\u04ec\u0005"+
		"\u016a\u0000\u0000\u04eb\u04e9\u0001\u0000\u0000\u0000\u04eb\u04ec\u0001"+
		"\u0000\u0000\u0000\u04ec\u04ed\u0001\u0000\u0000\u0000\u04ed\u04ef\u0005"+
		"\u001d\u0000\u0000\u04ee\u04e7\u0001\u0000\u0000\u0000\u04ee\u04ef\u0001"+
		"\u0000\u0000\u0000\u04ef\u04f1\u0001\u0000\u0000\u0000\u04f0\u04f2\u0003"+
		"\u00eau\u0000\u04f1\u04f0\u0001\u0000\u0000\u0000\u04f1\u04f2\u0001\u0000"+
		"\u0000\u0000\u04f2\u04f4\u0001\u0000\u0000\u0000\u04f3\u04f5\u0003\u00ec"+
		"v\u0000\u04f4\u04f3\u0001\u0000\u0000\u0000\u04f4\u04f5\u0001\u0000\u0000"+
		"\u0000\u04f5\u00e7\u0001\u0000\u0000\u0000\u04f6\u050e\u0005}\u0000\u0000"+
		"\u04f7\u050e\u0005\u00ab\u0000\u0000\u04f8\u050e\u0005\u012d\u0000\u0000"+
		"\u04f9\u050e\u0005\u00a9\u0000\u0000\u04fa\u050e\u0005\u00a7\u0000\u0000"+
		"\u04fb\u050e\u0005\u0147\u0000\u0000\u04fc\u050e\u0005\u00ac\u0000\u0000"+
		"\u04fd\u050e\u0005\u00a8\u0000\u0000\u04fe\u050e\u0005\u0081\u0000\u0000"+
		"\u04ff\u050e\u0005\u0082\u0000\u0000\u0500\u050e\u0005\u0083\u0000\u0000"+
		"\u0501\u050e\u0005{\u0000\u0000\u0502\u0503\u0005\u0082\u0000\u0000\u0503"+
		"\u0504\u0005H\u0000\u0000\u0504\u0505\u0005\u00a0\u0000\u0000\u0505\u0506"+
		"\u0005\u0082\u0000\u0000\u0506\u050e\u0005\u0162\u0000\u0000\u0507\u0508"+
		"\u0005\u0083\u0000\u0000\u0508\u0509\u0005H\u0000\u0000\u0509\u050a\u0005"+
		"\u00a0\u0000\u0000\u050a\u050b\u0005\u0082\u0000\u0000\u050b\u050e\u0005"+
		"\u0162\u0000\u0000\u050c\u050e\u0003\u0080@\u0000\u050d\u04f6\u0001\u0000"+
		"\u0000\u0000\u050d\u04f7\u0001\u0000\u0000\u0000\u050d\u04f8\u0001\u0000"+
		"\u0000\u0000\u050d\u04f9\u0001\u0000\u0000\u0000\u050d\u04fa\u0001\u0000"+
		"\u0000\u0000\u050d\u04fb\u0001\u0000\u0000\u0000\u050d\u04fc\u0001\u0000"+
		"\u0000\u0000\u050d\u04fd\u0001\u0000\u0000\u0000\u050d\u04fe\u0001\u0000"+
		"\u0000\u0000\u050d\u04ff\u0001\u0000\u0000\u0000\u050d\u0500\u0001\u0000"+
		"\u0000\u0000\u050d\u0501\u0001\u0000\u0000\u0000\u050d\u0502\u0001\u0000"+
		"\u0000\u0000\u050d\u0507\u0001\u0000\u0000\u0000\u050d\u050c\u0001\u0000"+
		"\u0000\u0000\u050e\u00e9\u0001\u0000\u0000\u0000\u050f\u0510\u0007\u0013"+
		"\u0000\u0000\u0510\u0512\u00057\u0000\u0000\u0511\u0513\u0005\u0015\u0000"+
		"\u0000\u0512\u0511\u0001\u0000\u0000\u0000\u0512\u0513\u0001\u0000\u0000"+
		"\u0000\u0513\u0514\u0001\u0000\u0000\u0000\u0514\u0515\u0005\u016d\u0000"+
		"\u0000\u0515\u00eb\u0001\u0000\u0000\u0000\u0516\u0518\u0005\u00f2\u0000"+
		"\u0000\u0517\u0519\u0005\u0015\u0000\u0000\u0518\u0517\u0001\u0000\u0000"+
		"\u0000\u0518\u0519\u0001\u0000\u0000\u0000\u0519\u051a\u0001\u0000\u0000"+
		"\u0000\u051a\u051b\u0005\u0169\u0000\u0000\u051b\u00ed\u0001\u0000\u0000"+
		"\u0000\u0095\u00f5\u00f9\u00fd\u0105\u0109\u0110\u011a\u011d\u0130\u0135"+
		"\u0138\u013c\u0141\u0143\u014b\u015b\u0161\u0167\u016a\u016d\u0170\u0176"+
		"\u0184\u018d\u0191\u0198\u019d\u01a0\u01a4\u01a8\u01ab\u01ae\u01b1\u01b4"+
		"\u01b7\u01ba\u01bd\u01c2\u01c4\u01cb\u01d0\u01d4\u01d7\u01da\u01e3\u01e7"+
		"\u01f0\u01fa\u0201\u0205\u020a\u020d\u0210\u021b\u021e\u0221\u022e\u0231"+
		"\u0234\u023f\u0242\u0245\u0248\u024e\u0257\u025b\u0262\u026b\u0275\u0279"+
		"\u027c\u0282\u0288\u028e\u02a1\u02ab\u02bc\u02c0\u02c4\u02d9\u02ec\u02f3"+
		"\u02fa\u0301\u0308\u030f\u0317\u0319\u0322\u0333\u034d\u034f\u0356\u035d"+
		"\u0366\u036f\u0375\u0379\u037f\u0382\u0389\u0394\u03a8\u03aa\u03b9\u03cc"+
		"\u03d1\u03d5\u03dd\u03e0\u03e3\u03e7\u03eb\u03f6\u03fb\u0400\u0405\u0411"+
		"\u041b\u0422\u0425\u0428\u0435\u0447\u0468\u0472\u047b\u0489\u0490\u0497"+
		"\u049a\u04a1\u04a6\u04ab\u04b2\u04bf\u04cc\u04d1\u04d5\u04da\u04e0\u04e4"+
		"\u04eb\u04ee\u04f1\u04f4\u050d\u0512\u0518";
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