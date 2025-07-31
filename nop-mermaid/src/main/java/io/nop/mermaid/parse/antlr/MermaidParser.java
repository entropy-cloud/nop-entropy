// Nop Generated from Mermaid.g4 by ANTLR 4.13.0
package io.nop.mermaid.parse.antlr;
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
public class MermaidParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.13.0", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		FLOWCHART=1, SEQUENCE=2, CLASS=3, STATE=4, GANTT=5, PIE=6, GIT=7, ER=8, 
		JOURNEY=9, TB=10, BT=11, LR=12, RL=13, ROUND=14, STADIUM=15, SUBROUTINE=16, 
		CYLINDER=17, CIRCLE=18, ASYMMETRIC=19, RHOMBUS=20, HEXAGON=21, PARALLELOGRAM=22, 
		TRAPEZOID=23, DOUBLE_CIRCLE=24, ARROW=25, OPEN_ARROW=26, DOTTED=27, THICK=28, 
		PARTICIPANT=29, AS=30, TASK=31, SUBGRAPH=32, STYLE=33, STATIC=34, Visibility=35, 
		LPAREN=36, RPAREN=37, LCURLY=38, RCURLY=39, LBRACE=40, RBRACE=41, COLON=42, 
		SEMI=43, COMMA=44, DOT=45, PIPE=46, StringLiteral_=47, NumberLiteral_=48, 
		Identifier_=49, COMMENT=50, WS=51, DIRECTION=52, Identifier=53;
	public static final int
		RULE_mermaidDocument = 0, RULE_mermaidDiagramType_ = 1, RULE_mermaidStatements_ = 2, 
		RULE_mermaidStatement = 3, RULE_mermaidDirectionStatement = 4, RULE_mermaidDirection_ = 5, 
		RULE_mermaidComment = 6, RULE_mermaidFlowNode = 7, RULE_mermaidNodeShape_ = 8, 
		RULE_mermaidFlowEdge = 9, RULE_mermaidEdgeType_ = 10, RULE_mermaidFlowSubgraph = 11, 
		RULE_mermaidParticipant = 12, RULE_mermaidSequenceMessage = 13, RULE_mermaidClassNode = 14, 
		RULE_mermaidClassMembers_ = 15, RULE_mermaidClassMember = 16, RULE_mermaidStateNode = 17, 
		RULE_mermaidGanttTask = 18, RULE_mermaidPieItem = 19, RULE_mermaidStyleStatement = 20, 
		RULE_mermaidStyleAttributes_ = 21, RULE_mermaidStyleAttribute = 22;
	private static String[] makeRuleNames() {
		return new String[] {
			"mermaidDocument", "mermaidDiagramType_", "mermaidStatements_", "mermaidStatement", 
			"mermaidDirectionStatement", "mermaidDirection_", "mermaidComment", "mermaidFlowNode", 
			"mermaidNodeShape_", "mermaidFlowEdge", "mermaidEdgeType_", "mermaidFlowSubgraph", 
			"mermaidParticipant", "mermaidSequenceMessage", "mermaidClassNode", "mermaidClassMembers_", 
			"mermaidClassMember", "mermaidStateNode", "mermaidGanttTask", "mermaidPieItem", 
			"mermaidStyleStatement", "mermaidStyleAttributes_", "mermaidStyleAttribute"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, null, "'sequenceDiagram'", "'classDiagram'", "'stateDiagram'", 
			"'gantt'", "'pie'", "'git'", "'er'", "'journey'", null, "'BT'", "'LR'", 
			"'RL'", "'round'", "'stadium'", "'subroutine'", "'cylinder'", "'circle'", 
			"'asymmetric'", "'rhombus'", "'hexagon'", "'parallelogram'", "'trapezoid'", 
			"'double_circle'", "'-->'", "'->>'", "'-.->'", "'==>'", "'participant'", 
			"'as'", "'task'", "'subgraph'", "'style'", "'static'", null, "'('", "')'", 
			"'{'", "'}'", "'['", "']'", "':'", "';'", "','", "'.'", "'|'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "FLOWCHART", "SEQUENCE", "CLASS", "STATE", "GANTT", "PIE", "GIT", 
			"ER", "JOURNEY", "TB", "BT", "LR", "RL", "ROUND", "STADIUM", "SUBROUTINE", 
			"CYLINDER", "CIRCLE", "ASYMMETRIC", "RHOMBUS", "HEXAGON", "PARALLELOGRAM", 
			"TRAPEZOID", "DOUBLE_CIRCLE", "ARROW", "OPEN_ARROW", "DOTTED", "THICK", 
			"PARTICIPANT", "AS", "TASK", "SUBGRAPH", "STYLE", "STATIC", "Visibility", 
			"LPAREN", "RPAREN", "LCURLY", "RCURLY", "LBRACE", "RBRACE", "COLON", 
			"SEMI", "COMMA", "DOT", "PIPE", "StringLiteral_", "NumberLiteral_", "Identifier_", 
			"COMMENT", "WS", "DIRECTION", "Identifier"
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
	public String getGrammarFileName() { return "Mermaid.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public MermaidParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class MermaidDocumentContext extends ParserRuleContext {
		public MermaidDiagramType_Context type;
		public MermaidStatements_Context statements;
		public MermaidDiagramType_Context mermaidDiagramType_() {
			return getRuleContext(MermaidDiagramType_Context.class,0);
		}
		public MermaidStatements_Context mermaidStatements_() {
			return getRuleContext(MermaidStatements_Context.class,0);
		}
		public MermaidDocumentContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_mermaidDocument; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).enterMermaidDocument(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).exitMermaidDocument(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MermaidVisitor ) return ((MermaidVisitor<? extends T>)visitor).visitMermaidDocument(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MermaidDocumentContext mermaidDocument() throws RecognitionException {
		MermaidDocumentContext _localctx = new MermaidDocumentContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_mermaidDocument);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(46);
			((MermaidDocumentContext)_localctx).type = mermaidDiagramType_();
			setState(47);
			((MermaidDocumentContext)_localctx).statements = mermaidStatements_();
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
	public static class MermaidDiagramType_Context extends ParserRuleContext {
		public TerminalNode FLOWCHART() { return getToken(MermaidParser.FLOWCHART, 0); }
		public TerminalNode SEQUENCE() { return getToken(MermaidParser.SEQUENCE, 0); }
		public TerminalNode CLASS() { return getToken(MermaidParser.CLASS, 0); }
		public TerminalNode STATE() { return getToken(MermaidParser.STATE, 0); }
		public TerminalNode GANTT() { return getToken(MermaidParser.GANTT, 0); }
		public TerminalNode PIE() { return getToken(MermaidParser.PIE, 0); }
		public TerminalNode GIT() { return getToken(MermaidParser.GIT, 0); }
		public TerminalNode ER() { return getToken(MermaidParser.ER, 0); }
		public TerminalNode JOURNEY() { return getToken(MermaidParser.JOURNEY, 0); }
		public MermaidDiagramType_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_mermaidDiagramType_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).enterMermaidDiagramType_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).exitMermaidDiagramType_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MermaidVisitor ) return ((MermaidVisitor<? extends T>)visitor).visitMermaidDiagramType_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MermaidDiagramType_Context mermaidDiagramType_() throws RecognitionException {
		MermaidDiagramType_Context _localctx = new MermaidDiagramType_Context(_ctx, getState());
		enterRule(_localctx, 2, RULE_mermaidDiagramType_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(49);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 1022L) != 0)) ) {
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
	public static class MermaidStatements_Context extends ParserRuleContext {
		public MermaidStatementContext e;
		public List<MermaidStatementContext> mermaidStatement() {
			return getRuleContexts(MermaidStatementContext.class);
		}
		public MermaidStatementContext mermaidStatement(int i) {
			return getRuleContext(MermaidStatementContext.class,i);
		}
		public MermaidStatements_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_mermaidStatements_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).enterMermaidStatements_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).exitMermaidStatements_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MermaidVisitor ) return ((MermaidVisitor<? extends T>)visitor).visitMermaidStatements_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MermaidStatements_Context mermaidStatements_() throws RecognitionException {
		MermaidStatements_Context _localctx = new MermaidStatements_Context(_ctx, getState());
		enterRule(_localctx, 4, RULE_mermaidStatements_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(52); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(51);
				((MermaidStatements_Context)_localctx).e = mermaidStatement();
				}
				}
				setState(54); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( (((_la) & ~0x3f) == 0 && ((1L << _la) & 6192465056890968L) != 0) );
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
	public static class MermaidStatementContext extends ParserRuleContext {
		public MermaidDirectionStatementContext mermaidDirectionStatement() {
			return getRuleContext(MermaidDirectionStatementContext.class,0);
		}
		public MermaidCommentContext mermaidComment() {
			return getRuleContext(MermaidCommentContext.class,0);
		}
		public MermaidFlowNodeContext mermaidFlowNode() {
			return getRuleContext(MermaidFlowNodeContext.class,0);
		}
		public MermaidFlowEdgeContext mermaidFlowEdge() {
			return getRuleContext(MermaidFlowEdgeContext.class,0);
		}
		public MermaidFlowSubgraphContext mermaidFlowSubgraph() {
			return getRuleContext(MermaidFlowSubgraphContext.class,0);
		}
		public MermaidParticipantContext mermaidParticipant() {
			return getRuleContext(MermaidParticipantContext.class,0);
		}
		public MermaidSequenceMessageContext mermaidSequenceMessage() {
			return getRuleContext(MermaidSequenceMessageContext.class,0);
		}
		public MermaidClassNodeContext mermaidClassNode() {
			return getRuleContext(MermaidClassNodeContext.class,0);
		}
		public MermaidStateNodeContext mermaidStateNode() {
			return getRuleContext(MermaidStateNodeContext.class,0);
		}
		public MermaidGanttTaskContext mermaidGanttTask() {
			return getRuleContext(MermaidGanttTaskContext.class,0);
		}
		public MermaidPieItemContext mermaidPieItem() {
			return getRuleContext(MermaidPieItemContext.class,0);
		}
		public MermaidStyleStatementContext mermaidStyleStatement() {
			return getRuleContext(MermaidStyleStatementContext.class,0);
		}
		public MermaidStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_mermaidStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).enterMermaidStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).exitMermaidStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MermaidVisitor ) return ((MermaidVisitor<? extends T>)visitor).visitMermaidStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MermaidStatementContext mermaidStatement() throws RecognitionException {
		MermaidStatementContext _localctx = new MermaidStatementContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_mermaidStatement);
		try {
			setState(68);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,1,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(56);
				mermaidDirectionStatement();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(57);
				mermaidComment();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(58);
				mermaidFlowNode();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(59);
				mermaidFlowEdge();
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(60);
				mermaidFlowSubgraph();
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(61);
				mermaidParticipant();
				}
				break;
			case 7:
				enterOuterAlt(_localctx, 7);
				{
				setState(62);
				mermaidSequenceMessage();
				}
				break;
			case 8:
				enterOuterAlt(_localctx, 8);
				{
				setState(63);
				mermaidClassNode();
				}
				break;
			case 9:
				enterOuterAlt(_localctx, 9);
				{
				setState(64);
				mermaidStateNode();
				}
				break;
			case 10:
				enterOuterAlt(_localctx, 10);
				{
				setState(65);
				mermaidGanttTask();
				}
				break;
			case 11:
				enterOuterAlt(_localctx, 11);
				{
				setState(66);
				mermaidPieItem();
				}
				break;
			case 12:
				enterOuterAlt(_localctx, 12);
				{
				setState(67);
				mermaidStyleStatement();
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
	public static class MermaidDirectionStatementContext extends ParserRuleContext {
		public MermaidDirection_Context direction;
		public TerminalNode DIRECTION() { return getToken(MermaidParser.DIRECTION, 0); }
		public MermaidDirection_Context mermaidDirection_() {
			return getRuleContext(MermaidDirection_Context.class,0);
		}
		public MermaidDirectionStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_mermaidDirectionStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).enterMermaidDirectionStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).exitMermaidDirectionStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MermaidVisitor ) return ((MermaidVisitor<? extends T>)visitor).visitMermaidDirectionStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MermaidDirectionStatementContext mermaidDirectionStatement() throws RecognitionException {
		MermaidDirectionStatementContext _localctx = new MermaidDirectionStatementContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_mermaidDirectionStatement);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(70);
			match(DIRECTION);
			setState(71);
			((MermaidDirectionStatementContext)_localctx).direction = mermaidDirection_();
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
	public static class MermaidDirection_Context extends ParserRuleContext {
		public TerminalNode TB() { return getToken(MermaidParser.TB, 0); }
		public TerminalNode BT() { return getToken(MermaidParser.BT, 0); }
		public TerminalNode LR() { return getToken(MermaidParser.LR, 0); }
		public TerminalNode RL() { return getToken(MermaidParser.RL, 0); }
		public MermaidDirection_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_mermaidDirection_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).enterMermaidDirection_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).exitMermaidDirection_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MermaidVisitor ) return ((MermaidVisitor<? extends T>)visitor).visitMermaidDirection_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MermaidDirection_Context mermaidDirection_() throws RecognitionException {
		MermaidDirection_Context _localctx = new MermaidDirection_Context(_ctx, getState());
		enterRule(_localctx, 10, RULE_mermaidDirection_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(73);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 15360L) != 0)) ) {
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
	public static class MermaidCommentContext extends ParserRuleContext {
		public Token content;
		public TerminalNode COMMENT() { return getToken(MermaidParser.COMMENT, 0); }
		public TerminalNode StringLiteral_() { return getToken(MermaidParser.StringLiteral_, 0); }
		public MermaidCommentContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_mermaidComment; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).enterMermaidComment(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).exitMermaidComment(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MermaidVisitor ) return ((MermaidVisitor<? extends T>)visitor).visitMermaidComment(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MermaidCommentContext mermaidComment() throws RecognitionException {
		MermaidCommentContext _localctx = new MermaidCommentContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_mermaidComment);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(75);
			match(COMMENT);
			setState(76);
			((MermaidCommentContext)_localctx).content = match(StringLiteral_);
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
	public static class MermaidFlowNodeContext extends ParserRuleContext {
		public Token id;
		public Token text;
		public MermaidNodeShape_Context shape;
		public TerminalNode Identifier_() { return getToken(MermaidParser.Identifier_, 0); }
		public TerminalNode LPAREN() { return getToken(MermaidParser.LPAREN, 0); }
		public TerminalNode RPAREN() { return getToken(MermaidParser.RPAREN, 0); }
		public MermaidNodeShape_Context mermaidNodeShape_() {
			return getRuleContext(MermaidNodeShape_Context.class,0);
		}
		public TerminalNode StringLiteral_() { return getToken(MermaidParser.StringLiteral_, 0); }
		public MermaidFlowNodeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_mermaidFlowNode; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).enterMermaidFlowNode(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).exitMermaidFlowNode(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MermaidVisitor ) return ((MermaidVisitor<? extends T>)visitor).visitMermaidFlowNode(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MermaidFlowNodeContext mermaidFlowNode() throws RecognitionException {
		MermaidFlowNodeContext _localctx = new MermaidFlowNodeContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_mermaidFlowNode);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(78);
			((MermaidFlowNodeContext)_localctx).id = match(Identifier_);
			setState(84);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LPAREN) {
				{
				setState(79);
				match(LPAREN);
				setState(81);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==StringLiteral_) {
					{
					setState(80);
					((MermaidFlowNodeContext)_localctx).text = match(StringLiteral_);
					}
				}

				setState(83);
				match(RPAREN);
				}
			}

			setState(87);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 33538048L) != 0)) {
				{
				setState(86);
				((MermaidFlowNodeContext)_localctx).shape = mermaidNodeShape_();
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
	public static class MermaidNodeShape_Context extends ParserRuleContext {
		public TerminalNode ROUND() { return getToken(MermaidParser.ROUND, 0); }
		public TerminalNode STADIUM() { return getToken(MermaidParser.STADIUM, 0); }
		public TerminalNode SUBROUTINE() { return getToken(MermaidParser.SUBROUTINE, 0); }
		public TerminalNode CYLINDER() { return getToken(MermaidParser.CYLINDER, 0); }
		public TerminalNode CIRCLE() { return getToken(MermaidParser.CIRCLE, 0); }
		public TerminalNode ASYMMETRIC() { return getToken(MermaidParser.ASYMMETRIC, 0); }
		public TerminalNode RHOMBUS() { return getToken(MermaidParser.RHOMBUS, 0); }
		public TerminalNode HEXAGON() { return getToken(MermaidParser.HEXAGON, 0); }
		public TerminalNode PARALLELOGRAM() { return getToken(MermaidParser.PARALLELOGRAM, 0); }
		public TerminalNode TRAPEZOID() { return getToken(MermaidParser.TRAPEZOID, 0); }
		public TerminalNode DOUBLE_CIRCLE() { return getToken(MermaidParser.DOUBLE_CIRCLE, 0); }
		public MermaidNodeShape_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_mermaidNodeShape_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).enterMermaidNodeShape_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).exitMermaidNodeShape_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MermaidVisitor ) return ((MermaidVisitor<? extends T>)visitor).visitMermaidNodeShape_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MermaidNodeShape_Context mermaidNodeShape_() throws RecognitionException {
		MermaidNodeShape_Context _localctx = new MermaidNodeShape_Context(_ctx, getState());
		enterRule(_localctx, 16, RULE_mermaidNodeShape_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(89);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 33538048L) != 0)) ) {
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
	public static class MermaidFlowEdgeContext extends ParserRuleContext {
		public Token from;
		public MermaidEdgeType_Context edgeType;
		public Token label;
		public Token to;
		public TerminalNode Identifier_() { return getToken(MermaidParser.Identifier_, 0); }
		public TerminalNode Identifier() { return getToken(MermaidParser.Identifier, 0); }
		public MermaidEdgeType_Context mermaidEdgeType_() {
			return getRuleContext(MermaidEdgeType_Context.class,0);
		}
		public TerminalNode StringLiteral_() { return getToken(MermaidParser.StringLiteral_, 0); }
		public MermaidFlowEdgeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_mermaidFlowEdge; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).enterMermaidFlowEdge(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).exitMermaidFlowEdge(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MermaidVisitor ) return ((MermaidVisitor<? extends T>)visitor).visitMermaidFlowEdge(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MermaidFlowEdgeContext mermaidFlowEdge() throws RecognitionException {
		MermaidFlowEdgeContext _localctx = new MermaidFlowEdgeContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_mermaidFlowEdge);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(91);
			((MermaidFlowEdgeContext)_localctx).from = match(Identifier_);
			setState(93);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 503316480L) != 0)) {
				{
				setState(92);
				((MermaidFlowEdgeContext)_localctx).edgeType = mermaidEdgeType_();
				}
			}

			setState(96);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==StringLiteral_) {
				{
				setState(95);
				((MermaidFlowEdgeContext)_localctx).label = match(StringLiteral_);
				}
			}

			setState(98);
			((MermaidFlowEdgeContext)_localctx).to = match(Identifier);
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
	public static class MermaidEdgeType_Context extends ParserRuleContext {
		public TerminalNode ARROW() { return getToken(MermaidParser.ARROW, 0); }
		public TerminalNode OPEN_ARROW() { return getToken(MermaidParser.OPEN_ARROW, 0); }
		public TerminalNode DOTTED() { return getToken(MermaidParser.DOTTED, 0); }
		public TerminalNode THICK() { return getToken(MermaidParser.THICK, 0); }
		public MermaidEdgeType_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_mermaidEdgeType_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).enterMermaidEdgeType_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).exitMermaidEdgeType_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MermaidVisitor ) return ((MermaidVisitor<? extends T>)visitor).visitMermaidEdgeType_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MermaidEdgeType_Context mermaidEdgeType_() throws RecognitionException {
		MermaidEdgeType_Context _localctx = new MermaidEdgeType_Context(_ctx, getState());
		enterRule(_localctx, 20, RULE_mermaidEdgeType_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(100);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 503316480L) != 0)) ) {
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
	public static class MermaidFlowSubgraphContext extends ParserRuleContext {
		public Token id;
		public Token title;
		public MermaidStatements_Context statements;
		public TerminalNode SUBGRAPH() { return getToken(MermaidParser.SUBGRAPH, 0); }
		public TerminalNode LBRACE() { return getToken(MermaidParser.LBRACE, 0); }
		public TerminalNode RBRACE() { return getToken(MermaidParser.RBRACE, 0); }
		public TerminalNode Identifier_() { return getToken(MermaidParser.Identifier_, 0); }
		public MermaidStatements_Context mermaidStatements_() {
			return getRuleContext(MermaidStatements_Context.class,0);
		}
		public TerminalNode StringLiteral_() { return getToken(MermaidParser.StringLiteral_, 0); }
		public MermaidFlowSubgraphContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_mermaidFlowSubgraph; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).enterMermaidFlowSubgraph(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).exitMermaidFlowSubgraph(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MermaidVisitor ) return ((MermaidVisitor<? extends T>)visitor).visitMermaidFlowSubgraph(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MermaidFlowSubgraphContext mermaidFlowSubgraph() throws RecognitionException {
		MermaidFlowSubgraphContext _localctx = new MermaidFlowSubgraphContext(_ctx, getState());
		enterRule(_localctx, 22, RULE_mermaidFlowSubgraph);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(102);
			match(SUBGRAPH);
			setState(103);
			((MermaidFlowSubgraphContext)_localctx).id = match(Identifier_);
			setState(105);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==StringLiteral_) {
				{
				setState(104);
				((MermaidFlowSubgraphContext)_localctx).title = match(StringLiteral_);
				}
			}

			setState(107);
			match(LBRACE);
			setState(108);
			((MermaidFlowSubgraphContext)_localctx).statements = mermaidStatements_();
			setState(109);
			match(RBRACE);
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
	public static class MermaidParticipantContext extends ParserRuleContext {
		public Token name;
		public Token alias;
		public TerminalNode PARTICIPANT() { return getToken(MermaidParser.PARTICIPANT, 0); }
		public TerminalNode Identifier_() { return getToken(MermaidParser.Identifier_, 0); }
		public TerminalNode AS() { return getToken(MermaidParser.AS, 0); }
		public TerminalNode StringLiteral_() { return getToken(MermaidParser.StringLiteral_, 0); }
		public MermaidParticipantContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_mermaidParticipant; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).enterMermaidParticipant(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).exitMermaidParticipant(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MermaidVisitor ) return ((MermaidVisitor<? extends T>)visitor).visitMermaidParticipant(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MermaidParticipantContext mermaidParticipant() throws RecognitionException {
		MermaidParticipantContext _localctx = new MermaidParticipantContext(_ctx, getState());
		enterRule(_localctx, 24, RULE_mermaidParticipant);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(111);
			match(PARTICIPANT);
			setState(112);
			((MermaidParticipantContext)_localctx).name = match(Identifier_);
			setState(115);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==AS) {
				{
				setState(113);
				match(AS);
				setState(114);
				((MermaidParticipantContext)_localctx).alias = match(StringLiteral_);
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
	public static class MermaidSequenceMessageContext extends ParserRuleContext {
		public Token from;
		public MermaidEdgeType_Context edgeType;
		public Token message;
		public Token to;
		public TerminalNode Identifier_() { return getToken(MermaidParser.Identifier_, 0); }
		public TerminalNode Identifier() { return getToken(MermaidParser.Identifier, 0); }
		public MermaidEdgeType_Context mermaidEdgeType_() {
			return getRuleContext(MermaidEdgeType_Context.class,0);
		}
		public TerminalNode StringLiteral_() { return getToken(MermaidParser.StringLiteral_, 0); }
		public MermaidSequenceMessageContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_mermaidSequenceMessage; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).enterMermaidSequenceMessage(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).exitMermaidSequenceMessage(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MermaidVisitor ) return ((MermaidVisitor<? extends T>)visitor).visitMermaidSequenceMessage(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MermaidSequenceMessageContext mermaidSequenceMessage() throws RecognitionException {
		MermaidSequenceMessageContext _localctx = new MermaidSequenceMessageContext(_ctx, getState());
		enterRule(_localctx, 26, RULE_mermaidSequenceMessage);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(117);
			((MermaidSequenceMessageContext)_localctx).from = match(Identifier_);
			setState(119);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 503316480L) != 0)) {
				{
				setState(118);
				((MermaidSequenceMessageContext)_localctx).edgeType = mermaidEdgeType_();
				}
			}

			setState(122);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==StringLiteral_) {
				{
				setState(121);
				((MermaidSequenceMessageContext)_localctx).message = match(StringLiteral_);
				}
			}

			setState(124);
			((MermaidSequenceMessageContext)_localctx).to = match(Identifier);
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
	public static class MermaidClassNodeContext extends ParserRuleContext {
		public Token className;
		public MermaidClassMembers_Context members;
		public TerminalNode CLASS() { return getToken(MermaidParser.CLASS, 0); }
		public TerminalNode LBRACE() { return getToken(MermaidParser.LBRACE, 0); }
		public TerminalNode RBRACE() { return getToken(MermaidParser.RBRACE, 0); }
		public TerminalNode Identifier_() { return getToken(MermaidParser.Identifier_, 0); }
		public MermaidClassMembers_Context mermaidClassMembers_() {
			return getRuleContext(MermaidClassMembers_Context.class,0);
		}
		public MermaidClassNodeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_mermaidClassNode; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).enterMermaidClassNode(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).exitMermaidClassNode(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MermaidVisitor ) return ((MermaidVisitor<? extends T>)visitor).visitMermaidClassNode(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MermaidClassNodeContext mermaidClassNode() throws RecognitionException {
		MermaidClassNodeContext _localctx = new MermaidClassNodeContext(_ctx, getState());
		enterRule(_localctx, 28, RULE_mermaidClassNode);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(126);
			match(CLASS);
			setState(127);
			((MermaidClassNodeContext)_localctx).className = match(Identifier_);
			setState(128);
			match(LBRACE);
			setState(130);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Visibility || _la==Identifier_) {
				{
				setState(129);
				((MermaidClassNodeContext)_localctx).members = mermaidClassMembers_();
				}
			}

			setState(132);
			match(RBRACE);
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
	public static class MermaidClassMembers_Context extends ParserRuleContext {
		public MermaidClassMemberContext e;
		public List<MermaidClassMemberContext> mermaidClassMember() {
			return getRuleContexts(MermaidClassMemberContext.class);
		}
		public MermaidClassMemberContext mermaidClassMember(int i) {
			return getRuleContext(MermaidClassMemberContext.class,i);
		}
		public MermaidClassMembers_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_mermaidClassMembers_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).enterMermaidClassMembers_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).exitMermaidClassMembers_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MermaidVisitor ) return ((MermaidVisitor<? extends T>)visitor).visitMermaidClassMembers_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MermaidClassMembers_Context mermaidClassMembers_() throws RecognitionException {
		MermaidClassMembers_Context _localctx = new MermaidClassMembers_Context(_ctx, getState());
		enterRule(_localctx, 30, RULE_mermaidClassMembers_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(135); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(134);
				((MermaidClassMembers_Context)_localctx).e = mermaidClassMember();
				}
				}
				setState(137); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( _la==Visibility || _la==Identifier_ );
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
	public static class MermaidClassMemberContext extends ParserRuleContext {
		public Token visibility;
		public Token name;
		public Token type;
		public Token isStatic;
		public TerminalNode Identifier_() { return getToken(MermaidParser.Identifier_, 0); }
		public TerminalNode COLON() { return getToken(MermaidParser.COLON, 0); }
		public TerminalNode Visibility() { return getToken(MermaidParser.Visibility, 0); }
		public TerminalNode Identifier() { return getToken(MermaidParser.Identifier, 0); }
		public TerminalNode STATIC() { return getToken(MermaidParser.STATIC, 0); }
		public MermaidClassMemberContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_mermaidClassMember; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).enterMermaidClassMember(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).exitMermaidClassMember(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MermaidVisitor ) return ((MermaidVisitor<? extends T>)visitor).visitMermaidClassMember(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MermaidClassMemberContext mermaidClassMember() throws RecognitionException {
		MermaidClassMemberContext _localctx = new MermaidClassMemberContext(_ctx, getState());
		enterRule(_localctx, 32, RULE_mermaidClassMember);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(140);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Visibility) {
				{
				setState(139);
				((MermaidClassMemberContext)_localctx).visibility = match(Visibility);
				}
			}

			setState(142);
			((MermaidClassMemberContext)_localctx).name = match(Identifier_);
			setState(145);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COLON) {
				{
				setState(143);
				match(COLON);
				setState(144);
				((MermaidClassMemberContext)_localctx).type = match(Identifier);
				}
			}

			setState(148);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==STATIC) {
				{
				setState(147);
				((MermaidClassMemberContext)_localctx).isStatic = match(STATIC);
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
	public static class MermaidStateNodeContext extends ParserRuleContext {
		public Token id;
		public Token description;
		public TerminalNode STATE() { return getToken(MermaidParser.STATE, 0); }
		public TerminalNode Identifier_() { return getToken(MermaidParser.Identifier_, 0); }
		public TerminalNode COLON() { return getToken(MermaidParser.COLON, 0); }
		public TerminalNode StringLiteral_() { return getToken(MermaidParser.StringLiteral_, 0); }
		public MermaidStateNodeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_mermaidStateNode; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).enterMermaidStateNode(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).exitMermaidStateNode(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MermaidVisitor ) return ((MermaidVisitor<? extends T>)visitor).visitMermaidStateNode(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MermaidStateNodeContext mermaidStateNode() throws RecognitionException {
		MermaidStateNodeContext _localctx = new MermaidStateNodeContext(_ctx, getState());
		enterRule(_localctx, 34, RULE_mermaidStateNode);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(150);
			match(STATE);
			setState(151);
			((MermaidStateNodeContext)_localctx).id = match(Identifier_);
			setState(154);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COLON) {
				{
				setState(152);
				match(COLON);
				setState(153);
				((MermaidStateNodeContext)_localctx).description = match(StringLiteral_);
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
	public static class MermaidGanttTaskContext extends ParserRuleContext {
		public Token id;
		public Token title;
		public Token start;
		public Token duration;
		public TerminalNode TASK() { return getToken(MermaidParser.TASK, 0); }
		public TerminalNode COLON() { return getToken(MermaidParser.COLON, 0); }
		public TerminalNode Identifier_() { return getToken(MermaidParser.Identifier_, 0); }
		public List<TerminalNode> StringLiteral_() { return getTokens(MermaidParser.StringLiteral_); }
		public TerminalNode StringLiteral_(int i) {
			return getToken(MermaidParser.StringLiteral_, i);
		}
		public List<TerminalNode> COMMA() { return getTokens(MermaidParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(MermaidParser.COMMA, i);
		}
		public MermaidGanttTaskContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_mermaidGanttTask; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).enterMermaidGanttTask(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).exitMermaidGanttTask(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MermaidVisitor ) return ((MermaidVisitor<? extends T>)visitor).visitMermaidGanttTask(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MermaidGanttTaskContext mermaidGanttTask() throws RecognitionException {
		MermaidGanttTaskContext _localctx = new MermaidGanttTaskContext(_ctx, getState());
		enterRule(_localctx, 36, RULE_mermaidGanttTask);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(156);
			match(TASK);
			setState(157);
			((MermaidGanttTaskContext)_localctx).id = match(Identifier_);
			setState(158);
			match(COLON);
			setState(159);
			((MermaidGanttTaskContext)_localctx).title = match(StringLiteral_);
			setState(162);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,17,_ctx) ) {
			case 1:
				{
				setState(160);
				match(COMMA);
				setState(161);
				((MermaidGanttTaskContext)_localctx).start = match(StringLiteral_);
				}
				break;
			}
			setState(166);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COMMA) {
				{
				setState(164);
				match(COMMA);
				setState(165);
				((MermaidGanttTaskContext)_localctx).duration = match(StringLiteral_);
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
	public static class MermaidPieItemContext extends ParserRuleContext {
		public Token label;
		public Token value;
		public TerminalNode PIE() { return getToken(MermaidParser.PIE, 0); }
		public TerminalNode COLON() { return getToken(MermaidParser.COLON, 0); }
		public TerminalNode StringLiteral_() { return getToken(MermaidParser.StringLiteral_, 0); }
		public TerminalNode NumberLiteral_() { return getToken(MermaidParser.NumberLiteral_, 0); }
		public MermaidPieItemContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_mermaidPieItem; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).enterMermaidPieItem(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).exitMermaidPieItem(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MermaidVisitor ) return ((MermaidVisitor<? extends T>)visitor).visitMermaidPieItem(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MermaidPieItemContext mermaidPieItem() throws RecognitionException {
		MermaidPieItemContext _localctx = new MermaidPieItemContext(_ctx, getState());
		enterRule(_localctx, 38, RULE_mermaidPieItem);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(168);
			match(PIE);
			setState(169);
			((MermaidPieItemContext)_localctx).label = match(StringLiteral_);
			setState(170);
			match(COLON);
			setState(171);
			((MermaidPieItemContext)_localctx).value = match(NumberLiteral_);
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
	public static class MermaidStyleStatementContext extends ParserRuleContext {
		public Token target;
		public MermaidStyleAttributes_Context attributes;
		public TerminalNode STYLE() { return getToken(MermaidParser.STYLE, 0); }
		public TerminalNode LBRACE() { return getToken(MermaidParser.LBRACE, 0); }
		public TerminalNode RBRACE() { return getToken(MermaidParser.RBRACE, 0); }
		public TerminalNode StringLiteral_() { return getToken(MermaidParser.StringLiteral_, 0); }
		public MermaidStyleAttributes_Context mermaidStyleAttributes_() {
			return getRuleContext(MermaidStyleAttributes_Context.class,0);
		}
		public MermaidStyleStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_mermaidStyleStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).enterMermaidStyleStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).exitMermaidStyleStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MermaidVisitor ) return ((MermaidVisitor<? extends T>)visitor).visitMermaidStyleStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MermaidStyleStatementContext mermaidStyleStatement() throws RecognitionException {
		MermaidStyleStatementContext _localctx = new MermaidStyleStatementContext(_ctx, getState());
		enterRule(_localctx, 40, RULE_mermaidStyleStatement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(173);
			match(STYLE);
			setState(174);
			((MermaidStyleStatementContext)_localctx).target = match(StringLiteral_);
			setState(175);
			match(LBRACE);
			setState(177);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Identifier_) {
				{
				setState(176);
				((MermaidStyleStatementContext)_localctx).attributes = mermaidStyleAttributes_();
				}
			}

			setState(179);
			match(RBRACE);
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
	public static class MermaidStyleAttributes_Context extends ParserRuleContext {
		public MermaidStyleAttributeContext e;
		public List<MermaidStyleAttributeContext> mermaidStyleAttribute() {
			return getRuleContexts(MermaidStyleAttributeContext.class);
		}
		public MermaidStyleAttributeContext mermaidStyleAttribute(int i) {
			return getRuleContext(MermaidStyleAttributeContext.class,i);
		}
		public MermaidStyleAttributes_Context(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_mermaidStyleAttributes_; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).enterMermaidStyleAttributes_(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).exitMermaidStyleAttributes_(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MermaidVisitor ) return ((MermaidVisitor<? extends T>)visitor).visitMermaidStyleAttributes_(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MermaidStyleAttributes_Context mermaidStyleAttributes_() throws RecognitionException {
		MermaidStyleAttributes_Context _localctx = new MermaidStyleAttributes_Context(_ctx, getState());
		enterRule(_localctx, 42, RULE_mermaidStyleAttributes_);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(182); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(181);
				((MermaidStyleAttributes_Context)_localctx).e = mermaidStyleAttribute();
				}
				}
				setState(184); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( _la==Identifier_ );
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
	public static class MermaidStyleAttributeContext extends ParserRuleContext {
		public Token name;
		public Token value;
		public TerminalNode COLON() { return getToken(MermaidParser.COLON, 0); }
		public TerminalNode Identifier_() { return getToken(MermaidParser.Identifier_, 0); }
		public TerminalNode StringLiteral_() { return getToken(MermaidParser.StringLiteral_, 0); }
		public MermaidStyleAttributeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_mermaidStyleAttribute; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).enterMermaidStyleAttribute(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof MermaidListener ) ((MermaidListener)listener).exitMermaidStyleAttribute(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof MermaidVisitor ) return ((MermaidVisitor<? extends T>)visitor).visitMermaidStyleAttribute(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MermaidStyleAttributeContext mermaidStyleAttribute() throws RecognitionException {
		MermaidStyleAttributeContext _localctx = new MermaidStyleAttributeContext(_ctx, getState());
		enterRule(_localctx, 44, RULE_mermaidStyleAttribute);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(186);
			((MermaidStyleAttributeContext)_localctx).name = match(Identifier_);
			setState(187);
			match(COLON);
			setState(189);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==StringLiteral_) {
				{
				setState(188);
				((MermaidStyleAttributeContext)_localctx).value = match(StringLiteral_);
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

	public static final String _serializedATN =
		"\u0004\u00015\u00c0\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
		"\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002"+
		"\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007\u0002"+
		"\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002\u000b\u0007\u000b\u0002"+
		"\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e\u0002\u000f\u0007\u000f"+
		"\u0002\u0010\u0007\u0010\u0002\u0011\u0007\u0011\u0002\u0012\u0007\u0012"+
		"\u0002\u0013\u0007\u0013\u0002\u0014\u0007\u0014\u0002\u0015\u0007\u0015"+
		"\u0002\u0016\u0007\u0016\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0001"+
		"\u0001\u0001\u0001\u0002\u0004\u00025\b\u0002\u000b\u0002\f\u00026\u0001"+
		"\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001"+
		"\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0003"+
		"\u0003E\b\u0003\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0005\u0001"+
		"\u0005\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0007\u0001\u0007\u0001"+
		"\u0007\u0003\u0007R\b\u0007\u0001\u0007\u0003\u0007U\b\u0007\u0001\u0007"+
		"\u0003\u0007X\b\u0007\u0001\b\u0001\b\u0001\t\u0001\t\u0003\t^\b\t\u0001"+
		"\t\u0003\ta\b\t\u0001\t\u0001\t\u0001\n\u0001\n\u0001\u000b\u0001\u000b"+
		"\u0001\u000b\u0003\u000bj\b\u000b\u0001\u000b\u0001\u000b\u0001\u000b"+
		"\u0001\u000b\u0001\f\u0001\f\u0001\f\u0001\f\u0003\ft\b\f\u0001\r\u0001"+
		"\r\u0003\rx\b\r\u0001\r\u0003\r{\b\r\u0001\r\u0001\r\u0001\u000e\u0001"+
		"\u000e\u0001\u000e\u0001\u000e\u0003\u000e\u0083\b\u000e\u0001\u000e\u0001"+
		"\u000e\u0001\u000f\u0004\u000f\u0088\b\u000f\u000b\u000f\f\u000f\u0089"+
		"\u0001\u0010\u0003\u0010\u008d\b\u0010\u0001\u0010\u0001\u0010\u0001\u0010"+
		"\u0003\u0010\u0092\b\u0010\u0001\u0010\u0003\u0010\u0095\b\u0010\u0001"+
		"\u0011\u0001\u0011\u0001\u0011\u0001\u0011\u0003\u0011\u009b\b\u0011\u0001"+
		"\u0012\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012\u0003"+
		"\u0012\u00a3\b\u0012\u0001\u0012\u0001\u0012\u0003\u0012\u00a7\b\u0012"+
		"\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0014"+
		"\u0001\u0014\u0001\u0014\u0001\u0014\u0003\u0014\u00b2\b\u0014\u0001\u0014"+
		"\u0001\u0014\u0001\u0015\u0004\u0015\u00b7\b\u0015\u000b\u0015\f\u0015"+
		"\u00b8\u0001\u0016\u0001\u0016\u0001\u0016\u0003\u0016\u00be\b\u0016\u0001"+
		"\u0016\u0000\u0000\u0017\u0000\u0002\u0004\u0006\b\n\f\u000e\u0010\u0012"+
		"\u0014\u0016\u0018\u001a\u001c\u001e \"$&(*,\u0000\u0004\u0001\u0000\u0001"+
		"\t\u0001\u0000\n\r\u0001\u0000\u000e\u0018\u0001\u0000\u0019\u001c\u00c8"+
		"\u0000.\u0001\u0000\u0000\u0000\u00021\u0001\u0000\u0000\u0000\u00044"+
		"\u0001\u0000\u0000\u0000\u0006D\u0001\u0000\u0000\u0000\bF\u0001\u0000"+
		"\u0000\u0000\nI\u0001\u0000\u0000\u0000\fK\u0001\u0000\u0000\u0000\u000e"+
		"N\u0001\u0000\u0000\u0000\u0010Y\u0001\u0000\u0000\u0000\u0012[\u0001"+
		"\u0000\u0000\u0000\u0014d\u0001\u0000\u0000\u0000\u0016f\u0001\u0000\u0000"+
		"\u0000\u0018o\u0001\u0000\u0000\u0000\u001au\u0001\u0000\u0000\u0000\u001c"+
		"~\u0001\u0000\u0000\u0000\u001e\u0087\u0001\u0000\u0000\u0000 \u008c\u0001"+
		"\u0000\u0000\u0000\"\u0096\u0001\u0000\u0000\u0000$\u009c\u0001\u0000"+
		"\u0000\u0000&\u00a8\u0001\u0000\u0000\u0000(\u00ad\u0001\u0000\u0000\u0000"+
		"*\u00b6\u0001\u0000\u0000\u0000,\u00ba\u0001\u0000\u0000\u0000./\u0003"+
		"\u0002\u0001\u0000/0\u0003\u0004\u0002\u00000\u0001\u0001\u0000\u0000"+
		"\u000012\u0007\u0000\u0000\u00002\u0003\u0001\u0000\u0000\u000035\u0003"+
		"\u0006\u0003\u000043\u0001\u0000\u0000\u000056\u0001\u0000\u0000\u0000"+
		"64\u0001\u0000\u0000\u000067\u0001\u0000\u0000\u00007\u0005\u0001\u0000"+
		"\u0000\u00008E\u0003\b\u0004\u00009E\u0003\f\u0006\u0000:E\u0003\u000e"+
		"\u0007\u0000;E\u0003\u0012\t\u0000<E\u0003\u0016\u000b\u0000=E\u0003\u0018"+
		"\f\u0000>E\u0003\u001a\r\u0000?E\u0003\u001c\u000e\u0000@E\u0003\"\u0011"+
		"\u0000AE\u0003$\u0012\u0000BE\u0003&\u0013\u0000CE\u0003(\u0014\u0000"+
		"D8\u0001\u0000\u0000\u0000D9\u0001\u0000\u0000\u0000D:\u0001\u0000\u0000"+
		"\u0000D;\u0001\u0000\u0000\u0000D<\u0001\u0000\u0000\u0000D=\u0001\u0000"+
		"\u0000\u0000D>\u0001\u0000\u0000\u0000D?\u0001\u0000\u0000\u0000D@\u0001"+
		"\u0000\u0000\u0000DA\u0001\u0000\u0000\u0000DB\u0001\u0000\u0000\u0000"+
		"DC\u0001\u0000\u0000\u0000E\u0007\u0001\u0000\u0000\u0000FG\u00054\u0000"+
		"\u0000GH\u0003\n\u0005\u0000H\t\u0001\u0000\u0000\u0000IJ\u0007\u0001"+
		"\u0000\u0000J\u000b\u0001\u0000\u0000\u0000KL\u00052\u0000\u0000LM\u0005"+
		"/\u0000\u0000M\r\u0001\u0000\u0000\u0000NT\u00051\u0000\u0000OQ\u0005"+
		"$\u0000\u0000PR\u0005/\u0000\u0000QP\u0001\u0000\u0000\u0000QR\u0001\u0000"+
		"\u0000\u0000RS\u0001\u0000\u0000\u0000SU\u0005%\u0000\u0000TO\u0001\u0000"+
		"\u0000\u0000TU\u0001\u0000\u0000\u0000UW\u0001\u0000\u0000\u0000VX\u0003"+
		"\u0010\b\u0000WV\u0001\u0000\u0000\u0000WX\u0001\u0000\u0000\u0000X\u000f"+
		"\u0001\u0000\u0000\u0000YZ\u0007\u0002\u0000\u0000Z\u0011\u0001\u0000"+
		"\u0000\u0000[]\u00051\u0000\u0000\\^\u0003\u0014\n\u0000]\\\u0001\u0000"+
		"\u0000\u0000]^\u0001\u0000\u0000\u0000^`\u0001\u0000\u0000\u0000_a\u0005"+
		"/\u0000\u0000`_\u0001\u0000\u0000\u0000`a\u0001\u0000\u0000\u0000ab\u0001"+
		"\u0000\u0000\u0000bc\u00055\u0000\u0000c\u0013\u0001\u0000\u0000\u0000"+
		"de\u0007\u0003\u0000\u0000e\u0015\u0001\u0000\u0000\u0000fg\u0005 \u0000"+
		"\u0000gi\u00051\u0000\u0000hj\u0005/\u0000\u0000ih\u0001\u0000\u0000\u0000"+
		"ij\u0001\u0000\u0000\u0000jk\u0001\u0000\u0000\u0000kl\u0005(\u0000\u0000"+
		"lm\u0003\u0004\u0002\u0000mn\u0005)\u0000\u0000n\u0017\u0001\u0000\u0000"+
		"\u0000op\u0005\u001d\u0000\u0000ps\u00051\u0000\u0000qr\u0005\u001e\u0000"+
		"\u0000rt\u0005/\u0000\u0000sq\u0001\u0000\u0000\u0000st\u0001\u0000\u0000"+
		"\u0000t\u0019\u0001\u0000\u0000\u0000uw\u00051\u0000\u0000vx\u0003\u0014"+
		"\n\u0000wv\u0001\u0000\u0000\u0000wx\u0001\u0000\u0000\u0000xz\u0001\u0000"+
		"\u0000\u0000y{\u0005/\u0000\u0000zy\u0001\u0000\u0000\u0000z{\u0001\u0000"+
		"\u0000\u0000{|\u0001\u0000\u0000\u0000|}\u00055\u0000\u0000}\u001b\u0001"+
		"\u0000\u0000\u0000~\u007f\u0005\u0003\u0000\u0000\u007f\u0080\u00051\u0000"+
		"\u0000\u0080\u0082\u0005(\u0000\u0000\u0081\u0083\u0003\u001e\u000f\u0000"+
		"\u0082\u0081\u0001\u0000\u0000\u0000\u0082\u0083\u0001\u0000\u0000\u0000"+
		"\u0083\u0084\u0001\u0000\u0000\u0000\u0084\u0085\u0005)\u0000\u0000\u0085"+
		"\u001d\u0001\u0000\u0000\u0000\u0086\u0088\u0003 \u0010\u0000\u0087\u0086"+
		"\u0001\u0000\u0000\u0000\u0088\u0089\u0001\u0000\u0000\u0000\u0089\u0087"+
		"\u0001\u0000\u0000\u0000\u0089\u008a\u0001\u0000\u0000\u0000\u008a\u001f"+
		"\u0001\u0000\u0000\u0000\u008b\u008d\u0005#\u0000\u0000\u008c\u008b\u0001"+
		"\u0000\u0000\u0000\u008c\u008d\u0001\u0000\u0000\u0000\u008d\u008e\u0001"+
		"\u0000\u0000\u0000\u008e\u0091\u00051\u0000\u0000\u008f\u0090\u0005*\u0000"+
		"\u0000\u0090\u0092\u00055\u0000\u0000\u0091\u008f\u0001\u0000\u0000\u0000"+
		"\u0091\u0092\u0001\u0000\u0000\u0000\u0092\u0094\u0001\u0000\u0000\u0000"+
		"\u0093\u0095\u0005\"\u0000\u0000\u0094\u0093\u0001\u0000\u0000\u0000\u0094"+
		"\u0095\u0001\u0000\u0000\u0000\u0095!\u0001\u0000\u0000\u0000\u0096\u0097"+
		"\u0005\u0004\u0000\u0000\u0097\u009a\u00051\u0000\u0000\u0098\u0099\u0005"+
		"*\u0000\u0000\u0099\u009b\u0005/\u0000\u0000\u009a\u0098\u0001\u0000\u0000"+
		"\u0000\u009a\u009b\u0001\u0000\u0000\u0000\u009b#\u0001\u0000\u0000\u0000"+
		"\u009c\u009d\u0005\u001f\u0000\u0000\u009d\u009e\u00051\u0000\u0000\u009e"+
		"\u009f\u0005*\u0000\u0000\u009f\u00a2\u0005/\u0000\u0000\u00a0\u00a1\u0005"+
		",\u0000\u0000\u00a1\u00a3\u0005/\u0000\u0000\u00a2\u00a0\u0001\u0000\u0000"+
		"\u0000\u00a2\u00a3\u0001\u0000\u0000\u0000\u00a3\u00a6\u0001\u0000\u0000"+
		"\u0000\u00a4\u00a5\u0005,\u0000\u0000\u00a5\u00a7\u0005/\u0000\u0000\u00a6"+
		"\u00a4\u0001\u0000\u0000\u0000\u00a6\u00a7\u0001\u0000\u0000\u0000\u00a7"+
		"%\u0001\u0000\u0000\u0000\u00a8\u00a9\u0005\u0006\u0000\u0000\u00a9\u00aa"+
		"\u0005/\u0000\u0000\u00aa\u00ab\u0005*\u0000\u0000\u00ab\u00ac\u00050"+
		"\u0000\u0000\u00ac\'\u0001\u0000\u0000\u0000\u00ad\u00ae\u0005!\u0000"+
		"\u0000\u00ae\u00af\u0005/\u0000\u0000\u00af\u00b1\u0005(\u0000\u0000\u00b0"+
		"\u00b2\u0003*\u0015\u0000\u00b1\u00b0\u0001\u0000\u0000\u0000\u00b1\u00b2"+
		"\u0001\u0000\u0000\u0000\u00b2\u00b3\u0001\u0000\u0000\u0000\u00b3\u00b4"+
		"\u0005)\u0000\u0000\u00b4)\u0001\u0000\u0000\u0000\u00b5\u00b7\u0003,"+
		"\u0016\u0000\u00b6\u00b5\u0001\u0000\u0000\u0000\u00b7\u00b8\u0001\u0000"+
		"\u0000\u0000\u00b8\u00b6\u0001\u0000\u0000\u0000\u00b8\u00b9\u0001\u0000"+
		"\u0000\u0000\u00b9+\u0001\u0000\u0000\u0000\u00ba\u00bb\u00051\u0000\u0000"+
		"\u00bb\u00bd\u0005*\u0000\u0000\u00bc\u00be\u0005/\u0000\u0000\u00bd\u00bc"+
		"\u0001\u0000\u0000\u0000\u00bd\u00be\u0001\u0000\u0000\u0000\u00be-\u0001"+
		"\u0000\u0000\u0000\u00166DQTW]`iswz\u0082\u0089\u008c\u0091\u0094\u009a"+
		"\u00a2\u00a6\u00b1\u00b8\u00bd";
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