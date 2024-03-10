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
import io.nop.commons.util.StringHelper;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.LexerNoViableAltException;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;

import static io.nop.antlr4.common.AntlrErrors.ARG_OFFENDING_TOKEN;
import static io.nop.antlr4.common.AntlrErrors.ARG_SOURCE;
import static io.nop.antlr4.common.AntlrErrors.ERR_ANTLR_LEXER_PARSE_FAIL;
import static io.nop.antlr4.common.AntlrErrors.ERR_ANTLR_STRING_LITERAL_NOT_END;

public abstract class AbstractAntlrLexer extends Lexer {

    public AbstractAntlrLexer(CharStream input) {
        super(input);
    }

    private SourceLocation baseLocation;
    protected Token lastToken;
    private String source;

    public SourceLocation getBaseLocation() {
        return baseLocation;
    }

    public void setBaseLocation(SourceLocation baseLocation) {
        this.baseLocation = baseLocation;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSource() {
        return source;
    }

    protected void defaultNotifyListeners(LexerNoViableAltException e) {
        super.notifyListeners(e);
    }

    public SourceLocation getLocation() {
        if (baseLocation == null)
            baseLocation = SourceLocation.UNKNOWN;
        return baseLocation.offset(_tokenStartLine - 1, _tokenStartCharPositionInLine);
    }

    @Override
    public Token nextToken() {
        Token next = super.nextToken();

        if (next.getChannel() == Token.DEFAULT_CHANNEL) {
            // Keep track of the last token on the default channel.
            this.lastToken = next;
        } else if (next.getChannel() != Token.HIDDEN_CHANNEL) {
            checkErrorToken(next);
        }

        return next;
    }

    protected void checkErrorToken(Token next) {
        String text = _input.getText(Interval.of(_tokenStartCharIndex, _input.index()));
        if (text.startsWith("\"") || text.startsWith("'"))
            throw new NopException(ERR_ANTLR_STRING_LITERAL_NOT_END).loc(getLocation())
                    .param(ARG_OFFENDING_TOKEN, getErrorDisplay(text)).param(ARG_SOURCE, getSourceInfo());

        throw new NopException(ERR_ANTLR_LEXER_PARSE_FAIL).loc(getLocation())
                .param(ARG_OFFENDING_TOKEN, getErrorDisplay(text)).param(ARG_SOURCE, getSourceInfo());
    }

    protected String getSourceInfo() {
        if (source == null)
            return null;
        return StringHelper.shortText(source, _tokenStartCharIndex, 50);
    }

    @Override
    public void notifyListeners(LexerNoViableAltException e) {
        String text = _input.getText(Interval.of(_tokenStartCharIndex, _input.index()));
        throw new NopException(ERR_ANTLR_LEXER_PARSE_FAIL).loc(getLocation())
                .param(ARG_OFFENDING_TOKEN, getErrorDisplay(text)).param(ARG_SOURCE, getSourceInfo());
    }
}