/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.antlr4.common;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.io.stream.ICharReader;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.component.parse.AbstractCharReaderResourceParser;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CodePointCharStream;
import org.antlr.v4.runtime.IntStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.misc.IntervalSet;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

import static io.nop.antlr4.common.AntlrErrors.ARG_EXPECTED;
import static io.nop.antlr4.common.AntlrErrors.ARG_OFFENDING_TOKEN;
import static io.nop.antlr4.common.AntlrErrors.ARG_SOURCE;
import static io.nop.antlr4.common.AntlrErrors.ERR_ANTLR_PARSE_FAIL;
import static io.nop.antlr4.common.AntlrErrors.ERR_ANTLR_PARSE_NOT_END_PROPERLY;

public abstract class AbstractParseTreeParser extends AbstractCharReaderResourceParser<ParseTreeResult>
        implements IParseTreeParser {
    static final Logger LOG = LoggerFactory.getLogger(AbstractParseTreeParser.class);

    protected SourceLocation baseLocation;
    protected String source;

    @Override
    protected ParseTreeResult doParseResource(IResource resource) {
        SourceLocation loc = SourceLocation.fromPath(resource.getPath());
        String text = ResourceHelper.readText(resource, getEncoding());
        return parseFromText(loc, text);
    }

    @Override
    public ParseTreeResult parseFromText(SourceLocation loc, String text) {
        if (StringHelper.isEmpty(text))
            return null;
        this.baseLocation = loc;
        this.source = text;
        String sourceName = loc == null ? IntStream.UNKNOWN_SOURCE_NAME : loc.getCellPath();
        CodePointCharStream stream = CharStreams.fromString(text, sourceName);
        return doParse(stream);
    }

    protected void config(Lexer lexer) {
        if (baseLocation != null) {
            lexer.getInterpreter().setLine(baseLocation.getLine());
        }
        if (lexer instanceof AbstractAntlrLexer) {
            AbstractAntlrLexer antlrLexer = (AbstractAntlrLexer) lexer;
            antlrLexer.setSource(source);
            antlrLexer.setBaseLocation(baseLocation);
        }
    }

    protected abstract ParseTreeResult doParse(CharStream stream);

    @Override
    protected ParseTreeResult doParse(SourceLocation loc, ICharReader reader) {
        throw new UnsupportedOperationException();
    }

    protected <T extends Parser> ParseTreeResult twoPhaseParse(T parser, Function<T, ParseTree> parseFn) {
        try {
            parser.setErrorHandler(new BailErrorStrategy());
            parser.getInterpreter().setPredictionMode(PredictionMode.SLL);
            ParseTree result = parseFn.apply(parser);
            checkEnd(parser);
            return new ParseTreeResult(parser, baseLocation, result);
        } catch (final ParseCancellationException ex) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("nop.parse.first-phase-parse-fail", buildError(ex));
            }
            parser.reset();
            try {
                parser.getInterpreter().setPredictionMode(PredictionMode.LL);
                ParseTree result = parseFn.apply(parser);
                checkEnd(parser);
                return new ParseTreeResult(parser, baseLocation, result);
            } catch (ParseCancellationException e2) {
                reportError(e2.getCause());
                return null;
            }
        }
    }

    protected void checkEnd(Parser parser) {
        skipBlank(parser);

        if (!parser.isMatchedEOF() && parser.getCurrentToken().getType() != Token.EOF) {
            NopException ne = new NopException(ERR_ANTLR_PARSE_NOT_END_PROPERLY);
            Token token = parser.getCurrentToken();
            if (token != null) {
                if (baseLocation == null)
                    this.baseLocation = SourceLocation.fromClass(IParseTreeParser.class);
                SourceLocation loc = baseLocation.position(token.getLine(), token.getCharPositionInLine(), 0);
                ne.loc(loc);
                ne.param(ARG_OFFENDING_TOKEN, getTokenErrorDisplay(token));
                String str = StringHelper.shortText(source, token.getStartIndex(), 50);
                ne.param(ARG_SOURCE, str);
            }
            // ne.param(ARG_FULL_SOURCE, source);
            throw ne;
        }
    }

    void skipBlank(Parser parser) {
        while (!parser.isMatchedEOF()) {
            Token token = parser.getCurrentToken();
            String text = token.getText();
            if (!StringHelper.isBlank(text))
                break;
            parser.consume();
        }
    }

    protected void reportError(Throwable e) {
        throw buildError(e);
    }

    protected NopException buildError(Throwable e) {
        RecognitionException re = (RecognitionException) e;
        NopException ne = new NopException(ERR_ANTLR_PARSE_FAIL, e);
        Token token = re.getOffendingToken();
        if (token != null) {
            if (baseLocation == null)
                this.baseLocation = SourceLocation.fromClass(IParseTreeParser.class);
            SourceLocation loc = baseLocation.position(token.getLine(), token.getCharPositionInLine(), 0);
            ne.loc(loc);
            ne.param(ARG_OFFENDING_TOKEN, getTokenErrorDisplay(token));
            String str = StringHelper.shortText(source, token.getStartIndex(), 50);
            ne.param(ARG_SOURCE, str);
        }
        // ne.param(ARG_FULL_SOURCE, source);
        String expects = getExpects(re);
        if (expects != null) {
            ne.param(ARG_EXPECTED, expects);
        }
        return ne;
    }

    /**
     * 优先检查下一步预期的token是否在此集合中，如果是，则只返回对应的token信息，简化报错信息。
     */
    protected int[] getPrimaryExpectedTokens() {
        return null;
    }

    protected String getExpects(RecognitionException re) {
        IntervalSet expects = re.getExpectedTokens();
        if (expects == null)
            return null;

        int[] tokens = getPrimaryExpectedTokens();
        if (tokens != null) {
            for (int token : tokens) {
                if (expects.contains(token)) {
                    return re.getRecognizer().getVocabulary().getDisplayName(token);
                }
            }
        }

        return expects.toString(re.getRecognizer().getVocabulary());
    }

    protected String getTokenErrorDisplay(Token t) {
        if (t == null)
            return "<no token>";
        String s = getSymbolText(t);
        if (s == null) {
            if (getSymbolType(t) == Token.EOF) {
                s = "<EOF>";
            } else {
                s = "<" + getSymbolType(t) + ">";
            }
        }
        return escapeWSAndQuote(s);
    }

    protected String getSymbolText(Token symbol) {
        return symbol.getText();
    }

    protected int getSymbolType(Token symbol) {
        return symbol.getType();
    }

    protected String escapeWSAndQuote(String s) {
        // if ( s==null ) return s;
        return StringHelper.escapeJava(s);
    }
}