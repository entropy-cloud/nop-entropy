/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
// Generated from /home/u/sources/platform/entropy-cloud/nop-antlr4/nop-antlr4-xpath/src/main/antlr4/imports/XPathCommon.g4 by ANTLR 4.9

import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;

import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast"})
public class XPathCommon extends Parser {
    static {
        RuntimeMetaData.checkVersion("4.9", RuntimeMetaData.VERSION);
    }

    protected static final DFA[] _decisionToDFA;
    protected static final PredictionContextCache _sharedContextCache =
            new PredictionContextCache();
    public static final int
            OpenBracket = 1, CloseBracket = 2, OpenParen = 3, CloseParen = 4, OpenBrace = 5,
            CloseBrace = 6, SemiColon = 7, Comma = 8, Assign = 9, NullCoalesce = 10, Question = 11,
            OptionalDot = 12, Colon = 13, Ellipsis = 14, Dot = 15, PlusPlus = 16, MinusMinus = 17,
            Plus = 18, Minus = 19, BitNot = 20, Not = 21, Multiply = 22, Divide = 23, Modulus = 24,
            RightShiftArithmetic = 25, LeftShiftArithmetic = 26, RightShiftLogical = 27,
            LessThan = 28, MoreThan = 29, LessThanEquals = 30, GreaterThanEquals = 31, Equals_ = 32,
            NotEquals = 33, IdentityEquals = 34, IdentityNotEquals = 35, BitAnd = 36, BitXOr = 37,
            BitOr = 38, And = 39, Or = 40, MultiplyAssign = 41, DivideAssign = 42, ModulusAssign = 43,
            PlusAssign = 44, MinusAssign = 45, LeftShiftArithmeticAssign = 46, RightShiftArithmeticAssign = 47,
            RightShiftLogicalAssign = 48, BitAndAssign = 49, BitXorAssign = 50, BitOrAssign = 51,
            Arrow = 52, NullLiteral = 53, BooleanLiteral = 54, AndLiteral = 55, OrLiteral = 56,
            DecimalIntegerLiteral = 57, HexIntegerLiteral = 58, BinaryIntegerLiteral = 59,
            DecimalLiteral = 60, Break = 61, Do = 62, Instanceof = 63, Typeof = 64, Case = 65,
            Else = 66, New = 67, Var = 68, Catch = 69, Finally = 70, Return = 71, Void = 72, Continue = 73,
            For = 74, Switch = 75, While = 76, Debugger = 77, Function = 78, This = 79, With = 80,
            Default = 81, If = 82, Throw = 83, Delete = 84, In = 85, Try = 86, As = 87, From = 88,
            ReadOnly = 89, Async = 90, Await = 91, Class = 92, Enum = 93, Extends = 94, Super = 95,
            Const = 96, Export = 97, Import = 98, Implements = 99, Let = 100, Private = 101, Public = 102,
            Interface = 103, Package = 104, Protected = 105, Static = 106, Any = 107, Number = 108,
            Boolean = 109, String = 110, Symbol = 111, TypeAlias = 112, Constructor = 113, Abstract = 114,
            At = 115, Identifier = 116, StringLiteral = 117, TemplateStringLiteral = 118,
            WhiteSpaces = 119, LineTerminator = 120, UnexpectedCharacter = 121, XName = 122,
            RegularExpressionLiteral = 123;
    public static final int
            RULE_qualifiedType = 0, RULE_qualifiedName = 1, RULE_propertyName = 2,
            RULE_identifier = 3, RULE_identifierOrKeyword = 4, RULE_reservedWord = 5,
            RULE_keyword = 6, RULE_literal = 7, RULE_numericLiteral = 8;

    private static String[] makeRuleNames() {
        return new String[]{
                "qualifiedType", "qualifiedName", "propertyName", "identifier", "identifierOrKeyword",
                "reservedWord", "keyword", "literal", "numericLiteral"
        };
    }

    public static final String[] ruleNames = makeRuleNames();

    private static String[] makeLiteralNames() {
        return new String[]{
                null, "'['", "']'", "'('", "')'", "'{'", "'}'", "';'", "','", "'='",
                "'??'", "'?'", "'?.'", "':'", "'...'", "'.'", "'++'", "'--'", "'+'",
                "'-'", "'~'", "'!'", "'*'", "'/'", "'%'", "'>>'", "'<<'", "'>>>'", "'<'",
                "'>'", "'<='", "'>='", "'=='", "'!='", "'==='", "'!=='", "'&'", "'^'",
                "'|'", "'&&'", "'||'", "'*='", "'/='", "'%='", "'+='", "'-='", "'<<='",
                "'>>='", "'>>>='", "'&='", "'^='", "'|='", "'=>'", "'null'", null, "'and'",
                "'or'", null, null, null, null, "'break'", "'do'", "'instanceof'", "'typeof'",
                "'case'", "'else'", "'new'", "'var'", "'catch'", "'finally'", "'return'",
                "'void'", "'continue'", "'for'", "'switch'", "'while'", "'debugger'",
                "'function'", "'this'", "'with'", "'default'", "'if'", "'throw'", "'delete'",
                "'in'", "'try'", "'as'", "'from'", "'readonly'", "'async'", "'await'",
                "'class'", "'enum'", "'extends'", "'super'", "'const'", "'export'", "'import'",
                "'implements'", "'let'", "'private'", "'public'", "'interface'", "'package'",
                "'protected'", "'static'", "'any'", "'number'", "'boolean'", "'string'",
                "'symbol'", "'type'", "'constructor'", "'abstract'", "'@'"
        };
    }

    private static final String[] _LITERAL_NAMES = makeLiteralNames();

    private static String[] makeSymbolicNames() {
        return new String[]{
                null, "OpenBracket", "CloseBracket", "OpenParen", "CloseParen", "OpenBrace",
                "CloseBrace", "SemiColon", "Comma", "Assign", "NullCoalesce", "Question",
                "OptionalDot", "Colon", "Ellipsis", "Dot", "PlusPlus", "MinusMinus",
                "Plus", "Minus", "BitNot", "Not", "Multiply", "Divide", "Modulus", "RightShiftArithmetic",
                "LeftShiftArithmetic", "RightShiftLogical", "LessThan", "MoreThan", "LessThanEquals",
                "GreaterThanEquals", "Equals_", "NotEquals", "IdentityEquals", "IdentityNotEquals",
                "BitAnd", "BitXOr", "BitOr", "And", "Or", "MultiplyAssign", "DivideAssign",
                "ModulusAssign", "PlusAssign", "MinusAssign", "LeftShiftArithmeticAssign",
                "RightShiftArithmeticAssign", "RightShiftLogicalAssign", "BitAndAssign",
                "BitXorAssign", "BitOrAssign", "Arrow", "NullLiteral", "BooleanLiteral",
                "AndLiteral", "OrLiteral", "DecimalIntegerLiteral", "HexIntegerLiteral",
                "BinaryIntegerLiteral", "DecimalLiteral", "Break", "Do", "Instanceof",
                "Typeof", "Case", "Else", "New", "Var", "Catch", "Finally", "Return",
                "Void", "Continue", "For", "Switch", "While", "Debugger", "Function",
                "This", "With", "Default", "If", "Throw", "Delete", "In", "Try", "As",
                "From", "ReadOnly", "Async", "Await", "Class", "Enum", "Extends", "Super",
                "Const", "Export", "Import", "Implements", "Let", "Private", "Public",
                "Interface", "Package", "Protected", "Static", "Any", "Number", "Boolean",
                "String", "Symbol", "TypeAlias", "Constructor", "Abstract", "At", "Identifier",
                "StringLiteral", "TemplateStringLiteral", "WhiteSpaces", "LineTerminator",
                "UnexpectedCharacter", "XName", "RegularExpressionLiteral"
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
    public String getGrammarFileName() {
        return "XPathCommon.g4";
    }

    @Override
    public String[] getRuleNames() {
        return ruleNames;
    }

    @Override
    public String getSerializedATN() {
        return _serializedATN;
    }

    @Override
    public ATN getATN() {
        return _ATN;
    }

    public XPathCommon(TokenStream input) {
        super(input);
        _interp = new ParserATNSimulator(this, _ATN, _decisionToDFA, _sharedContextCache);
    }

    public static class QualifiedTypeContext extends ParserRuleContext {
        public TerminalNode Identifier() {
            return getToken(XPathCommon.Identifier, 0);
        }

        public List<TerminalNode> Dot() {
            return getTokens(XPathCommon.Dot);
        }

        public TerminalNode Dot(int i) {
            return getToken(XPathCommon.Dot, i);
        }

        public List<IdentifierContext> identifier() {
            return getRuleContexts(IdentifierContext.class);
        }

        public IdentifierContext identifier(int i) {
            return getRuleContext(IdentifierContext.class, i);
        }

        public QualifiedTypeContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_qualifiedType;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathCommonListener) ((XPathCommonListener) listener).enterQualifiedType(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathCommonListener) ((XPathCommonListener) listener).exitQualifiedType(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathCommonVisitor)
                return ((XPathCommonVisitor<? extends T>) visitor).visitQualifiedType(this);
            else return visitor.visitChildren(this);
        }
    }

    public final QualifiedTypeContext qualifiedType() throws RecognitionException {
        QualifiedTypeContext _localctx = new QualifiedTypeContext(_ctx, getState());
        enterRule(_localctx, 0, RULE_qualifiedType);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(18);
                match(Identifier);
                setState(23);
                _errHandler.sync(this);
                _la = _input.LA(1);
                while (_la == Dot) {
                    {
                        {
                            setState(19);
                            match(Dot);
                            setState(20);
                            identifier();
                        }
                    }
                    setState(25);
                    _errHandler.sync(this);
                    _la = _input.LA(1);
                }
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class QualifiedNameContext extends ParserRuleContext {
        public TerminalNode Identifier() {
            return getToken(XPathCommon.Identifier, 0);
        }

        public List<TerminalNode> Dot() {
            return getTokens(XPathCommon.Dot);
        }

        public TerminalNode Dot(int i) {
            return getToken(XPathCommon.Dot, i);
        }

        public List<IdentifierContext> identifier() {
            return getRuleContexts(IdentifierContext.class);
        }

        public IdentifierContext identifier(int i) {
            return getRuleContext(IdentifierContext.class, i);
        }

        public QualifiedNameContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_qualifiedName;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathCommonListener) ((XPathCommonListener) listener).enterQualifiedName(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathCommonListener) ((XPathCommonListener) listener).exitQualifiedName(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathCommonVisitor)
                return ((XPathCommonVisitor<? extends T>) visitor).visitQualifiedName(this);
            else return visitor.visitChildren(this);
        }
    }

    public final QualifiedNameContext qualifiedName() throws RecognitionException {
        QualifiedNameContext _localctx = new QualifiedNameContext(_ctx, getState());
        enterRule(_localctx, 2, RULE_qualifiedName);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(26);
                match(Identifier);
                setState(31);
                _errHandler.sync(this);
                _la = _input.LA(1);
                while (_la == Dot) {
                    {
                        {
                            setState(27);
                            match(Dot);
                            setState(28);
                            identifier();
                        }
                    }
                    setState(33);
                    _errHandler.sync(this);
                    _la = _input.LA(1);
                }
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class PropertyNameContext extends ParserRuleContext {
        public IdentifierOrKeywordContext identifierOrKeyword() {
            return getRuleContext(IdentifierOrKeywordContext.class, 0);
        }

        public TerminalNode StringLiteral() {
            return getToken(XPathCommon.StringLiteral, 0);
        }

        public PropertyNameContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_propertyName;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathCommonListener) ((XPathCommonListener) listener).enterPropertyName(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathCommonListener) ((XPathCommonListener) listener).exitPropertyName(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathCommonVisitor)
                return ((XPathCommonVisitor<? extends T>) visitor).visitPropertyName(this);
            else return visitor.visitChildren(this);
        }
    }

    public final PropertyNameContext propertyName() throws RecognitionException {
        PropertyNameContext _localctx = new PropertyNameContext(_ctx, getState());
        enterRule(_localctx, 4, RULE_propertyName);
        try {
            setState(36);
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
                    setState(34);
                    identifierOrKeyword();
                }
                break;
                case StringLiteral:
                    enterOuterAlt(_localctx, 2);
                {
                    setState(35);
                    match(StringLiteral);
                }
                break;
                default:
                    throw new NoViableAltException(this);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class IdentifierContext extends ParserRuleContext {
        public TerminalNode From() {
            return getToken(XPathCommon.From, 0);
        }

        public TerminalNode TypeAlias() {
            return getToken(XPathCommon.TypeAlias, 0);
        }

        public TerminalNode Identifier() {
            return getToken(XPathCommon.Identifier, 0);
        }

        public IdentifierContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_identifier;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathCommonListener) ((XPathCommonListener) listener).enterIdentifier(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathCommonListener) ((XPathCommonListener) listener).exitIdentifier(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathCommonVisitor)
                return ((XPathCommonVisitor<? extends T>) visitor).visitIdentifier(this);
            else return visitor.visitChildren(this);
        }
    }

    public final IdentifierContext identifier() throws RecognitionException {
        IdentifierContext _localctx = new IdentifierContext(_ctx, getState());
        enterRule(_localctx, 6, RULE_identifier);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(38);
                _la = _input.LA(1);
                if (!(((((_la - 88)) & ~0x3f) == 0 && ((1L << (_la - 88)) & ((1L << (From - 88)) | (1L << (TypeAlias - 88)) | (1L << (Identifier - 88)))) != 0))) {
                    _errHandler.recoverInline(this);
                } else {
                    if (_input.LA(1) == Token.EOF) matchedEOF = true;
                    _errHandler.reportMatch(this);
                    consume();
                }
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class IdentifierOrKeywordContext extends ParserRuleContext {
        public IdentifierContext identifier() {
            return getRuleContext(IdentifierContext.class, 0);
        }

        public ReservedWordContext reservedWord() {
            return getRuleContext(ReservedWordContext.class, 0);
        }

        public IdentifierOrKeywordContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_identifierOrKeyword;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathCommonListener)
                ((XPathCommonListener) listener).enterIdentifierOrKeyword(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathCommonListener) ((XPathCommonListener) listener).exitIdentifierOrKeyword(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathCommonVisitor)
                return ((XPathCommonVisitor<? extends T>) visitor).visitIdentifierOrKeyword(this);
            else return visitor.visitChildren(this);
        }
    }

    public final IdentifierOrKeywordContext identifierOrKeyword() throws RecognitionException {
        IdentifierOrKeywordContext _localctx = new IdentifierOrKeywordContext(_ctx, getState());
        enterRule(_localctx, 8, RULE_identifierOrKeyword);
        try {
            setState(42);
            _errHandler.sync(this);
            switch (getInterpreter().adaptivePredict(_input, 3, _ctx)) {
                case 1:
                    enterOuterAlt(_localctx, 1);
                {
                    setState(40);
                    identifier();
                }
                break;
                case 2:
                    enterOuterAlt(_localctx, 2);
                {
                    setState(41);
                    reservedWord();
                }
                break;
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class ReservedWordContext extends ParserRuleContext {
        public KeywordContext keyword() {
            return getRuleContext(KeywordContext.class, 0);
        }

        public TerminalNode NullLiteral() {
            return getToken(XPathCommon.NullLiteral, 0);
        }

        public TerminalNode BooleanLiteral() {
            return getToken(XPathCommon.BooleanLiteral, 0);
        }

        public TerminalNode AndLiteral() {
            return getToken(XPathCommon.AndLiteral, 0);
        }

        public TerminalNode OrLiteral() {
            return getToken(XPathCommon.OrLiteral, 0);
        }

        public ReservedWordContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_reservedWord;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathCommonListener) ((XPathCommonListener) listener).enterReservedWord(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathCommonListener) ((XPathCommonListener) listener).exitReservedWord(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathCommonVisitor)
                return ((XPathCommonVisitor<? extends T>) visitor).visitReservedWord(this);
            else return visitor.visitChildren(this);
        }
    }

    public final ReservedWordContext reservedWord() throws RecognitionException {
        ReservedWordContext _localctx = new ReservedWordContext(_ctx, getState());
        enterRule(_localctx, 10, RULE_reservedWord);
        try {
            setState(49);
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
                    setState(44);
                    keyword();
                }
                break;
                case NullLiteral:
                    enterOuterAlt(_localctx, 2);
                {
                    setState(45);
                    match(NullLiteral);
                }
                break;
                case BooleanLiteral:
                    enterOuterAlt(_localctx, 3);
                {
                    setState(46);
                    match(BooleanLiteral);
                }
                break;
                case AndLiteral:
                    enterOuterAlt(_localctx, 4);
                {
                    setState(47);
                    match(AndLiteral);
                }
                break;
                case OrLiteral:
                    enterOuterAlt(_localctx, 5);
                {
                    setState(48);
                    match(OrLiteral);
                }
                break;
                default:
                    throw new NoViableAltException(this);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class KeywordContext extends ParserRuleContext {
        public TerminalNode Break() {
            return getToken(XPathCommon.Break, 0);
        }

        public TerminalNode As() {
            return getToken(XPathCommon.As, 0);
        }

        public TerminalNode Do() {
            return getToken(XPathCommon.Do, 0);
        }

        public TerminalNode Instanceof() {
            return getToken(XPathCommon.Instanceof, 0);
        }

        public TerminalNode Typeof() {
            return getToken(XPathCommon.Typeof, 0);
        }

        public TerminalNode Case() {
            return getToken(XPathCommon.Case, 0);
        }

        public TerminalNode Else() {
            return getToken(XPathCommon.Else, 0);
        }

        public TerminalNode New() {
            return getToken(XPathCommon.New, 0);
        }

        public TerminalNode Var() {
            return getToken(XPathCommon.Var, 0);
        }

        public TerminalNode Catch() {
            return getToken(XPathCommon.Catch, 0);
        }

        public TerminalNode Finally() {
            return getToken(XPathCommon.Finally, 0);
        }

        public TerminalNode Return() {
            return getToken(XPathCommon.Return, 0);
        }

        public TerminalNode Void() {
            return getToken(XPathCommon.Void, 0);
        }

        public TerminalNode Continue() {
            return getToken(XPathCommon.Continue, 0);
        }

        public TerminalNode For() {
            return getToken(XPathCommon.For, 0);
        }

        public TerminalNode Switch() {
            return getToken(XPathCommon.Switch, 0);
        }

        public TerminalNode While() {
            return getToken(XPathCommon.While, 0);
        }

        public TerminalNode Debugger() {
            return getToken(XPathCommon.Debugger, 0);
        }

        public TerminalNode Function() {
            return getToken(XPathCommon.Function, 0);
        }

        public TerminalNode This() {
            return getToken(XPathCommon.This, 0);
        }

        public TerminalNode With() {
            return getToken(XPathCommon.With, 0);
        }

        public TerminalNode Default() {
            return getToken(XPathCommon.Default, 0);
        }

        public TerminalNode If() {
            return getToken(XPathCommon.If, 0);
        }

        public TerminalNode Throw() {
            return getToken(XPathCommon.Throw, 0);
        }

        public TerminalNode Delete() {
            return getToken(XPathCommon.Delete, 0);
        }

        public TerminalNode In() {
            return getToken(XPathCommon.In, 0);
        }

        public TerminalNode Try() {
            return getToken(XPathCommon.Try, 0);
        }

        public TerminalNode ReadOnly() {
            return getToken(XPathCommon.ReadOnly, 0);
        }

        public TerminalNode Class() {
            return getToken(XPathCommon.Class, 0);
        }

        public TerminalNode Enum() {
            return getToken(XPathCommon.Enum, 0);
        }

        public TerminalNode Extends() {
            return getToken(XPathCommon.Extends, 0);
        }

        public TerminalNode Super() {
            return getToken(XPathCommon.Super, 0);
        }

        public TerminalNode Const() {
            return getToken(XPathCommon.Const, 0);
        }

        public TerminalNode Export() {
            return getToken(XPathCommon.Export, 0);
        }

        public TerminalNode Import() {
            return getToken(XPathCommon.Import, 0);
        }

        public TerminalNode Implements() {
            return getToken(XPathCommon.Implements, 0);
        }

        public TerminalNode Let() {
            return getToken(XPathCommon.Let, 0);
        }

        public TerminalNode Private() {
            return getToken(XPathCommon.Private, 0);
        }

        public TerminalNode Public() {
            return getToken(XPathCommon.Public, 0);
        }

        public TerminalNode Interface() {
            return getToken(XPathCommon.Interface, 0);
        }

        public TerminalNode Package() {
            return getToken(XPathCommon.Package, 0);
        }

        public TerminalNode Protected() {
            return getToken(XPathCommon.Protected, 0);
        }

        public TerminalNode Static() {
            return getToken(XPathCommon.Static, 0);
        }

        public TerminalNode TypeAlias() {
            return getToken(XPathCommon.TypeAlias, 0);
        }

        public TerminalNode String() {
            return getToken(XPathCommon.String, 0);
        }

        public TerminalNode Boolean() {
            return getToken(XPathCommon.Boolean, 0);
        }

        public TerminalNode Number() {
            return getToken(XPathCommon.Number, 0);
        }

        public TerminalNode Any() {
            return getToken(XPathCommon.Any, 0);
        }

        public TerminalNode Symbol() {
            return getToken(XPathCommon.Symbol, 0);
        }

        public KeywordContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_keyword;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathCommonListener) ((XPathCommonListener) listener).enterKeyword(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathCommonListener) ((XPathCommonListener) listener).exitKeyword(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathCommonVisitor)
                return ((XPathCommonVisitor<? extends T>) visitor).visitKeyword(this);
            else return visitor.visitChildren(this);
        }
    }

    public final KeywordContext keyword() throws RecognitionException {
        KeywordContext _localctx = new KeywordContext(_ctx, getState());
        enterRule(_localctx, 12, RULE_keyword);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(51);
                _la = _input.LA(1);
                if (!(((((_la - 61)) & ~0x3f) == 0 && ((1L << (_la - 61)) & ((1L << (Break - 61)) | (1L << (Do - 61)) | (1L << (Instanceof - 61)) | (1L << (Typeof - 61)) | (1L << (Case - 61)) | (1L << (Else - 61)) | (1L << (New - 61)) | (1L << (Var - 61)) | (1L << (Catch - 61)) | (1L << (Finally - 61)) | (1L << (Return - 61)) | (1L << (Void - 61)) | (1L << (Continue - 61)) | (1L << (For - 61)) | (1L << (Switch - 61)) | (1L << (While - 61)) | (1L << (Debugger - 61)) | (1L << (Function - 61)) | (1L << (This - 61)) | (1L << (With - 61)) | (1L << (Default - 61)) | (1L << (If - 61)) | (1L << (Throw - 61)) | (1L << (Delete - 61)) | (1L << (In - 61)) | (1L << (Try - 61)) | (1L << (As - 61)) | (1L << (ReadOnly - 61)) | (1L << (Class - 61)) | (1L << (Enum - 61)) | (1L << (Extends - 61)) | (1L << (Super - 61)) | (1L << (Const - 61)) | (1L << (Export - 61)) | (1L << (Import - 61)) | (1L << (Implements - 61)) | (1L << (Let - 61)) | (1L << (Private - 61)) | (1L << (Public - 61)) | (1L << (Interface - 61)) | (1L << (Package - 61)) | (1L << (Protected - 61)) | (1L << (Static - 61)) | (1L << (Any - 61)) | (1L << (Number - 61)) | (1L << (Boolean - 61)) | (1L << (String - 61)) | (1L << (Symbol - 61)) | (1L << (TypeAlias - 61)))) != 0))) {
                    _errHandler.recoverInline(this);
                } else {
                    if (_input.LA(1) == Token.EOF) matchedEOF = true;
                    _errHandler.reportMatch(this);
                    consume();
                }
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class LiteralContext extends ParserRuleContext {
        public TerminalNode NullLiteral() {
            return getToken(XPathCommon.NullLiteral, 0);
        }

        public TerminalNode BooleanLiteral() {
            return getToken(XPathCommon.BooleanLiteral, 0);
        }

        public TerminalNode StringLiteral() {
            return getToken(XPathCommon.StringLiteral, 0);
        }

        public TerminalNode TemplateStringLiteral() {
            return getToken(XPathCommon.TemplateStringLiteral, 0);
        }

        public TerminalNode RegularExpressionLiteral() {
            return getToken(XPathCommon.RegularExpressionLiteral, 0);
        }

        public NumericLiteralContext numericLiteral() {
            return getRuleContext(NumericLiteralContext.class, 0);
        }

        public LiteralContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_literal;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathCommonListener) ((XPathCommonListener) listener).enterLiteral(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathCommonListener) ((XPathCommonListener) listener).exitLiteral(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathCommonVisitor)
                return ((XPathCommonVisitor<? extends T>) visitor).visitLiteral(this);
            else return visitor.visitChildren(this);
        }
    }

    public final LiteralContext literal() throws RecognitionException {
        LiteralContext _localctx = new LiteralContext(_ctx, getState());
        enterRule(_localctx, 14, RULE_literal);
        try {
            setState(59);
            _errHandler.sync(this);
            switch (_input.LA(1)) {
                case NullLiteral:
                    enterOuterAlt(_localctx, 1);
                {
                    setState(53);
                    match(NullLiteral);
                }
                break;
                case BooleanLiteral:
                    enterOuterAlt(_localctx, 2);
                {
                    setState(54);
                    match(BooleanLiteral);
                }
                break;
                case StringLiteral:
                    enterOuterAlt(_localctx, 3);
                {
                    setState(55);
                    match(StringLiteral);
                }
                break;
                case TemplateStringLiteral:
                    enterOuterAlt(_localctx, 4);
                {
                    setState(56);
                    match(TemplateStringLiteral);
                }
                break;
                case RegularExpressionLiteral:
                    enterOuterAlt(_localctx, 5);
                {
                    setState(57);
                    match(RegularExpressionLiteral);
                }
                break;
                case DecimalIntegerLiteral:
                case HexIntegerLiteral:
                case BinaryIntegerLiteral:
                case DecimalLiteral:
                    enterOuterAlt(_localctx, 6);
                {
                    setState(58);
                    numericLiteral();
                }
                break;
                default:
                    throw new NoViableAltException(this);
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static class NumericLiteralContext extends ParserRuleContext {
        public TerminalNode DecimalIntegerLiteral() {
            return getToken(XPathCommon.DecimalIntegerLiteral, 0);
        }

        public TerminalNode HexIntegerLiteral() {
            return getToken(XPathCommon.HexIntegerLiteral, 0);
        }

        public TerminalNode DecimalLiteral() {
            return getToken(XPathCommon.DecimalLiteral, 0);
        }

        public TerminalNode BinaryIntegerLiteral() {
            return getToken(XPathCommon.BinaryIntegerLiteral, 0);
        }

        public NumericLiteralContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_numericLiteral;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathCommonListener) ((XPathCommonListener) listener).enterNumericLiteral(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathCommonListener) ((XPathCommonListener) listener).exitNumericLiteral(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathCommonVisitor)
                return ((XPathCommonVisitor<? extends T>) visitor).visitNumericLiteral(this);
            else return visitor.visitChildren(this);
        }
    }

    public final NumericLiteralContext numericLiteral() throws RecognitionException {
        NumericLiteralContext _localctx = new NumericLiteralContext(_ctx, getState());
        enterRule(_localctx, 16, RULE_numericLiteral);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(61);
                _la = _input.LA(1);
                if (!((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << DecimalIntegerLiteral) | (1L << HexIntegerLiteral) | (1L << BinaryIntegerLiteral) | (1L << DecimalLiteral))) != 0))) {
                    _errHandler.recoverInline(this);
                } else {
                    if (_input.LA(1) == Token.EOF) matchedEOF = true;
                    _errHandler.reportMatch(this);
                    consume();
                }
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            exitRule();
        }
        return _localctx;
    }

    public static final String _serializedATN =
            "\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\3}B\4\2\t\2\4\3\t\3" +
                    "\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\3\2\3\2\3\2\7" +
                    "\2\30\n\2\f\2\16\2\33\13\2\3\3\3\3\3\3\7\3 \n\3\f\3\16\3#\13\3\3\4\3\4" +
                    "\5\4\'\n\4\3\5\3\5\3\6\3\6\5\6-\n\6\3\7\3\7\3\7\3\7\3\7\5\7\64\n\7\3\b" +
                    "\3\b\3\t\3\t\3\t\3\t\3\t\3\t\5\t>\n\t\3\n\3\n\3\n\2\2\13\2\4\6\b\n\f\16" +
                    "\20\22\2\5\5\2ZZrrvv\5\2?Y[[^r\3\2;>\2E\2\24\3\2\2\2\4\34\3\2\2\2\6&\3" +
                    "\2\2\2\b(\3\2\2\2\n,\3\2\2\2\f\63\3\2\2\2\16\65\3\2\2\2\20=\3\2\2\2\22" +
                    "?\3\2\2\2\24\31\7v\2\2\25\26\7\21\2\2\26\30\5\b\5\2\27\25\3\2\2\2\30\33" +
                    "\3\2\2\2\31\27\3\2\2\2\31\32\3\2\2\2\32\3\3\2\2\2\33\31\3\2\2\2\34!\7" +
                    "v\2\2\35\36\7\21\2\2\36 \5\b\5\2\37\35\3\2\2\2 #\3\2\2\2!\37\3\2\2\2!" +
                    "\"\3\2\2\2\"\5\3\2\2\2#!\3\2\2\2$\'\5\n\6\2%\'\7w\2\2&$\3\2\2\2&%\3\2" +
                    "\2\2\'\7\3\2\2\2()\t\2\2\2)\t\3\2\2\2*-\5\b\5\2+-\5\f\7\2,*\3\2\2\2,+" +
                    "\3\2\2\2-\13\3\2\2\2.\64\5\16\b\2/\64\7\67\2\2\60\64\78\2\2\61\64\79\2" +
                    "\2\62\64\7:\2\2\63.\3\2\2\2\63/\3\2\2\2\63\60\3\2\2\2\63\61\3\2\2\2\63" +
                    "\62\3\2\2\2\64\r\3\2\2\2\65\66\t\3\2\2\66\17\3\2\2\2\67>\7\67\2\28>\7" +
                    "8\2\29>\7w\2\2:>\7x\2\2;>\7}\2\2<>\5\22\n\2=\67\3\2\2\2=8\3\2\2\2=9\3" +
                    "\2\2\2=:\3\2\2\2=;\3\2\2\2=<\3\2\2\2>\21\3\2\2\2?@\t\4\2\2@\23\3\2\2\2" +
                    "\b\31!&,\63=";
    public static final ATN _ATN =
            new ATNDeserializer().deserialize(_serializedATN.toCharArray());

    static {
        _decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
        for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
            _decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
        }
    }
}