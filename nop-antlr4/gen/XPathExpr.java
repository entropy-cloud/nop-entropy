/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
// Generated from /home/u/sources/platform/entropy-cloud/nop-antlr4/nop-antlr4-xpath/src/main/antlr4/imports/XPathExpr.g4 by ANTLR 4.9

import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;

import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast"})
public class XPathExpr extends Parser {
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
            RULE_parameterList = 0, RULE_parameter = 1, RULE_identifierOrPattern = 2,
            RULE_namespaceName = 3, RULE_arrayLiteral = 4, RULE_elementList = 5, RULE_arrayElement = 6,
            RULE_objectLiteral = 7, RULE_propertyAssignment = 8, RULE_arguments = 9,
            RULE_argumentList = 10, RULE_argument = 11, RULE_expressionSequence = 12,
            RULE_initExpression = 13, RULE_initExpressionSequence = 14, RULE_singleExpression = 15,
            RULE_qualifiedType = 16, RULE_qualifiedName = 17, RULE_propertyName = 18,
            RULE_identifier = 19, RULE_identifierOrKeyword = 20, RULE_reservedWord = 21,
            RULE_keyword = 22, RULE_literal = 23, RULE_numericLiteral = 24;

    private static String[] makeRuleNames() {
        return new String[]{
                "parameterList", "parameter", "identifierOrPattern", "namespaceName",
                "arrayLiteral", "elementList", "arrayElement", "objectLiteral", "propertyAssignment",
                "arguments", "argumentList", "argument", "expressionSequence", "initExpression",
                "initExpressionSequence", "singleExpression", "qualifiedType", "qualifiedName",
                "propertyName", "identifier", "identifierOrKeyword", "reservedWord",
                "keyword", "literal", "numericLiteral"
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
        return "XPathExpr.g4";
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

    public XPathExpr(TokenStream input) {
        super(input);
        _interp = new ParserATNSimulator(this, _ATN, _decisionToDFA, _sharedContextCache);
    }

    public static class ParameterListContext extends ParserRuleContext {
        public List<ParameterContext> parameter() {
            return getRuleContexts(ParameterContext.class);
        }

        public ParameterContext parameter(int i) {
            return getRuleContext(ParameterContext.class, i);
        }

        public List<TerminalNode> Comma() {
            return getTokens(XPathExpr.Comma);
        }

        public TerminalNode Comma(int i) {
            return getToken(XPathExpr.Comma, i);
        }

        public ParameterListContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_parameterList;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterParameterList(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitParameterList(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitParameterList(this);
            else return visitor.visitChildren(this);
        }
    }

    public final ParameterListContext parameterList() throws RecognitionException {
        ParameterListContext _localctx = new ParameterListContext(_ctx, getState());
        enterRule(_localctx, 0, RULE_parameterList);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(50);
                parameter();
                setState(55);
                _errHandler.sync(this);
                _la = _input.LA(1);
                while (_la == Comma) {
                    {
                        {
                            setState(51);
                            match(Comma);
                            setState(52);
                            parameter();
                        }
                    }
                    setState(57);
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

    public static class ParameterContext extends ParserRuleContext {
        public IdentifierOrPatternContext identifierOrPattern() {
            return getRuleContext(IdentifierOrPatternContext.class, 0);
        }

        public ParameterContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_parameter;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterParameter(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitParameter(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitParameter(this);
            else return visitor.visitChildren(this);
        }
    }

    public final ParameterContext parameter() throws RecognitionException {
        ParameterContext _localctx = new ParameterContext(_ctx, getState());
        enterRule(_localctx, 2, RULE_parameter);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(58);
                identifierOrPattern();
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

    public static class IdentifierOrPatternContext extends ParserRuleContext {
        public IdentifierContext identifier() {
            return getRuleContext(IdentifierContext.class, 0);
        }

        public IdentifierOrPatternContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_identifierOrPattern;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterIdentifierOrPattern(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitIdentifierOrPattern(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitIdentifierOrPattern(this);
            else return visitor.visitChildren(this);
        }
    }

    public final IdentifierOrPatternContext identifierOrPattern() throws RecognitionException {
        IdentifierOrPatternContext _localctx = new IdentifierOrPatternContext(_ctx, getState());
        enterRule(_localctx, 4, RULE_identifierOrPattern);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(60);
                identifier();
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

    public static class NamespaceNameContext extends ParserRuleContext {
        public TerminalNode Identifier() {
            return getToken(XPathExpr.Identifier, 0);
        }

        public List<TerminalNode> Dot() {
            return getTokens(XPathExpr.Dot);
        }

        public TerminalNode Dot(int i) {
            return getToken(XPathExpr.Dot, i);
        }

        public List<IdentifierContext> identifier() {
            return getRuleContexts(IdentifierContext.class);
        }

        public IdentifierContext identifier(int i) {
            return getRuleContext(IdentifierContext.class, i);
        }

        public NamespaceNameContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_namespaceName;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterNamespaceName(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitNamespaceName(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitNamespaceName(this);
            else return visitor.visitChildren(this);
        }
    }

    public final NamespaceNameContext namespaceName() throws RecognitionException {
        NamespaceNameContext _localctx = new NamespaceNameContext(_ctx, getState());
        enterRule(_localctx, 6, RULE_namespaceName);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(62);
                match(Identifier);
                setState(67);
                _errHandler.sync(this);
                _la = _input.LA(1);
                while (_la == Dot) {
                    {
                        {
                            setState(63);
                            match(Dot);
                            setState(64);
                            identifier();
                        }
                    }
                    setState(69);
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

    public static class ArrayLiteralContext extends ParserRuleContext {
        public TerminalNode OpenBracket() {
            return getToken(XPathExpr.OpenBracket, 0);
        }

        public TerminalNode CloseBracket() {
            return getToken(XPathExpr.CloseBracket, 0);
        }

        public ElementListContext elementList() {
            return getRuleContext(ElementListContext.class, 0);
        }

        public ArrayLiteralContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_arrayLiteral;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterArrayLiteral(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitArrayLiteral(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitArrayLiteral(this);
            else return visitor.visitChildren(this);
        }
    }

    public final ArrayLiteralContext arrayLiteral() throws RecognitionException {
        ArrayLiteralContext _localctx = new ArrayLiteralContext(_ctx, getState());
        enterRule(_localctx, 8, RULE_arrayLiteral);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                {
                    setState(70);
                    match(OpenBracket);
                    setState(72);
                    _errHandler.sync(this);
                    _la = _input.LA(1);
                    if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << OpenBracket) | (1L << OpenParen) | (1L << OpenBrace) | (1L << Ellipsis) | (1L << PlusPlus) | (1L << MinusMinus) | (1L << Plus) | (1L << Minus) | (1L << BitNot) | (1L << Not) | (1L << NullLiteral) | (1L << BooleanLiteral) | (1L << DecimalIntegerLiteral) | (1L << HexIntegerLiteral) | (1L << BinaryIntegerLiteral) | (1L << DecimalLiteral))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (Typeof - 64)) | (1L << (Switch - 64)) | (1L << (This - 64)) | (1L << (If - 64)) | (1L << (Super - 64)) | (1L << (Identifier - 64)) | (1L << (StringLiteral - 64)) | (1L << (TemplateStringLiteral - 64)) | (1L << (RegularExpressionLiteral - 64)))) != 0)) {
                        {
                            setState(71);
                            elementList();
                        }
                    }

                    setState(74);
                    match(CloseBracket);
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

    public static class ElementListContext extends ParserRuleContext {
        public List<ArrayElementContext> arrayElement() {
            return getRuleContexts(ArrayElementContext.class);
        }

        public ArrayElementContext arrayElement(int i) {
            return getRuleContext(ArrayElementContext.class, i);
        }

        public List<TerminalNode> Comma() {
            return getTokens(XPathExpr.Comma);
        }

        public TerminalNode Comma(int i) {
            return getToken(XPathExpr.Comma, i);
        }

        public ElementListContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_elementList;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterElementList(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitElementList(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitElementList(this);
            else return visitor.visitChildren(this);
        }
    }

    public final ElementListContext elementList() throws RecognitionException {
        ElementListContext _localctx = new ElementListContext(_ctx, getState());
        enterRule(_localctx, 10, RULE_elementList);
        int _la;
        try {
            int _alt;
            enterOuterAlt(_localctx, 1);
            {
                setState(76);
                arrayElement();
                setState(81);
                _errHandler.sync(this);
                _alt = getInterpreter().adaptivePredict(_input, 3, _ctx);
                while (_alt != 2 && _alt != org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER) {
                    if (_alt == 1) {
                        {
                            {
                                setState(77);
                                match(Comma);
                                setState(78);
                                arrayElement();
                            }
                        }
                    }
                    setState(83);
                    _errHandler.sync(this);
                    _alt = getInterpreter().adaptivePredict(_input, 3, _ctx);
                }
                setState(85);
                _errHandler.sync(this);
                _la = _input.LA(1);
                if (_la == Comma) {
                    {
                        setState(84);
                        match(Comma);
                    }
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

    public static class ArrayElementContext extends ParserRuleContext {
        public SingleExpressionContext singleExpression() {
            return getRuleContext(SingleExpressionContext.class, 0);
        }

        public TerminalNode Ellipsis() {
            return getToken(XPathExpr.Ellipsis, 0);
        }

        public ArrayElementContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_arrayElement;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterArrayElement(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitArrayElement(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitArrayElement(this);
            else return visitor.visitChildren(this);
        }
    }

    public final ArrayElementContext arrayElement() throws RecognitionException {
        ArrayElementContext _localctx = new ArrayElementContext(_ctx, getState());
        enterRule(_localctx, 12, RULE_arrayElement);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(88);
                _errHandler.sync(this);
                _la = _input.LA(1);
                if (_la == Ellipsis) {
                    {
                        setState(87);
                        match(Ellipsis);
                    }
                }

                setState(90);
                singleExpression(0);
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

    public static class ObjectLiteralContext extends ParserRuleContext {
        public TerminalNode OpenBrace() {
            return getToken(XPathExpr.OpenBrace, 0);
        }

        public TerminalNode CloseBrace() {
            return getToken(XPathExpr.CloseBrace, 0);
        }

        public List<PropertyAssignmentContext> propertyAssignment() {
            return getRuleContexts(PropertyAssignmentContext.class);
        }

        public PropertyAssignmentContext propertyAssignment(int i) {
            return getRuleContext(PropertyAssignmentContext.class, i);
        }

        public List<TerminalNode> Comma() {
            return getTokens(XPathExpr.Comma);
        }

        public TerminalNode Comma(int i) {
            return getToken(XPathExpr.Comma, i);
        }

        public ObjectLiteralContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_objectLiteral;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterObjectLiteral(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitObjectLiteral(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitObjectLiteral(this);
            else return visitor.visitChildren(this);
        }
    }

    public final ObjectLiteralContext objectLiteral() throws RecognitionException {
        ObjectLiteralContext _localctx = new ObjectLiteralContext(_ctx, getState());
        enterRule(_localctx, 14, RULE_objectLiteral);
        int _la;
        try {
            int _alt;
            enterOuterAlt(_localctx, 1);
            {
                setState(92);
                match(OpenBrace);
                setState(101);
                _errHandler.sync(this);
                _la = _input.LA(1);
                if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << OpenBracket) | (1L << Ellipsis) | (1L << NullLiteral) | (1L << BooleanLiteral) | (1L << AndLiteral) | (1L << OrLiteral) | (1L << Break) | (1L << Do) | (1L << Instanceof))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (Typeof - 64)) | (1L << (Case - 64)) | (1L << (Else - 64)) | (1L << (New - 64)) | (1L << (Var - 64)) | (1L << (Catch - 64)) | (1L << (Finally - 64)) | (1L << (Return - 64)) | (1L << (Void - 64)) | (1L << (Continue - 64)) | (1L << (For - 64)) | (1L << (Switch - 64)) | (1L << (While - 64)) | (1L << (Debugger - 64)) | (1L << (Function - 64)) | (1L << (This - 64)) | (1L << (With - 64)) | (1L << (Default - 64)) | (1L << (If - 64)) | (1L << (Throw - 64)) | (1L << (Delete - 64)) | (1L << (In - 64)) | (1L << (Try - 64)) | (1L << (As - 64)) | (1L << (From - 64)) | (1L << (ReadOnly - 64)) | (1L << (Class - 64)) | (1L << (Enum - 64)) | (1L << (Extends - 64)) | (1L << (Super - 64)) | (1L << (Const - 64)) | (1L << (Export - 64)) | (1L << (Import - 64)) | (1L << (Implements - 64)) | (1L << (Let - 64)) | (1L << (Private - 64)) | (1L << (Public - 64)) | (1L << (Interface - 64)) | (1L << (Package - 64)) | (1L << (Protected - 64)) | (1L << (Static - 64)) | (1L << (Any - 64)) | (1L << (Number - 64)) | (1L << (Boolean - 64)) | (1L << (String - 64)) | (1L << (Symbol - 64)) | (1L << (TypeAlias - 64)) | (1L << (Identifier - 64)) | (1L << (StringLiteral - 64)))) != 0)) {
                    {
                        setState(93);
                        propertyAssignment();
                        setState(98);
                        _errHandler.sync(this);
                        _alt = getInterpreter().adaptivePredict(_input, 6, _ctx);
                        while (_alt != 2 && _alt != org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER) {
                            if (_alt == 1) {
                                {
                                    {
                                        setState(94);
                                        match(Comma);
                                        setState(95);
                                        propertyAssignment();
                                    }
                                }
                            }
                            setState(100);
                            _errHandler.sync(this);
                            _alt = getInterpreter().adaptivePredict(_input, 6, _ctx);
                        }
                    }
                }

                setState(104);
                _errHandler.sync(this);
                _la = _input.LA(1);
                if (_la == Comma) {
                    {
                        setState(103);
                        match(Comma);
                    }
                }

                setState(106);
                match(CloseBrace);
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

    public static class PropertyAssignmentContext extends ParserRuleContext {
        public PropertyAssignmentContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_propertyAssignment;
        }

        public PropertyAssignmentContext() {
        }

        public void copyFrom(PropertyAssignmentContext ctx) {
            super.copyFrom(ctx);
        }
    }

    public static class PropertyExpressionAssignmentContext extends PropertyAssignmentContext {
        public PropertyNameContext propertyName() {
            return getRuleContext(PropertyNameContext.class, 0);
        }

        public TerminalNode Colon() {
            return getToken(XPathExpr.Colon, 0);
        }

        public SingleExpressionContext singleExpression() {
            return getRuleContext(SingleExpressionContext.class, 0);
        }

        public PropertyExpressionAssignmentContext(PropertyAssignmentContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener)
                ((XPathExprListener) listener).enterPropertyExpressionAssignment(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener)
                ((XPathExprListener) listener).exitPropertyExpressionAssignment(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitPropertyExpressionAssignment(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class ComputedPropertyExpressionAssignmentContext extends PropertyAssignmentContext {
        public SingleExpressionContext key;
        public SingleExpressionContext value;

        public TerminalNode OpenBracket() {
            return getToken(XPathExpr.OpenBracket, 0);
        }

        public TerminalNode CloseBracket() {
            return getToken(XPathExpr.CloseBracket, 0);
        }

        public TerminalNode Colon() {
            return getToken(XPathExpr.Colon, 0);
        }

        public List<SingleExpressionContext> singleExpression() {
            return getRuleContexts(SingleExpressionContext.class);
        }

        public SingleExpressionContext singleExpression(int i) {
            return getRuleContext(SingleExpressionContext.class, i);
        }

        public ComputedPropertyExpressionAssignmentContext(PropertyAssignmentContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener)
                ((XPathExprListener) listener).enterComputedPropertyExpressionAssignment(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener)
                ((XPathExprListener) listener).exitComputedPropertyExpressionAssignment(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitComputedPropertyExpressionAssignment(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class PropertyShorthandContext extends PropertyAssignmentContext {
        public IdentifierOrKeywordContext identifierOrKeyword() {
            return getRuleContext(IdentifierOrKeywordContext.class, 0);
        }

        public PropertyShorthandContext(PropertyAssignmentContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterPropertyShorthand(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitPropertyShorthand(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitPropertyShorthand(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class RestParameterInObjectContext extends PropertyAssignmentContext {
        public TerminalNode Ellipsis() {
            return getToken(XPathExpr.Ellipsis, 0);
        }

        public SingleExpressionContext singleExpression() {
            return getRuleContext(SingleExpressionContext.class, 0);
        }

        public RestParameterInObjectContext(PropertyAssignmentContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterRestParameterInObject(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitRestParameterInObject(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitRestParameterInObject(this);
            else return visitor.visitChildren(this);
        }
    }

    public final PropertyAssignmentContext propertyAssignment() throws RecognitionException {
        PropertyAssignmentContext _localctx = new PropertyAssignmentContext(_ctx, getState());
        enterRule(_localctx, 16, RULE_propertyAssignment);
        try {
            setState(121);
            _errHandler.sync(this);
            switch (getInterpreter().adaptivePredict(_input, 9, _ctx)) {
                case 1:
                    _localctx = new PropertyExpressionAssignmentContext(_localctx);
                    enterOuterAlt(_localctx, 1);
                {
                    setState(108);
                    propertyName();
                    setState(109);
                    match(Colon);
                    setState(110);
                    singleExpression(0);
                }
                break;
                case 2:
                    _localctx = new ComputedPropertyExpressionAssignmentContext(_localctx);
                    enterOuterAlt(_localctx, 2);
                {
                    setState(112);
                    match(OpenBracket);
                    setState(113);
                    ((ComputedPropertyExpressionAssignmentContext) _localctx).key = singleExpression(0);
                    setState(114);
                    match(CloseBracket);
                    setState(115);
                    match(Colon);
                    setState(116);
                    ((ComputedPropertyExpressionAssignmentContext) _localctx).value = singleExpression(0);
                }
                break;
                case 3:
                    _localctx = new PropertyShorthandContext(_localctx);
                    enterOuterAlt(_localctx, 3);
                {
                    setState(118);
                    identifierOrKeyword();
                }
                break;
                case 4:
                    _localctx = new RestParameterInObjectContext(_localctx);
                    enterOuterAlt(_localctx, 4);
                {
                    setState(119);
                    match(Ellipsis);
                    setState(120);
                    singleExpression(0);
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

    public static class ArgumentsContext extends ParserRuleContext {
        public TerminalNode OpenParen() {
            return getToken(XPathExpr.OpenParen, 0);
        }

        public TerminalNode CloseParen() {
            return getToken(XPathExpr.CloseParen, 0);
        }

        public ArgumentListContext argumentList() {
            return getRuleContext(ArgumentListContext.class, 0);
        }

        public ArgumentsContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_arguments;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterArguments(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitArguments(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitArguments(this);
            else return visitor.visitChildren(this);
        }
    }

    public final ArgumentsContext arguments() throws RecognitionException {
        ArgumentsContext _localctx = new ArgumentsContext(_ctx, getState());
        enterRule(_localctx, 18, RULE_arguments);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(123);
                match(OpenParen);
                setState(125);
                _errHandler.sync(this);
                _la = _input.LA(1);
                if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << OpenBracket) | (1L << OpenParen) | (1L << OpenBrace) | (1L << PlusPlus) | (1L << MinusMinus) | (1L << Plus) | (1L << Minus) | (1L << BitNot) | (1L << Not) | (1L << NullLiteral) | (1L << BooleanLiteral) | (1L << DecimalIntegerLiteral) | (1L << HexIntegerLiteral) | (1L << BinaryIntegerLiteral) | (1L << DecimalLiteral))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (Typeof - 64)) | (1L << (Switch - 64)) | (1L << (This - 64)) | (1L << (If - 64)) | (1L << (Super - 64)) | (1L << (Identifier - 64)) | (1L << (StringLiteral - 64)) | (1L << (TemplateStringLiteral - 64)) | (1L << (RegularExpressionLiteral - 64)))) != 0)) {
                    {
                        setState(124);
                        argumentList();
                    }
                }

                setState(127);
                match(CloseParen);
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

    public static class ArgumentListContext extends ParserRuleContext {
        public List<ArgumentContext> argument() {
            return getRuleContexts(ArgumentContext.class);
        }

        public ArgumentContext argument(int i) {
            return getRuleContext(ArgumentContext.class, i);
        }

        public List<TerminalNode> Comma() {
            return getTokens(XPathExpr.Comma);
        }

        public TerminalNode Comma(int i) {
            return getToken(XPathExpr.Comma, i);
        }

        public ArgumentListContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_argumentList;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterArgumentList(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitArgumentList(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitArgumentList(this);
            else return visitor.visitChildren(this);
        }
    }

    public final ArgumentListContext argumentList() throws RecognitionException {
        ArgumentListContext _localctx = new ArgumentListContext(_ctx, getState());
        enterRule(_localctx, 20, RULE_argumentList);
        int _la;
        try {
            int _alt;
            enterOuterAlt(_localctx, 1);
            {
                setState(129);
                argument();
                setState(134);
                _errHandler.sync(this);
                _alt = getInterpreter().adaptivePredict(_input, 11, _ctx);
                while (_alt != 2 && _alt != org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER) {
                    if (_alt == 1) {
                        {
                            {
                                setState(130);
                                match(Comma);
                                setState(131);
                                argument();
                            }
                        }
                    }
                    setState(136);
                    _errHandler.sync(this);
                    _alt = getInterpreter().adaptivePredict(_input, 11, _ctx);
                }
                setState(138);
                _errHandler.sync(this);
                _la = _input.LA(1);
                if (_la == Comma) {
                    {
                        setState(137);
                        match(Comma);
                    }
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

    public static class ArgumentContext extends ParserRuleContext {
        public SingleExpressionContext singleExpression() {
            return getRuleContext(SingleExpressionContext.class, 0);
        }

        public ArgumentContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_argument;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterArgument(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitArgument(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitArgument(this);
            else return visitor.visitChildren(this);
        }
    }

    public final ArgumentContext argument() throws RecognitionException {
        ArgumentContext _localctx = new ArgumentContext(_ctx, getState());
        enterRule(_localctx, 22, RULE_argument);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(140);
                singleExpression(0);
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

    public static class ExpressionSequenceContext extends ParserRuleContext {
        public List<SingleExpressionContext> singleExpression() {
            return getRuleContexts(SingleExpressionContext.class);
        }

        public SingleExpressionContext singleExpression(int i) {
            return getRuleContext(SingleExpressionContext.class, i);
        }

        public List<TerminalNode> Comma() {
            return getTokens(XPathExpr.Comma);
        }

        public TerminalNode Comma(int i) {
            return getToken(XPathExpr.Comma, i);
        }

        public ExpressionSequenceContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_expressionSequence;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterExpressionSequence(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitExpressionSequence(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitExpressionSequence(this);
            else return visitor.visitChildren(this);
        }
    }

    public final ExpressionSequenceContext expressionSequence() throws RecognitionException {
        ExpressionSequenceContext _localctx = new ExpressionSequenceContext(_ctx, getState());
        enterRule(_localctx, 24, RULE_expressionSequence);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(142);
                singleExpression(0);
                setState(147);
                _errHandler.sync(this);
                _la = _input.LA(1);
                while (_la == Comma) {
                    {
                        {
                            setState(143);
                            match(Comma);
                            setState(144);
                            singleExpression(0);
                        }
                    }
                    setState(149);
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

    public static class InitExpressionContext extends ParserRuleContext {
        public TerminalNode Identifier() {
            return getToken(XPathExpr.Identifier, 0);
        }

        public TerminalNode Assign() {
            return getToken(XPathExpr.Assign, 0);
        }

        public SingleExpressionContext singleExpression() {
            return getRuleContext(SingleExpressionContext.class, 0);
        }

        public InitExpressionContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_initExpression;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterInitExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitInitExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitInitExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public final InitExpressionContext initExpression() throws RecognitionException {
        InitExpressionContext _localctx = new InitExpressionContext(_ctx, getState());
        enterRule(_localctx, 26, RULE_initExpression);
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(150);
                match(Identifier);
                setState(151);
                match(Assign);
                setState(152);
                singleExpression(0);
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

    public static class InitExpressionSequenceContext extends ParserRuleContext {
        public List<InitExpressionContext> initExpression() {
            return getRuleContexts(InitExpressionContext.class);
        }

        public InitExpressionContext initExpression(int i) {
            return getRuleContext(InitExpressionContext.class, i);
        }

        public List<TerminalNode> Comma() {
            return getTokens(XPathExpr.Comma);
        }

        public TerminalNode Comma(int i) {
            return getToken(XPathExpr.Comma, i);
        }

        public InitExpressionSequenceContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_initExpressionSequence;
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterInitExpressionSequence(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitInitExpressionSequence(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitInitExpressionSequence(this);
            else return visitor.visitChildren(this);
        }
    }

    public final InitExpressionSequenceContext initExpressionSequence() throws RecognitionException {
        InitExpressionSequenceContext _localctx = new InitExpressionSequenceContext(_ctx, getState());
        enterRule(_localctx, 28, RULE_initExpressionSequence);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(154);
                initExpression();
                setState(159);
                _errHandler.sync(this);
                _la = _input.LA(1);
                while (_la == Comma) {
                    {
                        {
                            setState(155);
                            match(Comma);
                            setState(156);
                            initExpression();
                        }
                    }
                    setState(161);
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

    public static class SingleExpressionContext extends ParserRuleContext {
        public SingleExpressionContext(ParserRuleContext parent, int invokingState) {
            super(parent, invokingState);
        }

        @Override
        public int getRuleIndex() {
            return RULE_singleExpression;
        }

        public SingleExpressionContext() {
        }

        public void copyFrom(SingleExpressionContext ctx) {
            super.copyFrom(ctx);
        }
    }

    public static class TemplateStringExpressionContext extends SingleExpressionContext {
        public TerminalNode Identifier() {
            return getToken(XPathExpr.Identifier, 0);
        }

        public TerminalNode TemplateStringLiteral() {
            return getToken(XPathExpr.TemplateStringLiteral, 0);
        }

        public TemplateStringExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener)
                ((XPathExprListener) listener).enterTemplateStringExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener)
                ((XPathExprListener) listener).exitTemplateStringExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitTemplateStringExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class TernaryExpressionContext extends SingleExpressionContext {
        public SingleExpressionContext test;
        public SingleExpressionContext consequence;
        public SingleExpressionContext alternate;

        public TerminalNode Question() {
            return getToken(XPathExpr.Question, 0);
        }

        public TerminalNode Colon() {
            return getToken(XPathExpr.Colon, 0);
        }

        public List<SingleExpressionContext> singleExpression() {
            return getRuleContexts(SingleExpressionContext.class);
        }

        public SingleExpressionContext singleExpression(int i) {
            return getRuleContext(SingleExpressionContext.class, i);
        }

        public TernaryExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterTernaryExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitTernaryExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitTernaryExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class LogicalAndExpressionContext extends SingleExpressionContext {
        public SingleExpressionContext left;
        public Token bop;
        public SingleExpressionContext right;

        public List<SingleExpressionContext> singleExpression() {
            return getRuleContexts(SingleExpressionContext.class);
        }

        public SingleExpressionContext singleExpression(int i) {
            return getRuleContext(SingleExpressionContext.class, i);
        }

        public TerminalNode And() {
            return getToken(XPathExpr.And, 0);
        }

        public TerminalNode AndLiteral() {
            return getToken(XPathExpr.AndLiteral, 0);
        }

        public LogicalAndExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterLogicalAndExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitLogicalAndExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitLogicalAndExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class ChainExpressionContext extends SingleExpressionContext {
        public SingleExpressionContext singleExpression() {
            return getRuleContext(SingleExpressionContext.class, 0);
        }

        public TerminalNode Not() {
            return getToken(XPathExpr.Not, 0);
        }

        public ChainExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterChainExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitChainExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitChainExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class SwitchExpressionContext extends SingleExpressionContext {
        public ArgumentContext value;

        public TerminalNode Switch() {
            return getToken(XPathExpr.Switch, 0);
        }

        public TerminalNode OpenParen() {
            return getToken(XPathExpr.OpenParen, 0);
        }

        public TerminalNode Comma() {
            return getToken(XPathExpr.Comma, 0);
        }

        public ArgumentListContext argumentList() {
            return getRuleContext(ArgumentListContext.class, 0);
        }

        public TerminalNode CloseParen() {
            return getToken(XPathExpr.CloseParen, 0);
        }

        public ArgumentContext argument() {
            return getRuleContext(ArgumentContext.class, 0);
        }

        public SwitchExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterSwitchExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitSwitchExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitSwitchExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class PreIncrementExpressionContext extends SingleExpressionContext {
        public Token op;

        public SingleExpressionContext singleExpression() {
            return getRuleContext(SingleExpressionContext.class, 0);
        }

        public TerminalNode PlusPlus() {
            return getToken(XPathExpr.PlusPlus, 0);
        }

        public PreIncrementExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterPreIncrementExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitPreIncrementExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitPreIncrementExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class ObjectLiteralExpressionContext extends SingleExpressionContext {
        public ObjectLiteralContext objectLiteral() {
            return getRuleContext(ObjectLiteralContext.class, 0);
        }

        public ObjectLiteralExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener)
                ((XPathExprListener) listener).enterObjectLiteralExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitObjectLiteralExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitObjectLiteralExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class InExpressionContext extends SingleExpressionContext {
        public SingleExpressionContext left;
        public SingleExpressionContext right;

        public TerminalNode In() {
            return getToken(XPathExpr.In, 0);
        }

        public List<SingleExpressionContext> singleExpression() {
            return getRuleContexts(SingleExpressionContext.class);
        }

        public SingleExpressionContext singleExpression(int i) {
            return getRuleContext(SingleExpressionContext.class, i);
        }

        public InExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterInExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitInExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitInExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class LogicalOrExpressionContext extends SingleExpressionContext {
        public SingleExpressionContext left;
        public Token bop;
        public SingleExpressionContext right;

        public List<SingleExpressionContext> singleExpression() {
            return getRuleContexts(SingleExpressionContext.class);
        }

        public SingleExpressionContext singleExpression(int i) {
            return getRuleContext(SingleExpressionContext.class, i);
        }

        public TerminalNode Or() {
            return getToken(XPathExpr.Or, 0);
        }

        public TerminalNode OrLiteral() {
            return getToken(XPathExpr.OrLiteral, 0);
        }

        public LogicalOrExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterLogicalOrExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitLogicalOrExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitLogicalOrExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class NotExpressionContext extends SingleExpressionContext {
        public Token op;

        public SingleExpressionContext singleExpression() {
            return getRuleContext(SingleExpressionContext.class, 0);
        }

        public TerminalNode Not() {
            return getToken(XPathExpr.Not, 0);
        }

        public NotExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterNotExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitNotExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitNotExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class PreDecreaseExpressionContext extends SingleExpressionContext {
        public Token op;

        public SingleExpressionContext singleExpression() {
            return getRuleContext(SingleExpressionContext.class, 0);
        }

        public TerminalNode MinusMinus() {
            return getToken(XPathExpr.MinusMinus, 0);
        }

        public PreDecreaseExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterPreDecreaseExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitPreDecreaseExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitPreDecreaseExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class ThisExpressionContext extends SingleExpressionContext {
        public TerminalNode This() {
            return getToken(XPathExpr.This, 0);
        }

        public ThisExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterThisExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitThisExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitThisExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class UnaryMinusExpressionContext extends SingleExpressionContext {
        public Token op;

        public SingleExpressionContext singleExpression() {
            return getRuleContext(SingleExpressionContext.class, 0);
        }

        public TerminalNode Minus() {
            return getToken(XPathExpr.Minus, 0);
        }

        public UnaryMinusExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterUnaryMinusExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitUnaryMinusExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitUnaryMinusExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class PostDecreaseExpressionContext extends SingleExpressionContext {
        public SingleExpressionContext singleExpression() {
            return getRuleContext(SingleExpressionContext.class, 0);
        }

        public TerminalNode MinusMinus() {
            return getToken(XPathExpr.MinusMinus, 0);
        }

        public PostDecreaseExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterPostDecreaseExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitPostDecreaseExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitPostDecreaseExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class TypeofExpressionContext extends SingleExpressionContext {
        public TerminalNode Typeof() {
            return getToken(XPathExpr.Typeof, 0);
        }

        public SingleExpressionContext singleExpression() {
            return getRuleContext(SingleExpressionContext.class, 0);
        }

        public TypeofExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterTypeofExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitTypeofExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitTypeofExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class InstanceofExpressionContext extends SingleExpressionContext {
        public SingleExpressionContext left;
        public QualifiedTypeContext right;

        public TerminalNode Instanceof() {
            return getToken(XPathExpr.Instanceof, 0);
        }

        public SingleExpressionContext singleExpression() {
            return getRuleContext(SingleExpressionContext.class, 0);
        }

        public QualifiedTypeContext qualifiedType() {
            return getRuleContext(QualifiedTypeContext.class, 0);
        }

        public InstanceofExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterInstanceofExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitInstanceofExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitInstanceofExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class UnaryPlusExpressionContext extends SingleExpressionContext {
        public Token op;

        public SingleExpressionContext singleExpression() {
            return getRuleContext(SingleExpressionContext.class, 0);
        }

        public TerminalNode Plus() {
            return getToken(XPathExpr.Plus, 0);
        }

        public UnaryPlusExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterUnaryPlusExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitUnaryPlusExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitUnaryPlusExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class EqualityExpressionContext extends SingleExpressionContext {
        public SingleExpressionContext left;
        public Token bop;
        public SingleExpressionContext right;

        public List<SingleExpressionContext> singleExpression() {
            return getRuleContexts(SingleExpressionContext.class);
        }

        public SingleExpressionContext singleExpression(int i) {
            return getRuleContext(SingleExpressionContext.class, i);
        }

        public TerminalNode Equals_() {
            return getToken(XPathExpr.Equals_, 0);
        }

        public TerminalNode NotEquals() {
            return getToken(XPathExpr.NotEquals, 0);
        }

        public TerminalNode IdentityEquals() {
            return getToken(XPathExpr.IdentityEquals, 0);
        }

        public TerminalNode IdentityNotEquals() {
            return getToken(XPathExpr.IdentityNotEquals, 0);
        }

        public EqualityExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterEqualityExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitEqualityExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitEqualityExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class BitXOrExpressionContext extends SingleExpressionContext {
        public SingleExpressionContext left;
        public SingleExpressionContext right;

        public TerminalNode BitXOr() {
            return getToken(XPathExpr.BitXOr, 0);
        }

        public List<SingleExpressionContext> singleExpression() {
            return getRuleContexts(SingleExpressionContext.class);
        }

        public SingleExpressionContext singleExpression(int i) {
            return getRuleContext(SingleExpressionContext.class, i);
        }

        public BitXOrExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterBitXOrExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitBitXOrExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitBitXOrExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class SuperExpressionContext extends SingleExpressionContext {
        public TerminalNode Super() {
            return getToken(XPathExpr.Super, 0);
        }

        public SuperExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterSuperExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitSuperExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitSuperExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class MultiplicativeExpressionContext extends SingleExpressionContext {
        public SingleExpressionContext left;
        public Token bop;
        public SingleExpressionContext right;

        public List<SingleExpressionContext> singleExpression() {
            return getRuleContexts(SingleExpressionContext.class);
        }

        public SingleExpressionContext singleExpression(int i) {
            return getRuleContext(SingleExpressionContext.class, i);
        }

        public TerminalNode Multiply() {
            return getToken(XPathExpr.Multiply, 0);
        }

        public TerminalNode Divide() {
            return getToken(XPathExpr.Divide, 0);
        }

        public TerminalNode Modulus() {
            return getToken(XPathExpr.Modulus, 0);
        }

        public MultiplicativeExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener)
                ((XPathExprListener) listener).enterMultiplicativeExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener)
                ((XPathExprListener) listener).exitMultiplicativeExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitMultiplicativeExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class CallExpressionContext extends SingleExpressionContext {
        public SingleExpressionContext singleExpression() {
            return getRuleContext(SingleExpressionContext.class, 0);
        }

        public ArgumentsContext arguments() {
            return getRuleContext(ArgumentsContext.class, 0);
        }

        public CallExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterCallExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitCallExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitCallExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class BitShiftExpressionContext extends SingleExpressionContext {
        public SingleExpressionContext left;
        public Token bop;
        public SingleExpressionContext right;

        public List<SingleExpressionContext> singleExpression() {
            return getRuleContexts(SingleExpressionContext.class);
        }

        public SingleExpressionContext singleExpression(int i) {
            return getRuleContext(SingleExpressionContext.class, i);
        }

        public TerminalNode LeftShiftArithmetic() {
            return getToken(XPathExpr.LeftShiftArithmetic, 0);
        }

        public TerminalNode RightShiftArithmetic() {
            return getToken(XPathExpr.RightShiftArithmetic, 0);
        }

        public TerminalNode RightShiftLogical() {
            return getToken(XPathExpr.RightShiftLogical, 0);
        }

        public BitShiftExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterBitShiftExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitBitShiftExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitBitShiftExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class ParenthesizedExpressionContext extends SingleExpressionContext {
        public TerminalNode OpenParen() {
            return getToken(XPathExpr.OpenParen, 0);
        }

        public SingleExpressionContext singleExpression() {
            return getRuleContext(SingleExpressionContext.class, 0);
        }

        public TerminalNode CloseParen() {
            return getToken(XPathExpr.CloseParen, 0);
        }

        public ParenthesizedExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener)
                ((XPathExprListener) listener).enterParenthesizedExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitParenthesizedExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitParenthesizedExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class IfExpressionContext extends SingleExpressionContext {
        public TerminalNode If() {
            return getToken(XPathExpr.If, 0);
        }

        public TerminalNode OpenParen() {
            return getToken(XPathExpr.OpenParen, 0);
        }

        public ArgumentContext argument() {
            return getRuleContext(ArgumentContext.class, 0);
        }

        public TerminalNode Comma() {
            return getToken(XPathExpr.Comma, 0);
        }

        public ArgumentListContext argumentList() {
            return getRuleContext(ArgumentListContext.class, 0);
        }

        public TerminalNode CloseParen() {
            return getToken(XPathExpr.CloseParen, 0);
        }

        public IfExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterIfExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitIfExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitIfExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class AdditiveExpressionContext extends SingleExpressionContext {
        public SingleExpressionContext left;
        public Token bop;
        public SingleExpressionContext right;

        public List<SingleExpressionContext> singleExpression() {
            return getRuleContexts(SingleExpressionContext.class);
        }

        public SingleExpressionContext singleExpression(int i) {
            return getRuleContext(SingleExpressionContext.class, i);
        }

        public TerminalNode Plus() {
            return getToken(XPathExpr.Plus, 0);
        }

        public TerminalNode Minus() {
            return getToken(XPathExpr.Minus, 0);
        }

        public AdditiveExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterAdditiveExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitAdditiveExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitAdditiveExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class RelationalExpressionContext extends SingleExpressionContext {
        public SingleExpressionContext left;
        public Token bop;
        public SingleExpressionContext right;

        public List<SingleExpressionContext> singleExpression() {
            return getRuleContexts(SingleExpressionContext.class);
        }

        public SingleExpressionContext singleExpression(int i) {
            return getRuleContext(SingleExpressionContext.class, i);
        }

        public TerminalNode LessThan() {
            return getToken(XPathExpr.LessThan, 0);
        }

        public TerminalNode MoreThan() {
            return getToken(XPathExpr.MoreThan, 0);
        }

        public TerminalNode LessThanEquals() {
            return getToken(XPathExpr.LessThanEquals, 0);
        }

        public TerminalNode GreaterThanEquals() {
            return getToken(XPathExpr.GreaterThanEquals, 0);
        }

        public RelationalExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterRelationalExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitRelationalExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitRelationalExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class PostIncrementExpressionContext extends SingleExpressionContext {
        public SingleExpressionContext singleExpression() {
            return getRuleContext(SingleExpressionContext.class, 0);
        }

        public TerminalNode PlusPlus() {
            return getToken(XPathExpr.PlusPlus, 0);
        }

        public PostIncrementExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener)
                ((XPathExprListener) listener).enterPostIncrementExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitPostIncrementExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitPostIncrementExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class BitNotExpressionContext extends SingleExpressionContext {
        public Token op;

        public SingleExpressionContext singleExpression() {
            return getRuleContext(SingleExpressionContext.class, 0);
        }

        public TerminalNode BitNot() {
            return getToken(XPathExpr.BitNot, 0);
        }

        public BitNotExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterBitNotExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitBitNotExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitBitNotExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class LiteralExpressionContext extends SingleExpressionContext {
        public LiteralContext literal() {
            return getRuleContext(LiteralContext.class, 0);
        }

        public LiteralExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterLiteralExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitLiteralExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitLiteralExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class ArrayLiteralExpressionContext extends SingleExpressionContext {
        public ArrayLiteralContext arrayLiteral() {
            return getRuleContext(ArrayLiteralContext.class, 0);
        }

        public ArrayLiteralExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterArrayLiteralExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitArrayLiteralExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitArrayLiteralExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class MemberDotExpressionContext extends SingleExpressionContext {
        public Token op;

        public SingleExpressionContext singleExpression() {
            return getRuleContext(SingleExpressionContext.class, 0);
        }

        public IdentifierOrKeywordContext identifierOrKeyword() {
            return getRuleContext(IdentifierOrKeywordContext.class, 0);
        }

        public TerminalNode OptionalDot() {
            return getToken(XPathExpr.OptionalDot, 0);
        }

        public TerminalNode Dot() {
            return getToken(XPathExpr.Dot, 0);
        }

        public MemberDotExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterMemberDotExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitMemberDotExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitMemberDotExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class MemberIndexExpressionContext extends SingleExpressionContext {
        public SingleExpressionContext obj;
        public SingleExpressionContext index;

        public TerminalNode OpenBracket() {
            return getToken(XPathExpr.OpenBracket, 0);
        }

        public TerminalNode CloseBracket() {
            return getToken(XPathExpr.CloseBracket, 0);
        }

        public List<SingleExpressionContext> singleExpression() {
            return getRuleContexts(SingleExpressionContext.class);
        }

        public SingleExpressionContext singleExpression(int i) {
            return getRuleContext(SingleExpressionContext.class, i);
        }

        public MemberIndexExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterMemberIndexExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitMemberIndexExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitMemberIndexExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class IdentifierExpressionContext extends SingleExpressionContext {
        public TerminalNode Identifier() {
            return getToken(XPathExpr.Identifier, 0);
        }

        public IdentifierExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterIdentifierExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitIdentifierExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitIdentifierExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class BitAndExpressionContext extends SingleExpressionContext {
        public SingleExpressionContext left;
        public SingleExpressionContext right;

        public TerminalNode BitAnd() {
            return getToken(XPathExpr.BitAnd, 0);
        }

        public List<SingleExpressionContext> singleExpression() {
            return getRuleContexts(SingleExpressionContext.class);
        }

        public SingleExpressionContext singleExpression(int i) {
            return getRuleContext(SingleExpressionContext.class, i);
        }

        public BitAndExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterBitAndExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitBitAndExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitBitAndExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class BitOrExpressionContext extends SingleExpressionContext {
        public SingleExpressionContext left;
        public SingleExpressionContext right;

        public TerminalNode BitOr() {
            return getToken(XPathExpr.BitOr, 0);
        }

        public List<SingleExpressionContext> singleExpression() {
            return getRuleContexts(SingleExpressionContext.class);
        }

        public SingleExpressionContext singleExpression(int i) {
            return getRuleContext(SingleExpressionContext.class, i);
        }

        public BitOrExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterBitOrExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitBitOrExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitBitOrExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public static class NullCoalesceExpressionContext extends SingleExpressionContext {
        public SingleExpressionContext left;
        public SingleExpressionContext right;

        public TerminalNode NullCoalesce() {
            return getToken(XPathExpr.NullCoalesce, 0);
        }

        public List<SingleExpressionContext> singleExpression() {
            return getRuleContexts(SingleExpressionContext.class);
        }

        public SingleExpressionContext singleExpression(int i) {
            return getRuleContext(SingleExpressionContext.class, i);
        }

        public NullCoalesceExpressionContext(SingleExpressionContext ctx) {
            copyFrom(ctx);
        }

        @Override
        public void enterRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterNullCoalesceExpression(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitNullCoalesceExpression(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitNullCoalesceExpression(this);
            else return visitor.visitChildren(this);
        }
    }

    public final SingleExpressionContext singleExpression() throws RecognitionException {
        return singleExpression(0);
    }

    private SingleExpressionContext singleExpression(int _p) throws RecognitionException {
        ParserRuleContext _parentctx = _ctx;
        int _parentState = getState();
        SingleExpressionContext _localctx = new SingleExpressionContext(_ctx, _parentState);
        SingleExpressionContext _prevctx = _localctx;
        int _startState = 30;
        enterRecursionRule(_localctx, 30, RULE_singleExpression, _p);
        int _la;
        try {
            int _alt;
            enterOuterAlt(_localctx, 1);
            {
                setState(204);
                _errHandler.sync(this);
                switch (getInterpreter().adaptivePredict(_input, 15, _ctx)) {
                    case 1: {
                        _localctx = new TemplateStringExpressionContext(_localctx);
                        _ctx = _localctx;
                        _prevctx = _localctx;

                        setState(163);
                        match(Identifier);
                        setState(164);
                        if (!(this.notLineTerminator()))
                            throw new FailedPredicateException(this, "this.notLineTerminator()");
                        setState(165);
                        match(TemplateStringLiteral);
                    }
                    break;
                    case 2: {
                        _localctx = new ThisExpressionContext(_localctx);
                        _ctx = _localctx;
                        _prevctx = _localctx;
                        setState(166);
                        match(This);
                    }
                    break;
                    case 3: {
                        _localctx = new IdentifierExpressionContext(_localctx);
                        _ctx = _localctx;
                        _prevctx = _localctx;
                        setState(167);
                        match(Identifier);
                    }
                    break;
                    case 4: {
                        _localctx = new SuperExpressionContext(_localctx);
                        _ctx = _localctx;
                        _prevctx = _localctx;
                        setState(168);
                        match(Super);
                    }
                    break;
                    case 5: {
                        _localctx = new LiteralExpressionContext(_localctx);
                        _ctx = _localctx;
                        _prevctx = _localctx;
                        setState(169);
                        literal();
                    }
                    break;
                    case 6: {
                        _localctx = new ArrayLiteralExpressionContext(_localctx);
                        _ctx = _localctx;
                        _prevctx = _localctx;
                        setState(170);
                        arrayLiteral();
                    }
                    break;
                    case 7: {
                        _localctx = new ObjectLiteralExpressionContext(_localctx);
                        _ctx = _localctx;
                        _prevctx = _localctx;
                        setState(171);
                        objectLiteral();
                    }
                    break;
                    case 8: {
                        _localctx = new ParenthesizedExpressionContext(_localctx);
                        _ctx = _localctx;
                        _prevctx = _localctx;
                        setState(172);
                        match(OpenParen);
                        setState(173);
                        singleExpression(0);
                        setState(174);
                        match(CloseParen);
                    }
                    break;
                    case 9: {
                        _localctx = new SwitchExpressionContext(_localctx);
                        _ctx = _localctx;
                        _prevctx = _localctx;
                        setState(176);
                        match(Switch);
                        setState(177);
                        match(OpenParen);
                        setState(178);
                        ((SwitchExpressionContext) _localctx).value = argument();
                        setState(179);
                        match(Comma);
                        setState(180);
                        argumentList();
                        setState(181);
                        match(CloseParen);
                    }
                    break;
                    case 10: {
                        _localctx = new IfExpressionContext(_localctx);
                        _ctx = _localctx;
                        _prevctx = _localctx;
                        setState(183);
                        match(If);
                        setState(184);
                        match(OpenParen);
                        setState(185);
                        argument();
                        setState(186);
                        match(Comma);
                        setState(187);
                        argumentList();
                        setState(188);
                        match(CloseParen);
                    }
                    break;
                    case 11: {
                        _localctx = new TypeofExpressionContext(_localctx);
                        _ctx = _localctx;
                        _prevctx = _localctx;
                        setState(190);
                        match(Typeof);
                        setState(191);
                        singleExpression(21);
                    }
                    break;
                    case 12: {
                        _localctx = new PreIncrementExpressionContext(_localctx);
                        _ctx = _localctx;
                        _prevctx = _localctx;
                        setState(192);
                        ((PreIncrementExpressionContext) _localctx).op = match(PlusPlus);
                        setState(193);
                        singleExpression(20);
                    }
                    break;
                    case 13: {
                        _localctx = new PreDecreaseExpressionContext(_localctx);
                        _ctx = _localctx;
                        _prevctx = _localctx;
                        setState(194);
                        ((PreDecreaseExpressionContext) _localctx).op = match(MinusMinus);
                        setState(195);
                        singleExpression(19);
                    }
                    break;
                    case 14: {
                        _localctx = new UnaryPlusExpressionContext(_localctx);
                        _ctx = _localctx;
                        _prevctx = _localctx;
                        setState(196);
                        ((UnaryPlusExpressionContext) _localctx).op = match(Plus);
                        setState(197);
                        singleExpression(18);
                    }
                    break;
                    case 15: {
                        _localctx = new UnaryMinusExpressionContext(_localctx);
                        _ctx = _localctx;
                        _prevctx = _localctx;
                        setState(198);
                        ((UnaryMinusExpressionContext) _localctx).op = match(Minus);
                        setState(199);
                        singleExpression(17);
                    }
                    break;
                    case 16: {
                        _localctx = new BitNotExpressionContext(_localctx);
                        _ctx = _localctx;
                        _prevctx = _localctx;
                        setState(200);
                        ((BitNotExpressionContext) _localctx).op = match(BitNot);
                        setState(201);
                        singleExpression(16);
                    }
                    break;
                    case 17: {
                        _localctx = new NotExpressionContext(_localctx);
                        _ctx = _localctx;
                        _prevctx = _localctx;
                        setState(202);
                        ((NotExpressionContext) _localctx).op = match(Not);
                        setState(203);
                        singleExpression(15);
                    }
                    break;
                }
                _ctx.stop = _input.LT(-1);
                setState(272);
                _errHandler.sync(this);
                _alt = getInterpreter().adaptivePredict(_input, 17, _ctx);
                while (_alt != 2 && _alt != org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER) {
                    if (_alt == 1) {
                        if (_parseListeners != null) triggerExitRuleEvent();
                        _prevctx = _localctx;
                        {
                            setState(270);
                            _errHandler.sync(this);
                            switch (getInterpreter().adaptivePredict(_input, 16, _ctx)) {
                                case 1: {
                                    _localctx = new MultiplicativeExpressionContext(new SingleExpressionContext(_parentctx, _parentState));
                                    ((MultiplicativeExpressionContext) _localctx).left = _prevctx;
                                    pushNewRecursionContext(_localctx, _startState, RULE_singleExpression);
                                    setState(206);
                                    if (!(precpred(_ctx, 14)))
                                        throw new FailedPredicateException(this, "precpred(_ctx, 14)");
                                    setState(207);
                                    ((MultiplicativeExpressionContext) _localctx).bop = _input.LT(1);
                                    _la = _input.LA(1);
                                    if (!((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << Multiply) | (1L << Divide) | (1L << Modulus))) != 0))) {
                                        ((MultiplicativeExpressionContext) _localctx).bop = (Token) _errHandler.recoverInline(this);
                                    } else {
                                        if (_input.LA(1) == Token.EOF) matchedEOF = true;
                                        _errHandler.reportMatch(this);
                                        consume();
                                    }
                                    setState(208);
                                    ((MultiplicativeExpressionContext) _localctx).right = singleExpression(15);
                                }
                                break;
                                case 2: {
                                    _localctx = new AdditiveExpressionContext(new SingleExpressionContext(_parentctx, _parentState));
                                    ((AdditiveExpressionContext) _localctx).left = _prevctx;
                                    pushNewRecursionContext(_localctx, _startState, RULE_singleExpression);
                                    setState(209);
                                    if (!(precpred(_ctx, 13)))
                                        throw new FailedPredicateException(this, "precpred(_ctx, 13)");
                                    setState(210);
                                    ((AdditiveExpressionContext) _localctx).bop = _input.LT(1);
                                    _la = _input.LA(1);
                                    if (!(_la == Plus || _la == Minus)) {
                                        ((AdditiveExpressionContext) _localctx).bop = (Token) _errHandler.recoverInline(this);
                                    } else {
                                        if (_input.LA(1) == Token.EOF) matchedEOF = true;
                                        _errHandler.reportMatch(this);
                                        consume();
                                    }
                                    setState(211);
                                    ((AdditiveExpressionContext) _localctx).right = singleExpression(14);
                                }
                                break;
                                case 3: {
                                    _localctx = new BitShiftExpressionContext(new SingleExpressionContext(_parentctx, _parentState));
                                    ((BitShiftExpressionContext) _localctx).left = _prevctx;
                                    pushNewRecursionContext(_localctx, _startState, RULE_singleExpression);
                                    setState(212);
                                    if (!(precpred(_ctx, 12)))
                                        throw new FailedPredicateException(this, "precpred(_ctx, 12)");
                                    setState(213);
                                    ((BitShiftExpressionContext) _localctx).bop = _input.LT(1);
                                    _la = _input.LA(1);
                                    if (!((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << RightShiftArithmetic) | (1L << LeftShiftArithmetic) | (1L << RightShiftLogical))) != 0))) {
                                        ((BitShiftExpressionContext) _localctx).bop = (Token) _errHandler.recoverInline(this);
                                    } else {
                                        if (_input.LA(1) == Token.EOF) matchedEOF = true;
                                        _errHandler.reportMatch(this);
                                        consume();
                                    }
                                    setState(214);
                                    ((BitShiftExpressionContext) _localctx).right = singleExpression(13);
                                }
                                break;
                                case 4: {
                                    _localctx = new RelationalExpressionContext(new SingleExpressionContext(_parentctx, _parentState));
                                    ((RelationalExpressionContext) _localctx).left = _prevctx;
                                    pushNewRecursionContext(_localctx, _startState, RULE_singleExpression);
                                    setState(215);
                                    if (!(precpred(_ctx, 11)))
                                        throw new FailedPredicateException(this, "precpred(_ctx, 11)");
                                    setState(216);
                                    ((RelationalExpressionContext) _localctx).bop = _input.LT(1);
                                    _la = _input.LA(1);
                                    if (!((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << LessThan) | (1L << MoreThan) | (1L << LessThanEquals) | (1L << GreaterThanEquals))) != 0))) {
                                        ((RelationalExpressionContext) _localctx).bop = (Token) _errHandler.recoverInline(this);
                                    } else {
                                        if (_input.LA(1) == Token.EOF) matchedEOF = true;
                                        _errHandler.reportMatch(this);
                                        consume();
                                    }
                                    setState(217);
                                    ((RelationalExpressionContext) _localctx).right = singleExpression(12);
                                }
                                break;
                                case 5: {
                                    _localctx = new InExpressionContext(new SingleExpressionContext(_parentctx, _parentState));
                                    ((InExpressionContext) _localctx).left = _prevctx;
                                    pushNewRecursionContext(_localctx, _startState, RULE_singleExpression);
                                    setState(218);
                                    if (!(precpred(_ctx, 9)))
                                        throw new FailedPredicateException(this, "precpred(_ctx, 9)");
                                    setState(219);
                                    match(In);
                                    setState(220);
                                    ((InExpressionContext) _localctx).right = singleExpression(10);
                                }
                                break;
                                case 6: {
                                    _localctx = new EqualityExpressionContext(new SingleExpressionContext(_parentctx, _parentState));
                                    ((EqualityExpressionContext) _localctx).left = _prevctx;
                                    pushNewRecursionContext(_localctx, _startState, RULE_singleExpression);
                                    setState(221);
                                    if (!(precpred(_ctx, 8)))
                                        throw new FailedPredicateException(this, "precpred(_ctx, 8)");
                                    setState(222);
                                    ((EqualityExpressionContext) _localctx).bop = _input.LT(1);
                                    _la = _input.LA(1);
                                    if (!((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << Equals_) | (1L << NotEquals) | (1L << IdentityEquals) | (1L << IdentityNotEquals))) != 0))) {
                                        ((EqualityExpressionContext) _localctx).bop = (Token) _errHandler.recoverInline(this);
                                    } else {
                                        if (_input.LA(1) == Token.EOF) matchedEOF = true;
                                        _errHandler.reportMatch(this);
                                        consume();
                                    }
                                    setState(223);
                                    ((EqualityExpressionContext) _localctx).right = singleExpression(9);
                                }
                                break;
                                case 7: {
                                    _localctx = new BitAndExpressionContext(new SingleExpressionContext(_parentctx, _parentState));
                                    ((BitAndExpressionContext) _localctx).left = _prevctx;
                                    pushNewRecursionContext(_localctx, _startState, RULE_singleExpression);
                                    setState(224);
                                    if (!(precpred(_ctx, 7)))
                                        throw new FailedPredicateException(this, "precpred(_ctx, 7)");
                                    setState(225);
                                    match(BitAnd);
                                    setState(226);
                                    ((BitAndExpressionContext) _localctx).right = singleExpression(8);
                                }
                                break;
                                case 8: {
                                    _localctx = new BitXOrExpressionContext(new SingleExpressionContext(_parentctx, _parentState));
                                    ((BitXOrExpressionContext) _localctx).left = _prevctx;
                                    pushNewRecursionContext(_localctx, _startState, RULE_singleExpression);
                                    setState(227);
                                    if (!(precpred(_ctx, 6)))
                                        throw new FailedPredicateException(this, "precpred(_ctx, 6)");
                                    setState(228);
                                    match(BitXOr);
                                    setState(229);
                                    ((BitXOrExpressionContext) _localctx).right = singleExpression(7);
                                }
                                break;
                                case 9: {
                                    _localctx = new BitOrExpressionContext(new SingleExpressionContext(_parentctx, _parentState));
                                    ((BitOrExpressionContext) _localctx).left = _prevctx;
                                    pushNewRecursionContext(_localctx, _startState, RULE_singleExpression);
                                    setState(230);
                                    if (!(precpred(_ctx, 5)))
                                        throw new FailedPredicateException(this, "precpred(_ctx, 5)");
                                    setState(231);
                                    match(BitOr);
                                    setState(232);
                                    ((BitOrExpressionContext) _localctx).right = singleExpression(6);
                                }
                                break;
                                case 10: {
                                    _localctx = new LogicalAndExpressionContext(new SingleExpressionContext(_parentctx, _parentState));
                                    ((LogicalAndExpressionContext) _localctx).left = _prevctx;
                                    pushNewRecursionContext(_localctx, _startState, RULE_singleExpression);
                                    setState(233);
                                    if (!(precpred(_ctx, 4)))
                                        throw new FailedPredicateException(this, "precpred(_ctx, 4)");
                                    setState(234);
                                    ((LogicalAndExpressionContext) _localctx).bop = _input.LT(1);
                                    _la = _input.LA(1);
                                    if (!(_la == And || _la == AndLiteral)) {
                                        ((LogicalAndExpressionContext) _localctx).bop = (Token) _errHandler.recoverInline(this);
                                    } else {
                                        if (_input.LA(1) == Token.EOF) matchedEOF = true;
                                        _errHandler.reportMatch(this);
                                        consume();
                                    }
                                    setState(235);
                                    ((LogicalAndExpressionContext) _localctx).right = singleExpression(5);
                                }
                                break;
                                case 11: {
                                    _localctx = new LogicalOrExpressionContext(new SingleExpressionContext(_parentctx, _parentState));
                                    ((LogicalOrExpressionContext) _localctx).left = _prevctx;
                                    pushNewRecursionContext(_localctx, _startState, RULE_singleExpression);
                                    setState(236);
                                    if (!(precpred(_ctx, 3)))
                                        throw new FailedPredicateException(this, "precpred(_ctx, 3)");
                                    setState(237);
                                    ((LogicalOrExpressionContext) _localctx).bop = _input.LT(1);
                                    _la = _input.LA(1);
                                    if (!(_la == Or || _la == OrLiteral)) {
                                        ((LogicalOrExpressionContext) _localctx).bop = (Token) _errHandler.recoverInline(this);
                                    } else {
                                        if (_input.LA(1) == Token.EOF) matchedEOF = true;
                                        _errHandler.reportMatch(this);
                                        consume();
                                    }
                                    setState(238);
                                    ((LogicalOrExpressionContext) _localctx).right = singleExpression(4);
                                }
                                break;
                                case 12: {
                                    _localctx = new NullCoalesceExpressionContext(new SingleExpressionContext(_parentctx, _parentState));
                                    ((NullCoalesceExpressionContext) _localctx).left = _prevctx;
                                    pushNewRecursionContext(_localctx, _startState, RULE_singleExpression);
                                    setState(239);
                                    if (!(precpred(_ctx, 2)))
                                        throw new FailedPredicateException(this, "precpred(_ctx, 2)");
                                    setState(240);
                                    match(NullCoalesce);
                                    setState(241);
                                    ((NullCoalesceExpressionContext) _localctx).right = singleExpression(3);
                                }
                                break;
                                case 13: {
                                    _localctx = new TernaryExpressionContext(new SingleExpressionContext(_parentctx, _parentState));
                                    ((TernaryExpressionContext) _localctx).test = _prevctx;
                                    pushNewRecursionContext(_localctx, _startState, RULE_singleExpression);
                                    setState(242);
                                    if (!(precpred(_ctx, 1)))
                                        throw new FailedPredicateException(this, "precpred(_ctx, 1)");
                                    setState(243);
                                    match(Question);
                                    setState(244);
                                    ((TernaryExpressionContext) _localctx).consequence = singleExpression(0);
                                    setState(245);
                                    match(Colon);
                                    setState(246);
                                    ((TernaryExpressionContext) _localctx).alternate = singleExpression(1);
                                }
                                break;
                                case 14: {
                                    _localctx = new MemberIndexExpressionContext(new SingleExpressionContext(_parentctx, _parentState));
                                    ((MemberIndexExpressionContext) _localctx).obj = _prevctx;
                                    pushNewRecursionContext(_localctx, _startState, RULE_singleExpression);
                                    setState(248);
                                    if (!(precpred(_ctx, 29)))
                                        throw new FailedPredicateException(this, "precpred(_ctx, 29)");
                                    setState(249);
                                    match(OpenBracket);
                                    setState(250);
                                    ((MemberIndexExpressionContext) _localctx).index = singleExpression(0);
                                    setState(251);
                                    match(CloseBracket);
                                }
                                break;
                                case 15: {
                                    _localctx = new MemberDotExpressionContext(new SingleExpressionContext(_parentctx, _parentState));
                                    pushNewRecursionContext(_localctx, _startState, RULE_singleExpression);
                                    setState(253);
                                    if (!(precpred(_ctx, 28)))
                                        throw new FailedPredicateException(this, "precpred(_ctx, 28)");
                                    setState(254);
                                    ((MemberDotExpressionContext) _localctx).op = _input.LT(1);
                                    _la = _input.LA(1);
                                    if (!(_la == OptionalDot || _la == Dot)) {
                                        ((MemberDotExpressionContext) _localctx).op = (Token) _errHandler.recoverInline(this);
                                    } else {
                                        if (_input.LA(1) == Token.EOF) matchedEOF = true;
                                        _errHandler.reportMatch(this);
                                        consume();
                                    }
                                    setState(255);
                                    identifierOrKeyword();
                                }
                                break;
                                case 16: {
                                    _localctx = new CallExpressionContext(new SingleExpressionContext(_parentctx, _parentState));
                                    pushNewRecursionContext(_localctx, _startState, RULE_singleExpression);
                                    setState(256);
                                    if (!(precpred(_ctx, 27)))
                                        throw new FailedPredicateException(this, "precpred(_ctx, 27)");
                                    setState(257);
                                    arguments();
                                }
                                break;
                                case 17: {
                                    _localctx = new PostIncrementExpressionContext(new SingleExpressionContext(_parentctx, _parentState));
                                    pushNewRecursionContext(_localctx, _startState, RULE_singleExpression);
                                    setState(258);
                                    if (!(precpred(_ctx, 24)))
                                        throw new FailedPredicateException(this, "precpred(_ctx, 24)");
                                    setState(259);
                                    if (!(this.notLineTerminator()))
                                        throw new FailedPredicateException(this, "this.notLineTerminator()");
                                    setState(260);
                                    match(PlusPlus);
                                }
                                break;
                                case 18: {
                                    _localctx = new PostDecreaseExpressionContext(new SingleExpressionContext(_parentctx, _parentState));
                                    pushNewRecursionContext(_localctx, _startState, RULE_singleExpression);
                                    setState(261);
                                    if (!(precpred(_ctx, 23)))
                                        throw new FailedPredicateException(this, "precpred(_ctx, 23)");
                                    setState(262);
                                    if (!(this.notLineTerminator()))
                                        throw new FailedPredicateException(this, "this.notLineTerminator()");
                                    setState(263);
                                    match(MinusMinus);
                                }
                                break;
                                case 19: {
                                    _localctx = new ChainExpressionContext(new SingleExpressionContext(_parentctx, _parentState));
                                    pushNewRecursionContext(_localctx, _startState, RULE_singleExpression);
                                    setState(264);
                                    if (!(precpred(_ctx, 22)))
                                        throw new FailedPredicateException(this, "precpred(_ctx, 22)");
                                    setState(265);
                                    if (!(this.notLineTerminator()))
                                        throw new FailedPredicateException(this, "this.notLineTerminator()");
                                    setState(266);
                                    match(Not);
                                }
                                break;
                                case 20: {
                                    _localctx = new InstanceofExpressionContext(new SingleExpressionContext(_parentctx, _parentState));
                                    ((InstanceofExpressionContext) _localctx).left = _prevctx;
                                    pushNewRecursionContext(_localctx, _startState, RULE_singleExpression);
                                    setState(267);
                                    if (!(precpred(_ctx, 10)))
                                        throw new FailedPredicateException(this, "precpred(_ctx, 10)");
                                    setState(268);
                                    match(Instanceof);
                                    setState(269);
                                    ((InstanceofExpressionContext) _localctx).right = qualifiedType();
                                }
                                break;
                            }
                        }
                    }
                    setState(274);
                    _errHandler.sync(this);
                    _alt = getInterpreter().adaptivePredict(_input, 17, _ctx);
                }
            }
        } catch (RecognitionException re) {
            _localctx.exception = re;
            _errHandler.reportError(this, re);
            _errHandler.recover(this, re);
        } finally {
            unrollRecursionContexts(_parentctx);
        }
        return _localctx;
    }

    public static class QualifiedTypeContext extends ParserRuleContext {
        public TerminalNode Identifier() {
            return getToken(XPathExpr.Identifier, 0);
        }

        public List<TerminalNode> Dot() {
            return getTokens(XPathExpr.Dot);
        }

        public TerminalNode Dot(int i) {
            return getToken(XPathExpr.Dot, i);
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
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterQualifiedType(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitQualifiedType(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitQualifiedType(this);
            else return visitor.visitChildren(this);
        }
    }

    public final QualifiedTypeContext qualifiedType() throws RecognitionException {
        QualifiedTypeContext _localctx = new QualifiedTypeContext(_ctx, getState());
        enterRule(_localctx, 32, RULE_qualifiedType);
        try {
            int _alt;
            enterOuterAlt(_localctx, 1);
            {
                setState(275);
                match(Identifier);
                setState(280);
                _errHandler.sync(this);
                _alt = getInterpreter().adaptivePredict(_input, 18, _ctx);
                while (_alt != 2 && _alt != org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER) {
                    if (_alt == 1) {
                        {
                            {
                                setState(276);
                                match(Dot);
                                setState(277);
                                identifier();
                            }
                        }
                    }
                    setState(282);
                    _errHandler.sync(this);
                    _alt = getInterpreter().adaptivePredict(_input, 18, _ctx);
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
            return getToken(XPathExpr.Identifier, 0);
        }

        public List<TerminalNode> Dot() {
            return getTokens(XPathExpr.Dot);
        }

        public TerminalNode Dot(int i) {
            return getToken(XPathExpr.Dot, i);
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
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterQualifiedName(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitQualifiedName(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitQualifiedName(this);
            else return visitor.visitChildren(this);
        }
    }

    public final QualifiedNameContext qualifiedName() throws RecognitionException {
        QualifiedNameContext _localctx = new QualifiedNameContext(_ctx, getState());
        enterRule(_localctx, 34, RULE_qualifiedName);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(283);
                match(Identifier);
                setState(288);
                _errHandler.sync(this);
                _la = _input.LA(1);
                while (_la == Dot) {
                    {
                        {
                            setState(284);
                            match(Dot);
                            setState(285);
                            identifier();
                        }
                    }
                    setState(290);
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
            return getToken(XPathExpr.StringLiteral, 0);
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
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterPropertyName(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitPropertyName(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitPropertyName(this);
            else return visitor.visitChildren(this);
        }
    }

    public final PropertyNameContext propertyName() throws RecognitionException {
        PropertyNameContext _localctx = new PropertyNameContext(_ctx, getState());
        enterRule(_localctx, 36, RULE_propertyName);
        try {
            setState(293);
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
                    setState(291);
                    identifierOrKeyword();
                }
                break;
                case StringLiteral:
                    enterOuterAlt(_localctx, 2);
                {
                    setState(292);
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
            return getToken(XPathExpr.From, 0);
        }

        public TerminalNode TypeAlias() {
            return getToken(XPathExpr.TypeAlias, 0);
        }

        public TerminalNode Identifier() {
            return getToken(XPathExpr.Identifier, 0);
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
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterIdentifier(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitIdentifier(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitIdentifier(this);
            else return visitor.visitChildren(this);
        }
    }

    public final IdentifierContext identifier() throws RecognitionException {
        IdentifierContext _localctx = new IdentifierContext(_ctx, getState());
        enterRule(_localctx, 38, RULE_identifier);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(295);
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
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterIdentifierOrKeyword(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitIdentifierOrKeyword(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitIdentifierOrKeyword(this);
            else return visitor.visitChildren(this);
        }
    }

    public final IdentifierOrKeywordContext identifierOrKeyword() throws RecognitionException {
        IdentifierOrKeywordContext _localctx = new IdentifierOrKeywordContext(_ctx, getState());
        enterRule(_localctx, 40, RULE_identifierOrKeyword);
        try {
            setState(299);
            _errHandler.sync(this);
            switch (getInterpreter().adaptivePredict(_input, 21, _ctx)) {
                case 1:
                    enterOuterAlt(_localctx, 1);
                {
                    setState(297);
                    identifier();
                }
                break;
                case 2:
                    enterOuterAlt(_localctx, 2);
                {
                    setState(298);
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
            return getToken(XPathExpr.NullLiteral, 0);
        }

        public TerminalNode BooleanLiteral() {
            return getToken(XPathExpr.BooleanLiteral, 0);
        }

        public TerminalNode AndLiteral() {
            return getToken(XPathExpr.AndLiteral, 0);
        }

        public TerminalNode OrLiteral() {
            return getToken(XPathExpr.OrLiteral, 0);
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
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterReservedWord(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitReservedWord(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitReservedWord(this);
            else return visitor.visitChildren(this);
        }
    }

    public final ReservedWordContext reservedWord() throws RecognitionException {
        ReservedWordContext _localctx = new ReservedWordContext(_ctx, getState());
        enterRule(_localctx, 42, RULE_reservedWord);
        try {
            setState(306);
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
                    setState(301);
                    keyword();
                }
                break;
                case NullLiteral:
                    enterOuterAlt(_localctx, 2);
                {
                    setState(302);
                    match(NullLiteral);
                }
                break;
                case BooleanLiteral:
                    enterOuterAlt(_localctx, 3);
                {
                    setState(303);
                    match(BooleanLiteral);
                }
                break;
                case AndLiteral:
                    enterOuterAlt(_localctx, 4);
                {
                    setState(304);
                    match(AndLiteral);
                }
                break;
                case OrLiteral:
                    enterOuterAlt(_localctx, 5);
                {
                    setState(305);
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
            return getToken(XPathExpr.Break, 0);
        }

        public TerminalNode As() {
            return getToken(XPathExpr.As, 0);
        }

        public TerminalNode Do() {
            return getToken(XPathExpr.Do, 0);
        }

        public TerminalNode Instanceof() {
            return getToken(XPathExpr.Instanceof, 0);
        }

        public TerminalNode Typeof() {
            return getToken(XPathExpr.Typeof, 0);
        }

        public TerminalNode Case() {
            return getToken(XPathExpr.Case, 0);
        }

        public TerminalNode Else() {
            return getToken(XPathExpr.Else, 0);
        }

        public TerminalNode New() {
            return getToken(XPathExpr.New, 0);
        }

        public TerminalNode Var() {
            return getToken(XPathExpr.Var, 0);
        }

        public TerminalNode Catch() {
            return getToken(XPathExpr.Catch, 0);
        }

        public TerminalNode Finally() {
            return getToken(XPathExpr.Finally, 0);
        }

        public TerminalNode Return() {
            return getToken(XPathExpr.Return, 0);
        }

        public TerminalNode Void() {
            return getToken(XPathExpr.Void, 0);
        }

        public TerminalNode Continue() {
            return getToken(XPathExpr.Continue, 0);
        }

        public TerminalNode For() {
            return getToken(XPathExpr.For, 0);
        }

        public TerminalNode Switch() {
            return getToken(XPathExpr.Switch, 0);
        }

        public TerminalNode While() {
            return getToken(XPathExpr.While, 0);
        }

        public TerminalNode Debugger() {
            return getToken(XPathExpr.Debugger, 0);
        }

        public TerminalNode Function() {
            return getToken(XPathExpr.Function, 0);
        }

        public TerminalNode This() {
            return getToken(XPathExpr.This, 0);
        }

        public TerminalNode With() {
            return getToken(XPathExpr.With, 0);
        }

        public TerminalNode Default() {
            return getToken(XPathExpr.Default, 0);
        }

        public TerminalNode If() {
            return getToken(XPathExpr.If, 0);
        }

        public TerminalNode Throw() {
            return getToken(XPathExpr.Throw, 0);
        }

        public TerminalNode Delete() {
            return getToken(XPathExpr.Delete, 0);
        }

        public TerminalNode In() {
            return getToken(XPathExpr.In, 0);
        }

        public TerminalNode Try() {
            return getToken(XPathExpr.Try, 0);
        }

        public TerminalNode ReadOnly() {
            return getToken(XPathExpr.ReadOnly, 0);
        }

        public TerminalNode Class() {
            return getToken(XPathExpr.Class, 0);
        }

        public TerminalNode Enum() {
            return getToken(XPathExpr.Enum, 0);
        }

        public TerminalNode Extends() {
            return getToken(XPathExpr.Extends, 0);
        }

        public TerminalNode Super() {
            return getToken(XPathExpr.Super, 0);
        }

        public TerminalNode Const() {
            return getToken(XPathExpr.Const, 0);
        }

        public TerminalNode Export() {
            return getToken(XPathExpr.Export, 0);
        }

        public TerminalNode Import() {
            return getToken(XPathExpr.Import, 0);
        }

        public TerminalNode Implements() {
            return getToken(XPathExpr.Implements, 0);
        }

        public TerminalNode Let() {
            return getToken(XPathExpr.Let, 0);
        }

        public TerminalNode Private() {
            return getToken(XPathExpr.Private, 0);
        }

        public TerminalNode Public() {
            return getToken(XPathExpr.Public, 0);
        }

        public TerminalNode Interface() {
            return getToken(XPathExpr.Interface, 0);
        }

        public TerminalNode Package() {
            return getToken(XPathExpr.Package, 0);
        }

        public TerminalNode Protected() {
            return getToken(XPathExpr.Protected, 0);
        }

        public TerminalNode Static() {
            return getToken(XPathExpr.Static, 0);
        }

        public TerminalNode TypeAlias() {
            return getToken(XPathExpr.TypeAlias, 0);
        }

        public TerminalNode String() {
            return getToken(XPathExpr.String, 0);
        }

        public TerminalNode Boolean() {
            return getToken(XPathExpr.Boolean, 0);
        }

        public TerminalNode Number() {
            return getToken(XPathExpr.Number, 0);
        }

        public TerminalNode Any() {
            return getToken(XPathExpr.Any, 0);
        }

        public TerminalNode Symbol() {
            return getToken(XPathExpr.Symbol, 0);
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
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterKeyword(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitKeyword(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitKeyword(this);
            else return visitor.visitChildren(this);
        }
    }

    public final KeywordContext keyword() throws RecognitionException {
        KeywordContext _localctx = new KeywordContext(_ctx, getState());
        enterRule(_localctx, 44, RULE_keyword);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(308);
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
            return getToken(XPathExpr.NullLiteral, 0);
        }

        public TerminalNode BooleanLiteral() {
            return getToken(XPathExpr.BooleanLiteral, 0);
        }

        public TerminalNode StringLiteral() {
            return getToken(XPathExpr.StringLiteral, 0);
        }

        public TerminalNode TemplateStringLiteral() {
            return getToken(XPathExpr.TemplateStringLiteral, 0);
        }

        public TerminalNode RegularExpressionLiteral() {
            return getToken(XPathExpr.RegularExpressionLiteral, 0);
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
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterLiteral(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitLiteral(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitLiteral(this);
            else return visitor.visitChildren(this);
        }
    }

    public final LiteralContext literal() throws RecognitionException {
        LiteralContext _localctx = new LiteralContext(_ctx, getState());
        enterRule(_localctx, 46, RULE_literal);
        try {
            setState(316);
            _errHandler.sync(this);
            switch (_input.LA(1)) {
                case NullLiteral:
                    enterOuterAlt(_localctx, 1);
                {
                    setState(310);
                    match(NullLiteral);
                }
                break;
                case BooleanLiteral:
                    enterOuterAlt(_localctx, 2);
                {
                    setState(311);
                    match(BooleanLiteral);
                }
                break;
                case StringLiteral:
                    enterOuterAlt(_localctx, 3);
                {
                    setState(312);
                    match(StringLiteral);
                }
                break;
                case TemplateStringLiteral:
                    enterOuterAlt(_localctx, 4);
                {
                    setState(313);
                    match(TemplateStringLiteral);
                }
                break;
                case RegularExpressionLiteral:
                    enterOuterAlt(_localctx, 5);
                {
                    setState(314);
                    match(RegularExpressionLiteral);
                }
                break;
                case DecimalIntegerLiteral:
                case HexIntegerLiteral:
                case BinaryIntegerLiteral:
                case DecimalLiteral:
                    enterOuterAlt(_localctx, 6);
                {
                    setState(315);
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
            return getToken(XPathExpr.DecimalIntegerLiteral, 0);
        }

        public TerminalNode HexIntegerLiteral() {
            return getToken(XPathExpr.HexIntegerLiteral, 0);
        }

        public TerminalNode DecimalLiteral() {
            return getToken(XPathExpr.DecimalLiteral, 0);
        }

        public TerminalNode BinaryIntegerLiteral() {
            return getToken(XPathExpr.BinaryIntegerLiteral, 0);
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
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).enterNumericLiteral(this);
        }

        @Override
        public void exitRule(ParseTreeListener listener) {
            if (listener instanceof XPathExprListener) ((XPathExprListener) listener).exitNumericLiteral(this);
        }

        @Override
        public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
            if (visitor instanceof XPathExprVisitor)
                return ((XPathExprVisitor<? extends T>) visitor).visitNumericLiteral(this);
            else return visitor.visitChildren(this);
        }
    }

    public final NumericLiteralContext numericLiteral() throws RecognitionException {
        NumericLiteralContext _localctx = new NumericLiteralContext(_ctx, getState());
        enterRule(_localctx, 48, RULE_numericLiteral);
        int _la;
        try {
            enterOuterAlt(_localctx, 1);
            {
                setState(318);
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

    public boolean sempred(RuleContext _localctx, int ruleIndex, int predIndex) {
        switch (ruleIndex) {
            case 15:
                return singleExpression_sempred((SingleExpressionContext) _localctx, predIndex);
        }
        return true;
    }

    private boolean singleExpression_sempred(SingleExpressionContext _localctx, int predIndex) {
        switch (predIndex) {
            case 0:
                return this.notLineTerminator();
            case 1:
                return precpred(_ctx, 14);
            case 2:
                return precpred(_ctx, 13);
            case 3:
                return precpred(_ctx, 12);
            case 4:
                return precpred(_ctx, 11);
            case 5:
                return precpred(_ctx, 9);
            case 6:
                return precpred(_ctx, 8);
            case 7:
                return precpred(_ctx, 7);
            case 8:
                return precpred(_ctx, 6);
            case 9:
                return precpred(_ctx, 5);
            case 10:
                return precpred(_ctx, 4);
            case 11:
                return precpred(_ctx, 3);
            case 12:
                return precpred(_ctx, 2);
            case 13:
                return precpred(_ctx, 1);
            case 14:
                return precpred(_ctx, 29);
            case 15:
                return precpred(_ctx, 28);
            case 16:
                return precpred(_ctx, 27);
            case 17:
                return precpred(_ctx, 24);
            case 18:
                return this.notLineTerminator();
            case 19:
                return precpred(_ctx, 23);
            case 20:
                return this.notLineTerminator();
            case 21:
                return precpred(_ctx, 22);
            case 22:
                return this.notLineTerminator();
            case 23:
                return precpred(_ctx, 10);
        }
        return true;
    }

    public static final String _serializedATN =
            "\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\3}\u0143\4\2\t\2\4" +
                    "\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13\t" +
                    "\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22" +
                    "\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31\t\31" +
                    "\4\32\t\32\3\2\3\2\3\2\7\28\n\2\f\2\16\2;\13\2\3\3\3\3\3\4\3\4\3\5\3\5" +
                    "\3\5\7\5D\n\5\f\5\16\5G\13\5\3\6\3\6\5\6K\n\6\3\6\3\6\3\7\3\7\3\7\7\7" +
                    "R\n\7\f\7\16\7U\13\7\3\7\5\7X\n\7\3\b\5\b[\n\b\3\b\3\b\3\t\3\t\3\t\3\t" +
                    "\7\tc\n\t\f\t\16\tf\13\t\5\th\n\t\3\t\5\tk\n\t\3\t\3\t\3\n\3\n\3\n\3\n" +
                    "\3\n\3\n\3\n\3\n\3\n\3\n\3\n\3\n\3\n\5\n|\n\n\3\13\3\13\5\13\u0080\n\13" +
                    "\3\13\3\13\3\f\3\f\3\f\7\f\u0087\n\f\f\f\16\f\u008a\13\f\3\f\5\f\u008d" +
                    "\n\f\3\r\3\r\3\16\3\16\3\16\7\16\u0094\n\16\f\16\16\16\u0097\13\16\3\17" +
                    "\3\17\3\17\3\17\3\20\3\20\3\20\7\20\u00a0\n\20\f\20\16\20\u00a3\13\20" +
                    "\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21" +
                    "\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21" +
                    "\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21" +
                    "\5\21\u00cf\n\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21" +
                    "\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21" +
                    "\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21" +
                    "\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21" +
                    "\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\7\21\u0111\n\21" +
                    "\f\21\16\21\u0114\13\21\3\22\3\22\3\22\7\22\u0119\n\22\f\22\16\22\u011c" +
                    "\13\22\3\23\3\23\3\23\7\23\u0121\n\23\f\23\16\23\u0124\13\23\3\24\3\24" +
                    "\5\24\u0128\n\24\3\25\3\25\3\26\3\26\5\26\u012e\n\26\3\27\3\27\3\27\3" +
                    "\27\3\27\5\27\u0135\n\27\3\30\3\30\3\31\3\31\3\31\3\31\3\31\3\31\5\31" +
                    "\u013f\n\31\3\32\3\32\3\32\2\3 \33\2\4\6\b\n\f\16\20\22\24\26\30\32\34" +
                    "\36 \"$&(*,.\60\62\2\r\3\2\30\32\3\2\24\25\3\2\33\35\3\2\36!\3\2\"%\4" +
                    "\2))99\4\2**::\4\2\16\16\21\21\5\2ZZrrvv\5\2?Y[[^r\3\2;>\2\u016b\2\64" +
                    "\3\2\2\2\4<\3\2\2\2\6>\3\2\2\2\b@\3\2\2\2\nH\3\2\2\2\fN\3\2\2\2\16Z\3" +
                    "\2\2\2\20^\3\2\2\2\22{\3\2\2\2\24}\3\2\2\2\26\u0083\3\2\2\2\30\u008e\3" +
                    "\2\2\2\32\u0090\3\2\2\2\34\u0098\3\2\2\2\36\u009c\3\2\2\2 \u00ce\3\2\2" +
                    "\2\"\u0115\3\2\2\2$\u011d\3\2\2\2&\u0127\3\2\2\2(\u0129\3\2\2\2*\u012d" +
                    "\3\2\2\2,\u0134\3\2\2\2.\u0136\3\2\2\2\60\u013e\3\2\2\2\62\u0140\3\2\2" +
                    "\2\649\5\4\3\2\65\66\7\n\2\2\668\5\4\3\2\67\65\3\2\2\28;\3\2\2\29\67\3" +
                    "\2\2\29:\3\2\2\2:\3\3\2\2\2;9\3\2\2\2<=\5\6\4\2=\5\3\2\2\2>?\5(\25\2?" +
                    "\7\3\2\2\2@E\7v\2\2AB\7\21\2\2BD\5(\25\2CA\3\2\2\2DG\3\2\2\2EC\3\2\2\2" +
                    "EF\3\2\2\2F\t\3\2\2\2GE\3\2\2\2HJ\7\3\2\2IK\5\f\7\2JI\3\2\2\2JK\3\2\2" +
                    "\2KL\3\2\2\2LM\7\4\2\2M\13\3\2\2\2NS\5\16\b\2OP\7\n\2\2PR\5\16\b\2QO\3" +
                    "\2\2\2RU\3\2\2\2SQ\3\2\2\2ST\3\2\2\2TW\3\2\2\2US\3\2\2\2VX\7\n\2\2WV\3" +
                    "\2\2\2WX\3\2\2\2X\r\3\2\2\2Y[\7\20\2\2ZY\3\2\2\2Z[\3\2\2\2[\\\3\2\2\2" +
                    "\\]\5 \21\2]\17\3\2\2\2^g\7\7\2\2_d\5\22\n\2`a\7\n\2\2ac\5\22\n\2b`\3" +
                    "\2\2\2cf\3\2\2\2db\3\2\2\2de\3\2\2\2eh\3\2\2\2fd\3\2\2\2g_\3\2\2\2gh\3" +
                    "\2\2\2hj\3\2\2\2ik\7\n\2\2ji\3\2\2\2jk\3\2\2\2kl\3\2\2\2lm\7\b\2\2m\21" +
                    "\3\2\2\2no\5&\24\2op\7\17\2\2pq\5 \21\2q|\3\2\2\2rs\7\3\2\2st\5 \21\2" +
                    "tu\7\4\2\2uv\7\17\2\2vw\5 \21\2w|\3\2\2\2x|\5*\26\2yz\7\20\2\2z|\5 \21" +
                    "\2{n\3\2\2\2{r\3\2\2\2{x\3\2\2\2{y\3\2\2\2|\23\3\2\2\2}\177\7\5\2\2~\u0080" +
                    "\5\26\f\2\177~\3\2\2\2\177\u0080\3\2\2\2\u0080\u0081\3\2\2\2\u0081\u0082" +
                    "\7\6\2\2\u0082\25\3\2\2\2\u0083\u0088\5\30\r\2\u0084\u0085\7\n\2\2\u0085" +
                    "\u0087\5\30\r\2\u0086\u0084\3\2\2\2\u0087\u008a\3\2\2\2\u0088\u0086\3" +
                    "\2\2\2\u0088\u0089\3\2\2\2\u0089\u008c\3\2\2\2\u008a\u0088\3\2\2\2\u008b" +
                    "\u008d\7\n\2\2\u008c\u008b\3\2\2\2\u008c\u008d\3\2\2\2\u008d\27\3\2\2" +
                    "\2\u008e\u008f\5 \21\2\u008f\31\3\2\2\2\u0090\u0095\5 \21\2\u0091\u0092" +
                    "\7\n\2\2\u0092\u0094\5 \21\2\u0093\u0091\3\2\2\2\u0094\u0097\3\2\2\2\u0095" +
                    "\u0093\3\2\2\2\u0095\u0096\3\2\2\2\u0096\33\3\2\2\2\u0097\u0095\3\2\2" +
                    "\2\u0098\u0099\7v\2\2\u0099\u009a\7\13\2\2\u009a\u009b\5 \21\2\u009b\35" +
                    "\3\2\2\2\u009c\u00a1\5\34\17\2\u009d\u009e\7\n\2\2\u009e\u00a0\5\34\17" +
                    "\2\u009f\u009d\3\2\2\2\u00a0\u00a3\3\2\2\2\u00a1\u009f\3\2\2\2\u00a1\u00a2" +
                    "\3\2\2\2\u00a2\37\3\2\2\2\u00a3\u00a1\3\2\2\2\u00a4\u00a5\b\21\1\2\u00a5" +
                    "\u00a6\7v\2\2\u00a6\u00a7\6\21\2\2\u00a7\u00cf\7x\2\2\u00a8\u00cf\7Q\2" +
                    "\2\u00a9\u00cf\7v\2\2\u00aa\u00cf\7a\2\2\u00ab\u00cf\5\60\31\2\u00ac\u00cf" +
                    "\5\n\6\2\u00ad\u00cf\5\20\t\2\u00ae\u00af\7\5\2\2\u00af\u00b0\5 \21\2" +
                    "\u00b0\u00b1\7\6\2\2\u00b1\u00cf\3\2\2\2\u00b2\u00b3\7M\2\2\u00b3\u00b4" +
                    "\7\5\2\2\u00b4\u00b5\5\30\r\2\u00b5\u00b6\7\n\2\2\u00b6\u00b7\5\26\f\2" +
                    "\u00b7\u00b8\7\6\2\2\u00b8\u00cf\3\2\2\2\u00b9\u00ba\7T\2\2\u00ba\u00bb" +
                    "\7\5\2\2\u00bb\u00bc\5\30\r\2\u00bc\u00bd\7\n\2\2\u00bd\u00be\5\26\f\2" +
                    "\u00be\u00bf\7\6\2\2\u00bf\u00cf\3\2\2\2\u00c0\u00c1\7B\2\2\u00c1\u00cf" +
                    "\5 \21\27\u00c2\u00c3\7\22\2\2\u00c3\u00cf\5 \21\26\u00c4\u00c5\7\23\2" +
                    "\2\u00c5\u00cf\5 \21\25\u00c6\u00c7\7\24\2\2\u00c7\u00cf\5 \21\24\u00c8" +
                    "\u00c9\7\25\2\2\u00c9\u00cf\5 \21\23\u00ca\u00cb\7\26\2\2\u00cb\u00cf" +
                    "\5 \21\22\u00cc\u00cd\7\27\2\2\u00cd\u00cf\5 \21\21\u00ce\u00a4\3\2\2" +
                    "\2\u00ce\u00a8\3\2\2\2\u00ce\u00a9\3\2\2\2\u00ce\u00aa\3\2\2\2\u00ce\u00ab" +
                    "\3\2\2\2\u00ce\u00ac\3\2\2\2\u00ce\u00ad\3\2\2\2\u00ce\u00ae\3\2\2\2\u00ce" +
                    "\u00b2\3\2\2\2\u00ce\u00b9\3\2\2\2\u00ce\u00c0\3\2\2\2\u00ce\u00c2\3\2" +
                    "\2\2\u00ce\u00c4\3\2\2\2\u00ce\u00c6\3\2\2\2\u00ce\u00c8\3\2\2\2\u00ce" +
                    "\u00ca\3\2\2\2\u00ce\u00cc\3\2\2\2\u00cf\u0112\3\2\2\2\u00d0\u00d1\f\20" +
                    "\2\2\u00d1\u00d2\t\2\2\2\u00d2\u0111\5 \21\21\u00d3\u00d4\f\17\2\2\u00d4" +
                    "\u00d5\t\3\2\2\u00d5\u0111\5 \21\20\u00d6\u00d7\f\16\2\2\u00d7\u00d8\t" +
                    "\4\2\2\u00d8\u0111\5 \21\17\u00d9\u00da\f\r\2\2\u00da\u00db\t\5\2\2\u00db" +
                    "\u0111\5 \21\16\u00dc\u00dd\f\13\2\2\u00dd\u00de\7W\2\2\u00de\u0111\5" +
                    " \21\f\u00df\u00e0\f\n\2\2\u00e0\u00e1\t\6\2\2\u00e1\u0111\5 \21\13\u00e2" +
                    "\u00e3\f\t\2\2\u00e3\u00e4\7&\2\2\u00e4\u0111\5 \21\n\u00e5\u00e6\f\b" +
                    "\2\2\u00e6\u00e7\7\'\2\2\u00e7\u0111\5 \21\t\u00e8\u00e9\f\7\2\2\u00e9" +
                    "\u00ea\7(\2\2\u00ea\u0111\5 \21\b\u00eb\u00ec\f\6\2\2\u00ec\u00ed\t\7" +
                    "\2\2\u00ed\u0111\5 \21\7\u00ee\u00ef\f\5\2\2\u00ef\u00f0\t\b\2\2\u00f0" +
                    "\u0111\5 \21\6\u00f1\u00f2\f\4\2\2\u00f2\u00f3\7\f\2\2\u00f3\u0111\5 " +
                    "\21\5\u00f4\u00f5\f\3\2\2\u00f5\u00f6\7\r\2\2\u00f6\u00f7\5 \21\2\u00f7" +
                    "\u00f8\7\17\2\2\u00f8\u00f9\5 \21\3\u00f9\u0111\3\2\2\2\u00fa\u00fb\f" +
                    "\37\2\2\u00fb\u00fc\7\3\2\2\u00fc\u00fd\5 \21\2\u00fd\u00fe\7\4\2\2\u00fe" +
                    "\u0111\3\2\2\2\u00ff\u0100\f\36\2\2\u0100\u0101\t\t\2\2\u0101\u0111\5" +
                    "*\26\2\u0102\u0103\f\35\2\2\u0103\u0111\5\24\13\2\u0104\u0105\f\32\2\2" +
                    "\u0105\u0106\6\21\24\2\u0106\u0111\7\22\2\2\u0107\u0108\f\31\2\2\u0108" +
                    "\u0109\6\21\26\2\u0109\u0111\7\23\2\2\u010a\u010b\f\30\2\2\u010b\u010c" +
                    "\6\21\30\2\u010c\u0111\7\27\2\2\u010d\u010e\f\f\2\2\u010e\u010f\7A\2\2" +
                    "\u010f\u0111\5\"\22\2\u0110\u00d0\3\2\2\2\u0110\u00d3\3\2\2\2\u0110\u00d6" +
                    "\3\2\2\2\u0110\u00d9\3\2\2\2\u0110\u00dc\3\2\2\2\u0110\u00df\3\2\2\2\u0110" +
                    "\u00e2\3\2\2\2\u0110\u00e5\3\2\2\2\u0110\u00e8\3\2\2\2\u0110\u00eb\3\2" +
                    "\2\2\u0110\u00ee\3\2\2\2\u0110\u00f1\3\2\2\2\u0110\u00f4\3\2\2\2\u0110" +
                    "\u00fa\3\2\2\2\u0110\u00ff\3\2\2\2\u0110\u0102\3\2\2\2\u0110\u0104\3\2" +
                    "\2\2\u0110\u0107\3\2\2\2\u0110\u010a\3\2\2\2\u0110\u010d\3\2\2\2\u0111" +
                    "\u0114\3\2\2\2\u0112\u0110\3\2\2\2\u0112\u0113\3\2\2\2\u0113!\3\2\2\2" +
                    "\u0114\u0112\3\2\2\2\u0115\u011a\7v\2\2\u0116\u0117\7\21\2\2\u0117\u0119" +
                    "\5(\25\2\u0118\u0116\3\2\2\2\u0119\u011c\3\2\2\2\u011a\u0118\3\2\2\2\u011a" +
                    "\u011b\3\2\2\2\u011b#\3\2\2\2\u011c\u011a\3\2\2\2\u011d\u0122\7v\2\2\u011e" +
                    "\u011f\7\21\2\2\u011f\u0121\5(\25\2\u0120\u011e\3\2\2\2\u0121\u0124\3" +
                    "\2\2\2\u0122\u0120\3\2\2\2\u0122\u0123\3\2\2\2\u0123%\3\2\2\2\u0124\u0122" +
                    "\3\2\2\2\u0125\u0128\5*\26\2\u0126\u0128\7w\2\2\u0127\u0125\3\2\2\2\u0127" +
                    "\u0126\3\2\2\2\u0128\'\3\2\2\2\u0129\u012a\t\n\2\2\u012a)\3\2\2\2\u012b" +
                    "\u012e\5(\25\2\u012c\u012e\5,\27\2\u012d\u012b\3\2\2\2\u012d\u012c\3\2" +
                    "\2\2\u012e+\3\2\2\2\u012f\u0135\5.\30\2\u0130\u0135\7\67\2\2\u0131\u0135" +
                    "\78\2\2\u0132\u0135\79\2\2\u0133\u0135\7:\2\2\u0134\u012f\3\2\2\2\u0134" +
                    "\u0130\3\2\2\2\u0134\u0131\3\2\2\2\u0134\u0132\3\2\2\2\u0134\u0133\3\2" +
                    "\2\2\u0135-\3\2\2\2\u0136\u0137\t\13\2\2\u0137/\3\2\2\2\u0138\u013f\7" +
                    "\67\2\2\u0139\u013f\78\2\2\u013a\u013f\7w\2\2\u013b\u013f\7x\2\2\u013c" +
                    "\u013f\7}\2\2\u013d\u013f\5\62\32\2\u013e\u0138\3\2\2\2\u013e\u0139\3" +
                    "\2\2\2\u013e\u013a\3\2\2\2\u013e\u013b\3\2\2\2\u013e\u013c\3\2\2\2\u013e" +
                    "\u013d\3\2\2\2\u013f\61\3\2\2\2\u0140\u0141\t\f\2\2\u0141\63\3\2\2\2\32" +
                    "9EJSWZdgj{\177\u0088\u008c\u0095\u00a1\u00ce\u0110\u0112\u011a\u0122\u0127" +
                    "\u012d\u0134\u013e";
    public static final ATN _ATN =
            new ATNDeserializer().deserialize(_serializedATN.toCharArray());

    static {
        _decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
        for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
            _decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
        }
    }
}